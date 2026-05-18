package com.power.gitinsight.domain.hotspot

/**
 * team : gitInsight.
 * Class Name: HotspotModel
 * Description: Value types for per-file hotspot data, consumed by HotspotAggregator and HotspotService.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 12:42
 **/

/** A single commit that touched a particular file. Produced by GitAdapter.scanFileHistory. */
data class FileChangeEvent(
    val filePath: String,           // path relative to repo root
    val commitId: String,           // full SHA-1
    val author: String,
    val timestamp: Long,            // epoch milliseconds
    val isRevert: Boolean           // commit message starts with `Revert "` (case-insensitive)
)

/** Aggregated hotspot metrics for one file. Persisted into `file_hotspot` table. */
data class FileHotspot(
    val repoId: String,
    val filePath: String,
    val modifyCount: Int,
    val rollbackCount: Int,
    val authorCount: Int,
    val lastModified: Long,         // epoch milliseconds
    val hotspotScore: Double
)
