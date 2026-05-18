package com.power.gitinsight.ui.tooltip

import com.power.gitinsight.domain.blame.BlameLine

/**
 * team : gitInsight.
 * Class Name: BlameMarkerStrategy
 * Description: Boundary-only clustering: emit a gutter marker only at the first line of each contiguous same-commit run.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 23:28
 **/
internal object BlameMarkerStrategy {

    /**
     * Returns true when [line] should carry a visible marker.
     * Rule: the line must have blame, and either (a) the previous line has no blame
     * (gap or top-of-file) or (b) the previous line's commit differs from the current.
     */
    fun shouldEmit(line: Int, byLine: Map<Int, BlameLine>): Boolean {
        val current = byLine[line] ?: return false
        val prev = byLine[line - 1] ?: return true
        return prev.commitId != current.commitId
    }
}
