package com.power.gitinsight.domain.activity

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * team : gitInsight.
 * Class Name: ActivityAggregator
 * Description: Pure-function reducer turning a list of ActivityCommit into the ActivityStats the Activity tab renders.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
internal object ActivityAggregator {

    private const val RECENT_LIMIT = 20

    fun aggregate(commits: List<ActivityCommit>, windowDays: Int): ActivityStats {
        if (commits.isEmpty()) return ActivityStats.empty(windowDays)

        val total = commits.size
        val incidents = commits.count { it.incidentReason != null }
        val byDay = commits
            .groupingBy { dayKey(it.timestamp) }
            .eachCount()
        val recent = commits.sortedByDescending { it.timestamp }.take(RECENT_LIMIT)

        return ActivityStats(
            windowDays = windowDays,
            totalCommits = total,
            incidentCommits = incidents,
            byDay = byDay,
            recent = recent
        )
    }

    /** Stable yyyy-MM-dd bucket in UTC so a commit's day-of-week doesn't depend on viewer locale. */
    internal fun dayKey(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC).toLocalDate()
        return DateTimeFormatter.ISO_LOCAL_DATE.format(date)
    }
}
