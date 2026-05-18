package com.power.gitinsight.domain.hotspot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: HotspotAggregatorTest
 * Description: Pure-function tests for HotspotAggregator covering counting, dedup, revert detection, and recency scoring.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 14:14
 **/
class HotspotAggregatorTest {

    private val now = 1_747_500_000_000L  // pinned clock used by every test
    private val repoId = "abc1234567890def"

    @Test fun `empty events yields empty map`() {
        assertTrue(HotspotAggregator.aggregate(repoId, emptyList(), now).isEmpty())
    }

    @Test fun `modify count equals number of commits touching the file`() {
        val events = listOf(
            event(path = "A.kt", commit = "c1"),
            event(path = "A.kt", commit = "c2"),
            event(path = "A.kt", commit = "c3"),
            event(path = "B.kt", commit = "c1")
        )
        val result = HotspotAggregator.aggregate(repoId, events, now)

        assertEquals(3, result["A.kt"]!!.modifyCount)
        assertEquals(1, result["B.kt"]!!.modifyCount)
    }

    @Test fun `author count dedupes the same author across commits`() {
        val events = listOf(
            event(path = "A.kt", commit = "c1", author = "Alice"),
            event(path = "A.kt", commit = "c2", author = "Alice"),
            event(path = "A.kt", commit = "c3", author = "Bob")
        )
        val result = HotspotAggregator.aggregate(repoId, events, now)

        assertEquals(2, result["A.kt"]!!.authorCount, "Alice + Bob")
    }

    @Test fun `rollback count counts only revert events`() {
        val events = listOf(
            event(path = "A.kt", commit = "c1", isRevert = false),
            event(path = "A.kt", commit = "c2", isRevert = true),
            event(path = "A.kt", commit = "c3", isRevert = true),
            event(path = "A.kt", commit = "c4", isRevert = false)
        )
        val result = HotspotAggregator.aggregate(repoId, events, now)

        assertEquals(4, result["A.kt"]!!.modifyCount)
        assertEquals(2, result["A.kt"]!!.rollbackCount)
    }

    @Test fun `lastModified equals max timestamp among events`() {
        val older = now - TimeUnit.DAYS.toMillis(30)
        val newer = now - TimeUnit.DAYS.toMillis(2)
        val events = listOf(
            event(path = "A.kt", commit = "c1", timestamp = older),
            event(path = "A.kt", commit = "c2", timestamp = newer),
            event(path = "A.kt", commit = "c3", timestamp = older - 1_000)
        )
        val result = HotspotAggregator.aggregate(repoId, events, now)

        assertEquals(newer, result["A.kt"]!!.lastModified)
    }

    @Test fun `recent file scores higher than ancient file with same modify count`() {
        val recent = listOf(
            event(path = "Recent.kt", commit = "c1", timestamp = now - TimeUnit.DAYS.toMillis(1)),
            event(path = "Recent.kt", commit = "c2", timestamp = now - TimeUnit.DAYS.toMillis(2))
        )
        val ancient = listOf(
            event(path = "Old.kt", commit = "c3", timestamp = now - TimeUnit.DAYS.toMillis(300)),
            event(path = "Old.kt", commit = "c4", timestamp = now - TimeUnit.DAYS.toMillis(301))
        )

        val r1 = HotspotAggregator.aggregate(repoId, recent, now).getValue("Recent.kt")
        val r2 = HotspotAggregator.aggregate(repoId, ancient, now).getValue("Old.kt")

        assertEquals(r1.modifyCount, r2.modifyCount)
        assertTrue(r1.hotspotScore > r2.hotspotScore, "recency bonus must dominate when counts match")
    }

    @Test fun `score is never negative for very old files`() {
        val veryOld = listOf(
            event(path = "Ancient.kt", commit = "c1", timestamp = now - TimeUnit.DAYS.toMillis(10_000))
        )
        val result = HotspotAggregator.aggregate(repoId, veryOld, now).getValue("Ancient.kt")
        assertTrue(result.hotspotScore >= 0.0, "score floor is 0, got ${result.hotspotScore}")
    }

    @Test fun `repoId on output matches input`() {
        val events = listOf(event(path = "A.kt", commit = "c1"))
        val result = HotspotAggregator.aggregate(repoId, events, now)
        assertEquals(repoId, result["A.kt"]!!.repoId)
    }

    @Test fun `missing file returns null from map`() {
        val events = listOf(event(path = "A.kt", commit = "c1"))
        val result = HotspotAggregator.aggregate(repoId, events, now)
        assertNull(result["B.kt"])
    }

    private fun event(
        path: String,
        commit: String,
        author: String = "Tester",
        timestamp: Long = now - TimeUnit.HOURS.toMillis(2),
        isRevert: Boolean = false
    ) = FileChangeEvent(
        filePath = path,
        commitId = commit,
        author = author,
        timestamp = timestamp,
        isRevert = isRevert
    )
}
