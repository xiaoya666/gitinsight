package com.power.gitinsight.domain.activity

import com.power.gitinsight.domain.incident.IncidentReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: ActivityAggregatorTest
 * Description: TDD coverage for ActivityAggregator — empty input, counts, incident classification rollup, byDay bucketing, recent ordering, and incidentRate edge cases.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
class ActivityAggregatorTest {

    private val now = 1_747_500_000_000L  // pinned clock used by every test

    @Test fun `empty input returns empty stats with windowDays preserved`() {
        val stats = ActivityAggregator.aggregate(emptyList(), windowDays = 30)
        assertEquals(30, stats.windowDays)
        assertEquals(0, stats.totalCommits)
        assertEquals(0, stats.incidentCommits)
        assertTrue(stats.byDay.isEmpty())
        assertTrue(stats.recent.isEmpty())
        assertEquals(0.0, stats.incidentRate)
    }

    @Test fun `total counts every commit`() {
        val stats = ActivityAggregator.aggregate(
            commits = listOf(commit("c1"), commit("c2"), commit("c3")),
            windowDays = 7
        )
        assertEquals(3, stats.totalCommits)
    }

    @Test fun `incident count only flags reason-bearing commits`() {
        val stats = ActivityAggregator.aggregate(
            commits = listOf(
                commit("c1", reason = null),
                commit("c2", reason = IncidentReason.REVERT),
                commit("c3", reason = IncidentReason.HOTFIX),
                commit("c4", reason = null),
                commit("c5", reason = IncidentReason.ROLLBACK)
            ),
            windowDays = 30
        )
        assertEquals(5, stats.totalCommits)
        assertEquals(3, stats.incidentCommits)
        assertEquals(0.6, stats.incidentRate, 1e-9)
    }

    @Test fun `byDay groups commits by their UTC date`() {
        val day1 = epochOf("2026-05-25T10:00:00Z")
        val day1Later = epochOf("2026-05-25T23:30:00Z")
        val day2 = epochOf("2026-05-26T01:00:00Z")

        val stats = ActivityAggregator.aggregate(
            commits = listOf(
                commit("a", ts = day1),
                commit("b", ts = day1Later),
                commit("c", ts = day2)
            ),
            windowDays = 14
        )

        assertEquals(2, stats.byDay["2026-05-25"])
        assertEquals(1, stats.byDay["2026-05-26"])
    }

    @Test fun `recent is sorted descending by timestamp`() {
        val older = now - TimeUnit.DAYS.toMillis(5)
        val mid = now - TimeUnit.DAYS.toMillis(2)
        val newest = now - TimeUnit.HOURS.toMillis(1)

        val stats = ActivityAggregator.aggregate(
            commits = listOf(
                commit("older", ts = older),
                commit("newest", ts = newest),
                commit("mid", ts = mid)
            ),
            windowDays = 30
        )

        assertEquals(listOf("newest", "mid", "older"), stats.recent.map { it.commitId })
    }

    @Test fun `recent caps at the first 20 entries`() {
        val commits = (1..50).map { i ->
            commit("c$i", ts = now - TimeUnit.MINUTES.toMillis(i.toLong()))
        }
        val stats = ActivityAggregator.aggregate(commits, windowDays = 30)

        assertEquals(50, stats.totalCommits)
        assertEquals(20, stats.recent.size)
        // c1 is newest (smallest minutes-ago offset)
        assertEquals("c1", stats.recent.first().commitId)
    }

    private fun commit(
        id: String,
        ts: Long = now - TimeUnit.HOURS.toMillis(2),
        reason: IncidentReason? = null,
        subject: String = "feat: thing"
    ) = ActivityCommit(commitId = id, timestamp = ts, subject = subject, incidentReason = reason)

    private fun epochOf(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()
}
