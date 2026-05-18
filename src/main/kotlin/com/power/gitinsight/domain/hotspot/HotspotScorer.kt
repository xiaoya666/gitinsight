package com.power.gitinsight.domain.hotspot

import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tanh

/**
 * team : gitInsight.
 * Class Name: HotspotScorer
 * Description: Multi-factor hotspot score per §4.2 of the spec. Pure function with injectable weights and clock for unit tests.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 15:18
 **/
internal object HotspotScorer {

    /**
     * Tunable weights. Defaults from §4.2 — rollback weight is highest because reverts are the
     * strongest "this code has burned us" signal. Conflict weight stays defined but unused
     * until Sprint 3 wires conflictCount from merge history.
     */
    data class Weights(
        val modifyWeight: Double = 1.0,
        val recencyWeight: Double = 2.0,
        val conflictWeight: Double = 1.5,
        val rollbackWeight: Double = 3.0,
        val authorWeight: Double = 0.8
    )

    /** Raw inputs to the formula. conflictCount is 0 until Sprint 3 reintroduces merge analysis. */
    data class ScoringInputs(
        val modifyCount: Int,
        val rollbackCount: Int,
        val conflictCount: Int,
        val authorCount: Int,
        val lastModified: Long
    )

    /**
     * Compute the 0-100 hotspot score. Soft tanh normalization avoids the cliff edge of a hard clamp
     * and lets the score asymptotically approach 100 without ever exceeding it.
     */
    fun score(
        inputs: ScoringInputs,
        weights: Weights = Weights(),
        nowMs: Long = System.currentTimeMillis()
    ): Double {
        val modifyTerm = weights.modifyWeight * ln(1.0 + inputs.modifyCount.coerceAtLeast(0))
        val recencyTerm = weights.recencyWeight * recencyDecay(inputs.lastModified, nowMs)
        val conflictTerm = weights.conflictWeight * inputs.conflictCount.coerceAtLeast(0)
        val rollbackTerm = weights.rollbackWeight * inputs.rollbackCount.coerceAtLeast(0)
        val authorTerm = weights.authorWeight * ln(1.0 + inputs.authorCount.coerceAtLeast(0))

        val raw = modifyTerm + recencyTerm + conflictTerm + rollbackTerm + authorTerm
        return normalize(raw)
    }

    /**
     * Recency factor in 0-10 range: 1.0 today, ~0.5 at 30 days, ~0.04 at 100 days, ~0 at 1 year.
     * Returns 0 for invalid timestamps so the formula never penalizes missing data.
     */
    internal fun recencyDecay(lastModified: Long, nowMs: Long): Double {
        if (lastModified <= 0L) return 0.0
        val ageMs = (nowMs - lastModified).coerceAtLeast(0L)
        val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs).toDouble()
        return exp(-ageDays / 30.0) * 10.0
    }

    /** Soft normalization: tanh(raw / 30) * 100. Stays in [0, 100), saturates smoothly. */
    internal fun normalize(raw: Double): Double {
        if (raw <= 0.0) return 0.0
        return tanh(raw / 30.0) * 100.0
    }
}
