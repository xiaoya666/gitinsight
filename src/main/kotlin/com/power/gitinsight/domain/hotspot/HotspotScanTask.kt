package com.power.gitinsight.domain.hotspot

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.power.gitinsight.infra.git.GitAdapter
import com.power.gitinsight.infra.storage.BlameStorage
import git4idea.repo.GitRepositoryManager
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: HotspotScanTask
 * Description: Backgroundable task that runs `git log` per repo, aggregates via HotspotAggregator, upserts to SQLite.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 13:22
 **/
internal class HotspotScanTask(
    project: Project,
    private val windowDays: Long = DEFAULT_WINDOW_DAYS
) : Task.Backgroundable(project, "GitInsight: scanning file history", true) {

    override fun run(indicator: ProgressIndicator) {
        val proj = myProject ?: return
        val repos = GitRepositoryManager.getInstance(proj).repositories
        if (repos.isEmpty()) {
            thisLogger().info("HotspotScanTask: no Git repositories in project ${proj.name}")
            return
        }

        val sinceMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(windowDays)
        val storage = proj.service<BlameStorage>()
        val adapter = service<GitAdapter>()
        var totalRows = 0

        for ((index, repo) in repos.withIndex()) {
            indicator.checkCanceled()
            indicator.fraction = index.toDouble() / repos.size
            indicator.text = "GitInsight: scanning ${repo.root.name}"

            val events = adapter.scanFileHistory(proj, repo.root, sinceMs)
            if (events.isEmpty()) {
                thisLogger().info("HotspotScanTask: ${repo.root.path} produced 0 events")
                continue
            }

            val repoId = sha1Hex16(repo.root.path)
            val aggregated = HotspotAggregator.aggregate(repoId, events)
            val now = System.currentTimeMillis()

            runCatching {
                storage.hotspotQueries.transaction {
                    aggregated.values.forEach { h ->
                        storage.hotspotQueries.upsertFileHotspot(
                            repo_id = h.repoId,
                            file_path = h.filePath,
                            modify_count = h.modifyCount.toLong(),
                            rollback_count = h.rollbackCount.toLong(),
                            author_count = h.authorCount.toLong(),
                            last_modified = h.lastModified,
                            hotspot_score = h.hotspotScore,
                            updated_at = now
                        )
                    }
                }
            }.onFailure {
                thisLogger().info("HotspotScanTask: upsert failed for ${repo.root.path}: ${it.message}")
            }.onSuccess {
                totalRows += aggregated.size
                thisLogger().info("HotspotScanTask: wrote ${aggregated.size} hotspot rows for ${repo.root.path}")
            }
        }

        indicator.fraction = 1.0
        thisLogger().info("HotspotScanTask: done. total rows=$totalRows across ${repos.size} repo(s)")
    }

    private fun sha1Hex16(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    companion object {
        private const val DEFAULT_WINDOW_DAYS = 365L
    }
}
