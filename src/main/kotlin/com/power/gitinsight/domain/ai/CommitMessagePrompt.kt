package com.power.gitinsight.domain.ai

import com.intellij.openapi.vcs.changes.Change

/**
 * team : gitInsight.
 * Class Name: CommitMessagePrompt
 * Description: Pure-function adapter from a Change list to an AI prompt for commit-message generation.
 *              Caps the prompt at MAX_TOTAL_CHARS so cheap models still answer in one shot.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal object CommitMessagePrompt {

    private const val MAX_FILES = 20
    private const val MAX_CHARS_PER_FILE = 1200
    private const val MAX_TOTAL_CHARS = 12_000
    private const val FILE_HEADER = "----"

    fun systemMessage(): AiMessage = AiMessage(
        AiRole.SYSTEM,
        """
        You are a senior engineer drafting a Conventional Commits message.
        Output ONLY the commit message — no preamble, no markdown code fences.
        Subject line: <= 72 chars, imperative voice, optional Conventional Commits prefix
        (feat / fix / refactor / docs / test / chore / perf).
        Optional body: 2-4 short bullets explaining the WHY, separated by a blank line from subject.
        Match the user's repo language (CN/EN) based on file paths and content.
        """.trimIndent()
    )

    fun userMessage(changes: List<Change>): AiMessage {
        val sb = StringBuilder()
        sb.append("Summarize the following staged changes as a commit message.\n\n")

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
            sb.append("\n(... ${changes.size - filesIncluded} more files omitted for brevity ...)\n")
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
        val excerpt = (after ?: before).orEmpty()
        if (excerpt.isNotEmpty()) {
            sb.append(excerpt.take(MAX_CHARS_PER_FILE))
            if (excerpt.length > MAX_CHARS_PER_FILE) sb.append("\n... [truncated] ...\n")
        }
        return sb.toString()
    }
}
