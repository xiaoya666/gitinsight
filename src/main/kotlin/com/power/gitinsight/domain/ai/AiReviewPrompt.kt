package com.power.gitinsight.domain.ai

import com.intellij.openapi.vcs.changes.Change

/**
 * team : gitInsight.
 * Class Name: AiReviewPrompt
 * Description: Pure prompt builder for the "AI Review This Diff" action. System prompt encodes the
 *              Java / Kotlin / Spring focus areas from spec §4.5; user prompt lays out the changed files
 *              with size caps so cheap models still fit the request in a single round-trip.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal object AiReviewPrompt {

    private const val MAX_FILES = 30
    private const val MAX_CHARS_PER_FILE = 2000
    private const val MAX_TOTAL_CHARS = 20_000
    private const val FILE_HEADER = "===="

    fun systemMessage(): AiMessage = AiMessage(
        AiRole.SYSTEM,
        """
        You are a senior reviewer for a JVM backend codebase (Java / Kotlin / Spring Boot).
        Review the supplied diff and surface concrete issues. Skim past trivial style nits;
        focus on the following high-signal areas:

        1. Null safety — NPE risk, Kotlin `!!`, platform types crossing the Java/Kotlin boundary.
        2. BigDecimal precision — division without scale, equality with `==`, missing rounding mode.
        3. @Transactional gotchas — self-invocation, private methods, rollbackFor missing for checked exceptions.
        4. Concurrency — lock granularity, deadlock risk, missing volatile, unsafe shared state.
        5. SQL — `SELECT *`, missing indexes for hot WHERE clauses, UPDATE/DELETE without WHERE,
           N+1 queries, missing connection close.
        6. Resource handling — try-with-resources, closing streams / connections / Closeables.
        7. Validation & input — unchecked external input (path traversal, SSRF, injection).

        OUTPUT FORMAT (Markdown):
          ## <file path>
          - **Severity** (HIGH/MED/LOW) at line N: <one-line explanation>
            <2-4 sentence reasoning, including the relevant snippet>

        Group findings by file. If there are no issues, output exactly: "✅ No findings."
        Use the user's repo language (CN/EN) based on file paths and content.
        Do NOT echo unchanged files or hunks back at the user.
        """.trimIndent()
    )

    fun userMessage(changes: List<Change>): AiMessage {
        val sb = StringBuilder()
        sb.append("Review the following diff:\n\n")

        var totalChars = 0
        var filesIncluded = 0
        for (change in changes) {
            if (filesIncluded >= MAX_FILES || totalChars >= MAX_TOTAL_CHARS) break
            val block = describeChange(change) ?: continue
            sb.append(block).append('\n')
            totalChars += block.length
            filesIncluded++
        }
        if (filesIncluded < changes.size) {
            sb.append("\n(... ${changes.size - filesIncluded} more files omitted; review the included files only ...)\n")
        }
        return AiMessage(AiRole.USER, sb.toString())
    }

    private fun describeChange(change: Change): String? {
        val before = runCatching { change.beforeRevision?.content }.getOrNull()
        val after = runCatching { change.afterRevision?.content }.getOrNull()
        val path = change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path ?: return null

        val verb = when {
            before == null && after != null -> "Added"
            after == null && before != null -> "Deleted"
            else -> "Modified"
        }

        val sb = StringBuilder()
        sb.append(FILE_HEADER).append(' ').append(verb).append(": ").append(path).append('\n')

        // For Modified files, show before AND after halves so the model can see what changed.
        if (verb == "Modified" && before != null && after != null) {
            val half = MAX_CHARS_PER_FILE / 2
            sb.append("--- BEFORE ---\n")
            sb.append(before.take(half))
            if (before.length > half) sb.append("\n... [truncated] ...\n")
            sb.append("\n--- AFTER ---\n")
            sb.append(after.take(half))
            if (after.length > half) sb.append("\n... [truncated] ...\n")
        } else {
            val excerpt = (after ?: before).orEmpty()
            sb.append(excerpt.take(MAX_CHARS_PER_FILE))
            if (excerpt.length > MAX_CHARS_PER_FILE) sb.append("\n... [truncated] ...\n")
        }
        return sb.toString()
    }
}
