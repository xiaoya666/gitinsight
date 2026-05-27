package com.power.gitinsight.domain.incident

/**
 * team : gitInsight.
 * Class Name: IncidentModel
 * Description: Value types for incident-commit tracking; CommitRecord is one parsed git-log entry, IncidentEntry is a row written to the incident_commit table.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 11:51
 **/

/** Why we flagged a commit as an incident. Persisted as the `reason` column. */
enum class IncidentReason {
    REVERT,
    HOTFIX,
    ROLLBACK,
    MANUAL  // reserved for user-tagged incidents (future feature)
}

/** One parsed git-log commit. Produced by GitAdapter.scanRepoHistory. */
data class CommitRecord(
    val commitId: String,
    val author: String,
    val timestamp: Long,         // epoch milliseconds
    val subject: String,
    val incidentReason: IncidentReason?
)

/** A row persisted in `incident_commit`. */
data class IncidentEntry(
    val repoId: String,
    val commitId: String,
    val reason: IncidentReason,
    val subject: String,
    val markedAt: Long
)
