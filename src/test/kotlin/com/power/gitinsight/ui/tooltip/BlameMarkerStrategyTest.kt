package com.power.gitinsight.ui.tooltip

import com.power.gitinsight.domain.blame.BlameLine
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * team : gitInsight.
 * Class Name: BlameMarkerStrategyTest
 * Description: Boundary clustering rule for gutter markers: first line of a same-commit run emits, the rest are suppressed.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 23:32
 **/
class BlameMarkerStrategyTest {

    @Test fun `first line of file emits when blame present`() {
        val byLine = mapOf(
            1 to line(commit = "AAA"),
            2 to line(commit = "AAA")
        )
        assertTrue(BlameMarkerStrategy.shouldEmit(1, byLine))
    }

    @Test fun `same commit as previous line is suppressed`() {
        val byLine = mapOf(
            1 to line(commit = "AAA"),
            2 to line(commit = "AAA"),
            3 to line(commit = "AAA")
        )
        assertFalse(BlameMarkerStrategy.shouldEmit(2, byLine))
        assertFalse(BlameMarkerStrategy.shouldEmit(3, byLine))
    }

    @Test fun `different commit from previous line emits`() {
        val byLine = mapOf(
            1 to line(commit = "AAA"),
            2 to line(commit = "BBB")
        )
        assertTrue(BlameMarkerStrategy.shouldEmit(2, byLine))
    }

    @Test fun `gap line with no blame returns false`() {
        val byLine = mapOf(
            1 to line(commit = "AAA"),
            3 to line(commit = "AAA")
        )
        assertFalse(BlameMarkerStrategy.shouldEmit(2, byLine), "missing blame -> no marker")
    }

    @Test fun `line after a gap emits even if commit matches the next-earlier line`() {
        val byLine = mapOf(
            1 to line(commit = "AAA"),
            3 to line(commit = "AAA")  // line 2 missing
        )
        assertTrue(
            BlameMarkerStrategy.shouldEmit(3, byLine),
            "previous line (2) has no blame, so line 3 is a fresh boundary"
        )
    }

    @Test fun `empty map returns false`() {
        assertFalse(BlameMarkerStrategy.shouldEmit(1, emptyMap()))
    }

    private fun line(commit: String, n: Int = 0) = BlameLine(
        lineNumber = n,
        commitId = commit,
        author = "Tester",
        authorEmail = null,
        timestamp = 0L,
        summary = "test"
    )
}
