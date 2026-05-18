package com.power.gitinsight.domain.hotspot

/**
 * team : gitInsight.
 * Class Name: HotspotAggregator
 * Description: Pure-function reducer from FileChangeEvent stream to per-file FileHotspot; nowMs is injectable for tests.
 *              Delegates scoring to HotspotScorer (multi-factor formula per §4.2).
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
            val score = HotspotScorer.score(
                HotspotScorer.ScoringInputs(
                    modifyCount = modifyCount,
                    rollbackCount = rollbackCount,
                    conflictCount = 0,  // Sprint 3 reintroduces merge-conflict analysis
                    authorCount = authorCount,
                    lastModified = lastModified
                ),
                nowMs = nowMs
            )
            FileHotspot(
                repoId = repoId,
                filePath = path,
                modifyCount = modifyCount,
                rollbackCount = rollbackCount,
                authorCount = authorCount,
                lastModified = lastModified,
                hotspotScore = score
            )
        }
    }
}
