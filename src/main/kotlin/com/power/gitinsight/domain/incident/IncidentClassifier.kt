package com.power.gitinsight.domain.incident

/**
 * team : gitInsight.
 * Class Name: IncidentClassifier
 * Description: Pure-function classifier from a commit subject line to an IncidentReason; recognises Git's auto-revert format, conventional revert/hotfix/rollback prefixes, and the Chinese "回滚" prefix.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 11:51
 **/
object IncidentClassifier {

    private val REVERT_PREFIXES = listOf("Revert \"", "Revert:", "revert(", "revert:")
    private val HOTFIX_KEYWORDS = setOf("hotfix", "hot-fix")
    private const val ROLLBACK_KEYWORD = "rollback"
    private const val CHINESE_ROLLBACK = "回滚"

    fun classify(subject: String): IncidentReason? {
        val normalized = subject.trim()
        if (normalized.isEmpty()) return null

        if (REVERT_PREFIXES.any { normalized.startsWith(it, ignoreCase = true) }) {
            return IncidentReason.REVERT
        }

        // Conventional Commits: `<type>(scope): summary` or `<type>: summary`. We classify only when
        // the keyword IS the type, not when it merely appears in the summary text.
        val firstToken = normalized
            .substringBefore(':')
            .substringBefore('(')
            .substringBefore(' ')
            .lowercase()

        if (firstToken in HOTFIX_KEYWORDS) return IncidentReason.HOTFIX
        if (firstToken == ROLLBACK_KEYWORD) return IncidentReason.ROLLBACK
        if (normalized.startsWith(CHINESE_ROLLBACK)) return IncidentReason.ROLLBACK

        return null
    }
}
