package com.power.gitinsight.ui.tooltip

import com.intellij.openapi.util.text.StringUtil
import com.power.gitinsight.domain.blame.BlameLine
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: BlameTooltipRenderer
 * Description: Pure HTML/text rendering for blame tooltips; nowMs injectable so unit tests can pin the clock.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:50
 **/
internal object BlameTooltipRenderer {

    fun renderHtml(line: BlameLine, nowMs: Long = System.currentTimeMillis()): String {
        val author = StringUtil.escapeXmlEntities(line.author.ifBlank { "(unknown)" })
        val summary = StringUtil.escapeXmlEntities(line.summary.ifBlank { "(no message)" })
        val shortHash = line.commitId.take(8)
        val ago = formatRelative(line.timestamp, nowMs)
        return buildString {
            append("<html><body style='font-family: sans-serif; max-width: 360px;'>")
            append("<b>").append(author).append("</b>")
            if (ago.isNotEmpty()) append(" · ").append(ago)
            append("<br/>")
            append("<code style='color:#888'>").append(shortHash).append("</code><br/>")
            append(summary)
            append("</body></html>")
        }
    }

    fun formatRelative(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return ""
        val delta = nowMs - epochMs
        if (delta < 0) return ""
        val seconds = TimeUnit.MILLISECONDS.toSeconds(delta)
        if (seconds < 60) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        if (minutes < 60) return "$minutes min ago"
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        if (hours < 24) return "$hours hr ago"
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        if (days < 30) return "$days day(s) ago"
        if (days < 365) return "${days / 30} mo ago"
        return "${days / 365} yr ago"
    }
}
