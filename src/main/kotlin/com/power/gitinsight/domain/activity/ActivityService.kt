package com.power.gitinsight.domain.activity

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.domain.incident.IncidentClassifier
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: ActivityService
 * Description: Project-scoped service that runs `git log --author=<me>` within a time window, parses the output into ActivityCommit, and hands it to ActivityAggregator. UI consumers see only ActivityStats. Errors are logged and folded into ActivityStats.empty so the dashboard always has something to render.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
@Service(Service.Level.PROJECT)
internal class ActivityService(private val project: Project) {

    /** Compute personal stats for [root] over the trailing [windowDays] days. */
    fun computeStats(root: VirtualFile, windowDays: Int = DEFAULT_WINDOW_DAYS): ActivityStats {
        val email = currentUserEmail(root) ?: run {
            thisLogger().info("ActivityService: no git user.email configured for ${root.path}")
            return ActivityStats.empty(windowDays)
        }

        val sinceMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(windowDays.toLong())
        val commits = scanCommitsByAuthor(root, email, sinceMs)
        return ActivityAggregator.aggregate(commits, windowDays)
    }

    /** Email is the canonical author identity; %an names drift after `git config` changes. */
    private fun currentUserEmail(root: VirtualFile): String? = try {
        val handler = GitLineHandler(project, root, GitCommand.CONFIG)
        handler.addParameters("user.email")
        val result = Git.getInstance().runCommand(handler)
        if (result.success()) result.outputAsJoinedString.trim().takeIf { it.isNotEmpty() } else null
    } catch (e: Exception) {
        thisLogger().info("ActivityService.currentUserEmail() failed: ${e.message}")
        null
    }

    private fun scanCommitsByAuthor(root: VirtualFile, email: String, sinceEpochMs: Long): List<ActivityCommit> {
        return try {
            val handler = GitLineHandler(project, root, GitCommand.LOG)
            val sinceSec = (sinceEpochMs / 1000L).coerceAtLeast(0L)
            handler.addParameters(
                "--no-merges",
                "--encoding=UTF-8",
                "--author=$email",
                "--pretty=format:%H${SEP}%at${SEP}%s",
                "--since=$sinceSec"
            )
            val result = Git.getInstance().runCommand(handler)
            if (!result.success()) {
                thisLogger().info("git log --author failed for ${root.path}: ${result.errorOutputAsJoinedString}")
                return emptyList()
            }
            result.output.mapNotNull(::parseLine)
        } catch (e: Exception) {
            thisLogger().info("ActivityService.scanCommitsByAuthor() failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseLine(line: String): ActivityCommit? {
        if (line.isBlank()) return null
        val parts = line.split(SEP, limit = 3)
        if (parts.size < 3) return null
        val ts = (parts[1].toLongOrNull() ?: return null) * 1000L
        val subject = parts[2]
        return ActivityCommit(
            commitId = parts[0],
            timestamp = ts,
            subject = subject,
            incidentReason = IncidentClassifier.classify(subject)
        )
    }

    private companion object {
        const val DEFAULT_WINDOW_DAYS = 30
        const val SEP = "\u001F"  // Unit Separator; safe inside commit metadata
    }
}
