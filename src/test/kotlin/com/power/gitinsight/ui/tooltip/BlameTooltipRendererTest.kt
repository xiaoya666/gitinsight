package com.power.gitinsight.ui.tooltip

import com.power.gitinsight.domain.blame.BlameLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: BlameTooltipRendererTest
 * Description: Pure-function tests for BlameTooltipRenderer covering HTML escape and relative time boundaries.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:52
 **/
class BlameTooltipRendererTest {

    private val now = 1_747_500_000_000L  // pinned clock used by every test

    // --- formatRelative ----------------------------------------------------

    @Test fun `zero timestamp returns empty`() {
        assertEquals("", BlameTooltipRenderer.formatRelative(0L, now))
    }

    @Test fun `negative timestamp returns empty`() {
        assertEquals("", BlameTooltipRenderer.formatRelative(-1L, now))
    }

    @Test fun `future timestamp returns empty`() {
        assertEquals("", BlameTooltipRenderer.formatRelative(now + 1_000, now))
    }

    @Test fun `under one minute returns just now`() {
        val ts = now - TimeUnit.SECONDS.toMillis(30)
        assertEquals("just now", BlameTooltipRenderer.formatRelative(ts, now))
    }

    @Test fun `minute boundary returns min ago`() {
        val ts = now - TimeUnit.MINUTES.toMillis(5)
        assertEquals("5 min ago", BlameTooltipRenderer.formatRelative(ts, now))
    }

    @Test fun `hour boundary returns hr ago`() {
        val ts = now - TimeUnit.HOURS.toMillis(3)
        assertEquals("3 hr ago", BlameTooltipRenderer.formatRelative(ts, now))
    }

    @Test fun `day boundary returns day(s) ago`() {
        val ts = now - TimeUnit.DAYS.toMillis(5)
        assertEquals("5 day(s) ago", BlameTooltipRenderer.formatRelative(ts, now))
    }

    @Test fun `month boundary returns mo ago`() {
        val ts = now - TimeUnit.DAYS.toMillis(60)
        assertEquals("2 mo ago", BlameTooltipRenderer.formatRelative(ts, now))
    }

    @Test fun `year boundary returns yr ago`() {
        val ts = now - TimeUnit.DAYS.toMillis(400)
        assertEquals("1 yr ago", BlameTooltipRenderer.formatRelative(ts, now))
    }

    // --- renderHtml --------------------------------------------------------

    @Test fun `renders author summary and short hash`() {
        val line = sampleLine(author = "Zhang San", summary = "fix: a bug", hash = "abcdef0123456789")
        val html = BlameTooltipRenderer.renderHtml(line, now)
        assertTrue(html.contains("Zhang San"), html)
        assertTrue(html.contains("fix: a bug"), html)
        assertTrue(html.contains("abcdef01"), "should show first 8 chars of hash")
        assertFalse(html.contains("abcdef012345"), "should not show more than 8 chars")
    }

    @Test fun `escapes HTML in author and summary to prevent XSS`() {
        val line = sampleLine(
            author = "<script>alert(1)</script>",
            summary = "subject with & < > \" '"
        )
        val html = BlameTooltipRenderer.renderHtml(line, now)
        assertFalse(html.contains("<script>"), "raw <script> must be escaped")
        assertTrue(html.contains("&lt;script&gt;"), "should contain escaped form")
        assertTrue(html.contains("&amp;"), "& must be escaped")
    }

    @Test fun `blank author falls back to unknown placeholder`() {
        val line = sampleLine(author = "")
        val html = BlameTooltipRenderer.renderHtml(line, now)
        assertTrue(html.contains("(unknown)"), html)
    }

    @Test fun `blank summary falls back to no message placeholder`() {
        val line = sampleLine(summary = "")
        val html = BlameTooltipRenderer.renderHtml(line, now)
        assertTrue(html.contains("(no message)"), html)
    }

    @Test fun `zero timestamp omits the time-ago segment but keeps the rest`() {
        val line = sampleLine(timestamp = 0L)
        val html = BlameTooltipRenderer.renderHtml(line, now)
        assertFalse(html.contains(" · "), "should not append separator when ago is empty")
        assertTrue(html.contains("Zhang San"))
    }

    // --- helpers -----------------------------------------------------------

    private fun sampleLine(
        lineNumber: Int = 1,
        hash: String = "deadbeef00000000",
        author: String = "Zhang San",
        timestamp: Long = now - TimeUnit.HOURS.toMillis(2),
        summary: String = "fix: thing"
    ) = BlameLine(
        lineNumber = lineNumber,
        commitId = hash,
        author = author,
        authorEmail = null,
        timestamp = timestamp,
        summary = summary
    )
}
