package com.power.gitinsight.domain.incident

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.infra.storage.BlameStorage
import git4idea.repo.GitRepositoryManager
import java.security.MessageDigest

/**
 * team : gitInsight.
 * Class Name: IncidentService
 * Description: Project-scoped read-side gateway over the incident_commit table; resolves a commit SHA to its IncidentInfo (reason + subject + when-marked). Tooltip callers pass a VirtualFile and we derive the repo id ourselves so the UI layer stays Git-agnostic.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 11:51
 **/
@Service(Service.Level.PROJECT)
internal class IncidentService(private val project: Project) {

    /** Tooltip-facing info bundle. */
    data class IncidentInfo(
        val reason: IncidentReason,
        val subject: String,
        val markedAt: Long
    )

    /**
     * Find the incident record (if any) for [commitId] inside the repo that owns [file].
     * Returns null when the commit isn't flagged or when the repo/file is unknown.
     */
    fun findIncident(file: VirtualFile, commitId: String): IncidentInfo? {
        if (commitId.isBlank()) return null
        val repoId = repoIdFor(file) ?: return null
        return findIncident(repoId, commitId)
    }

    fun findIncident(repoId: String, commitId: String): IncidentInfo? {
        if (commitId.isBlank()) return null
        return runCatching {
            project.service<BlameStorage>()
                .incidentQueries
                .selectIncident(repoId, commitId)
                .executeAsOneOrNull()
                ?.let { row ->
                    val reason = parseReason(row.reason) ?: return@let null
                    IncidentInfo(reason = reason, subject = row.subject, markedAt = row.marked_at)
                }
        }.onFailure {
            thisLogger().info("IncidentService.findIncident($repoId, $commitId) failed: ${it.message}")
        }.getOrNull()
    }

    /** sha1-16-hex of the containing repo root path, matching HotspotScanTask's keying scheme. */
    private fun repoIdFor(file: VirtualFile): String? {
        val repo = GitRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null
        return sha1Hex16(repo.root.path)
    }

    private fun parseReason(text: String): IncidentReason? =
        runCatching { IncidentReason.valueOf(text) }.getOrNull()

    private fun sha1Hex16(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
