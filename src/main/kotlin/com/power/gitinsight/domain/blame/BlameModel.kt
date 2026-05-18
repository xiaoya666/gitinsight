package com.power.gitinsight.domain.blame

/**
 * team : gitInsight.
 * Class Name: BlameModel
 * Description: Value types for per-line Git blame results consumed by BlameService and Tooltip UI.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:05
 **/

data class BlameLine(
    val lineNumber: Int,         // 1-based, matches editor line numbering
    val commitId: String,        // full SHA-1
    val author: String,
    val authorEmail: String?,
    val timestamp: Long,         // epoch milliseconds
    val summary: String          // commit message subject line
)

data class BlameSnapshot(
    val filePath: String,
    val headCommitId: String,
    val lines: List<BlameLine>
)
