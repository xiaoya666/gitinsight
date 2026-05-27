package com.power.gitinsight.domain.activity

import com.power.gitinsight.domain.incident.IncidentReason

/**
 * team : gitInsight.
 * Class Name: ActivityModel
 * Description: Value types for the personal-activity dashboard (L2). ActivityCommit is one parsed git-log entry for the current user; ActivityStats is the aggregated rollup the UI consumes.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/

/** One commit authored by the dashboard's "me" user. */
data class ActivityCommit(
    val commitId: String,
    val timestamp: Long,            // epoch milliseconds
    val subject: String,
    val incidentReason: IncidentReason?
)

/**
 * Aggregated view shown in the Activity tab.
 * @param windowDays the time window the stats were computed over (echoed back for UI labels)
 * @param totalCommits count of all user commits in the window
 * @param incidentCommits subset where incidentReason != null (REVERT / HOTFIX / ROLLBACK)
 * @param byDay yyyy-MM-dd (UTC) → commit count; sparse map (days with zero commits omitted)
 * @param recent up to N most recent commits for the table view
 */
data class ActivityStats(
    val windowDays: Int,
    val totalCommits: Int,
    val incidentCommits: Int,
    val byDay: Map<String, Int>,
    val recent: List<ActivityCommit>
) {
    val incidentRate: Double
        get() = if (totalCommits == 0) 0.0 else incidentCommits.toDouble() / totalCommits

    companion object {
        fun empty(windowDays: Int) = ActivityStats(windowDays, 0, 0, emptyMap(), emptyList())
    }
}
