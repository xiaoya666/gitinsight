package com.power.gitinsight.infra.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.domain.blame.BlameSnapshot
import com.power.gitinsight.domain.hotspot.FileChangeEvent
import com.power.gitinsight.domain.incident.CommitRecord

/**
 * team : gitInsight.
 * Class Name: GitAdapter
 * Description: Abstracts Git access so domain services stay platform-independent; primary impl uses git4idea, JGit fallback deferred to Sprint 2.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:06
 **/
interface GitAdapter {
    /**
     * Capture per-line blame for the file at its current HEAD revision.
     * Returns null when the file is unversioned, VCS is unavailable, or annotation fails.
     */
    fun blame(project: Project, file: VirtualFile): BlameSnapshot?

    /**
     * Stream every commit since [sinceEpochMs] in [root] and emit one event per (commit, file) pair.
     * Heavy: callers must wrap in a background task with a progress indicator.
     * Returns an empty list on any error (callers log and continue).
     *
     * Default implementation delegates to [scanRepoHistory] and discards commit-level metadata.
     */
    fun scanFileHistory(project: Project, root: VirtualFile, sinceEpochMs: Long): List<FileChangeEvent> =
        scanRepoHistory(project, root, sinceEpochMs).events

    /**
     * Single git-log pass producing both file-level events (for hotspot scoring) and
     * commit-level metadata (for incident-commit classification). Returns [RepoHistory.EMPTY] on any error.
     */
    fun scanRepoHistory(project: Project, root: VirtualFile, sinceEpochMs: Long): RepoHistory
}

/** One scan's worth of git-log output, split into file-level and commit-level views. */
data class RepoHistory(
    val events: List<FileChangeEvent>,
    val commits: List<CommitRecord>
) {
    companion object {
        val EMPTY = RepoHistory(emptyList(), emptyList())
    }
}
