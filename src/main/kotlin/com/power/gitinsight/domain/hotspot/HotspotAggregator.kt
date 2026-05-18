package com.power.gitinsight.domain.hotspot

import java.util.concurrent.TimeUnit
import kotlin.math.ln

/**
 * team : gitInsight.
 * Class Name: HotspotAggregator
 * Description: Pure-function reducer from FileChangeEvent stream to per-file FileHotspot; nowMs is injectable for tests.
 *              Scoring is a deliberately simple placeholder; T9 replaces with the multi-factor formula from §4.2.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 12:45
 **/
internal object HotspotAggregator {

    fun aggregate(
        repoId: String,
        events: List<FileChangeEvent>,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, FileHotspot> {
        if (events.isEmpty()) return emptyMap()

        return events.groupBy { it.filePath }.mapValues { (path, group) ->
            val modifyCount = group.size
            val rollbackCount = group.count { it.isRevert }
            val authorCount = group.distinctBy { it.author }.size
            val lastModified = group.maxOf { it.timestamp }
            FileHotspot(
                repoId = repoId,
                filePath = path,
                modifyCount = modifyCount,
                rollbackCount = rollbackCount,
                authorCount = authorCount,
                lastModified = lastModified,
                hotspotScore = placeholderScore(modifyCount, lastModified, nowMs)
            )
        }
    }

    /**
     * T8 placeholder: log-normalized modify count plus a recency bonus that decays over a year.
     * Rolls and authors will get their own weights in T9.
     */
    private fun placeholderScore(modifyCount: Int, lastModified: Long, nowMs: Long): Double {
        val modifyTerm = ln(1.0 + modifyCount) * 10.0
        val ageMs = (nowMs - lastModified).coerceAtLeast(0L)
        val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs).toDouble()
        val recencyBonus = (30.0 - ageDays / 12.0).coerceIn(0.0, 30.0)
        return modifyTerm + recencyBonus
    }
}
