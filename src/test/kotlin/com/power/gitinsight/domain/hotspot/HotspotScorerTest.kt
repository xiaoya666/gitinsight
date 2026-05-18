package com.power.gitinsight.domain.hotspot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: HotspotScorerTest
 * Description: Pure-function tests for HotspotScorer covering each factor's contribution, weight overrides, bounds, and decay shape.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 15:46
 **/
class HotspotScorerTest {

    private val now = 1_747_500_000_000L

    private fun inputs(
        modifyCount: Int = 0,
        rollbackCount: Int = 0,
        conflictCount: Int = 0,
        authorCount: Int = 0,
        lastModified: Long = 0L
    ) = HotspotScorer.ScoringInputs(modifyCount, rollbackCount, conflictCount, authorCount, lastModified)

    @Test fun `zero inputs yield zero score`() {
        val s = HotspotScorer.score(inputs(), nowMs = now)
        assertEquals(0.0, s, 1e-9)
    }

    @Test fun `score is bounded below 100 even for huge inputs`() {
        val huge = inputs(
            modifyCount = 1_000_000,
            rollbackCount = 1_000,
            authorCount = 500,
            lastModified = now
        )
        val s = HotspotScorer.score(huge, nowMs = now)
        assertTrue(s <= 100.0, "score must not exceed 100, got $s")
        assertTrue(s > 95.0, "saturation should be close to 100, got $s")
    }

    @Test fun `score is never negative`() {
        val s = HotspotScorer.score(inputs(modifyCount = -5, rollbackCount = -3), nowMs = now)
        assertTrue(s >= 0.0, "score floor is 0, got $s")
    }

    @Test fun `more modifications give a higher score`() {
        val low = HotspotScorer.score(inputs(modifyCount = 1), nowMs = now)
        val high = HotspotScorer.score(inputs(modifyCount = 50), nowMs = now)
        assertTrue(high > low, "50 modifies should outrank 1 modify ($high vs $low)")
    }

    @Test fun `rollback dominates plain modification`() {
        val mods = HotspotScorer.score(inputs(modifyCount = 5), nowMs = now)
        val roll = HotspotScorer.score(inputs(modifyCount = 0, rollbackCount = 1), nowMs = now)
        assertTrue(roll > mods, "1 rollback should outscore 5 modifications ($roll vs $mods)")
    }

    @Test fun `recent file outscores ancient file with same other inputs`() {
        val today = HotspotScorer.score(
            inputs(modifyCount = 3, lastModified = now - TimeUnit.HOURS.toMillis(1)),
            nowMs = now
        )
        val ancient = HotspotScorer.score(
            inputs(modifyCount = 3, lastModified = now - TimeUnit.DAYS.toMillis(365)),
            nowMs = now
        )
        assertTrue(today > ancient, "recency bonus must add weight ($today vs $ancient)")
    }

    @Test fun `more distinct authors raise the score`() {
        val solo = HotspotScorer.score(inputs(modifyCount = 5, authorCount = 1), nowMs = now)
        val crowd = HotspotScorer.score(inputs(modifyCount = 5, authorCount = 10), nowMs = now)
        assertTrue(crowd > solo, "more authors = more shared ownership = higher risk ($crowd vs $solo)")
    }

    @Test fun `conflict count contributes to the score`() {
        val none = HotspotScorer.score(inputs(modifyCount = 3, conflictCount = 0), nowMs = now)
        val some = HotspotScorer.score(inputs(modifyCount = 3, conflictCount = 5), nowMs = now)
        assertTrue(some > none, "conflicts should raise the score even before Sprint 3 wires inputs")
    }

    @Test fun `custom weights override defaults`() {
        val base = inputs(modifyCount = 10)
        val noWeight = HotspotScorer.score(base, HotspotScorer.Weights(modifyWeight = 0.0), nowMs = now)
        val bigWeight = HotspotScorer.score(base, HotspotScorer.Weights(modifyWeight = 100.0), nowMs = now)
        assertTrue(bigWeight > noWeight, "bigger weight must raise score; got $bigWeight vs $noWeight")
        assertEquals(0.0, noWeight, 1e-9)
    }

    @Test fun `recencyDecay returns zero for missing timestamp`() {
        assertEquals(0.0, HotspotScorer.recencyDecay(0L, now), 1e-9)
        assertEquals(0.0, HotspotScorer.recencyDecay(-1L, now), 1e-9)
    }

    @Test fun `recencyDecay peaks at now and halves around 30 days`() {
        val today = HotspotScorer.recencyDecay(now, now)
        val twentyOneDays = HotspotScorer.recencyDecay(now - TimeUnit.DAYS.toMillis(21), now)
        val thirtyDays = HotspotScorer.recencyDecay(now - TimeUnit.DAYS.toMillis(30), now)
        val ancient = HotspotScorer.recencyDecay(now - TimeUnit.DAYS.toMillis(365), now)

        assertEquals(10.0, today, 1e-6, "today should be the peak (=10)")
        assertTrue(thirtyDays in 3.0..4.0, "~30d should be roughly 1/e * 10 ≈ 3.68, got $thirtyDays")
        assertTrue(twentyOneDays > thirtyDays, "21d should still beat 30d")
        assertTrue(ancient < 0.1, "1yr-old file should be near zero, got $ancient")
    }

    @Test fun `recencyDecay clamps future timestamps to now`() {
        val future = HotspotScorer.recencyDecay(now + TimeUnit.DAYS.toMillis(10), now)
        assertEquals(10.0, future, 1e-6, "future timestamps treated as 'now' (age clamped to 0)")
    }

    @Test fun `normalize maps zero to zero and saturates near 100`() {
        assertEquals(0.0, HotspotScorer.normalize(0.0), 1e-9)
        assertEquals(0.0, HotspotScorer.normalize(-1.0), 1e-9)
        val nearSat = HotspotScorer.normalize(300.0)
        assertTrue(nearSat in 99.0..100.0, "tanh(10) ≈ 1, so big inputs sit just under 100, got $nearSat")
    }

    @Test fun `normalize is monotonic`() {
        var prev = -1.0
        listOf(1.0, 5.0, 10.0, 30.0, 60.0, 100.0, 500.0).forEach { raw ->
            val v = HotspotScorer.normalize(raw)
            assertTrue(v > prev, "normalize must be strictly increasing on positive inputs ($v after $prev)")
            prev = v
        }
    }
}
