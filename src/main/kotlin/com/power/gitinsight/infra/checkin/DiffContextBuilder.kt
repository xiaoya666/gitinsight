package com.power.gitinsight.infra.checkin

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.power.gitinsight.domain.hotspot.HotspotService
import com.power.gitinsight.domain.risk.DiffContext
import com.power.gitinsight.domain.risk.FileDiff
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * team : gitInsight.
 * Class Name: DiffContextBuilder
 * Description: Adapter that materializes a DiffContext from a CheckinProjectPanel. Pulls content from
 *              ContentRevisions and pre-resolves hotspot scores so the engine stays a pure function.
 *              Caller MUST invoke this off the EDT — ContentRevision.content and getRepositoryForFile
 *              both assert background-thread access in 2024.2+.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 21:08
 **/
internal object DiffContextBuilder {

    fun build(project: Project, panel: CheckinProjectPanel): DiffContext {
        val changes = panel.selectedChanges
        val repoManager = GitRepositoryManager.getInstance(project)
        val hotspotService = project.service<HotspotService>()

        val files = mutableListOf<FileDiff>()
        val hotspotByPath = mutableMapOf<String, Double>()

        for (change in changes) {
            val (relativePath, repo) = relativePathAndRepo(change, repoManager) ?: continue
            val before = readContent { change.beforeRevision?.content }
            val after = readContent { change.afterRevision?.content }

            val beforeLines = lineCount(before)
            val afterLines = lineCount(after)
            // Net delta is enough for the v1 rules (LargeDelete cares about beforeLines vs delete count).
            val deletedLines = (beforeLines - afterLines).coerceAtLeast(0)
            val addedLines = (afterLines - beforeLines).coerceAtLeast(0)

            files += FileDiff(
                filePath = relativePath,
                addedLines = addedLines,
                deletedLines = deletedLines,
                totalLinesBefore = beforeLines,
                contentSnippet = after
            )

            val vf = change.virtualFile
            if (vf != null && repo != null) {
                val score = runCatching { hotspotService.getHotspot(vf)?.hotspotScore }.getOrNull()
                if (score != null) hotspotByPath[relativePath] = score
            }
        }

        return DiffContext(files) { path -> hotspotByPath[path] }
    }

    private fun relativePathAndRepo(
        change: Change,
        repoManager: GitRepositoryManager
    ): Pair<String, GitRepository?>? {
        val vf = change.virtualFile
            ?: change.afterRevision?.file?.virtualFile
            ?: change.beforeRevision?.file?.virtualFile
        val full = vf?.path
            ?: change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
            ?: return null
        val repo = vf?.let { repoManager.getRepositoryForFile(it) }
        val rootPath = repo?.root?.path
        val relative = if (rootPath != null && full.startsWith(rootPath)) {
            full.removePrefix(rootPath).trimStart('/')
        } else {
            // No git root match (submodule edge or non-VCS file) — fall back to the path tail.
            full.substringAfterLast('/')
        }
        return relative to repo
    }

    private fun readContent(supplier: () -> String?): String =
        runCatching { supplier().orEmpty() }.getOrDefault("")

    private fun lineCount(text: String): Int {
        if (text.isEmpty()) return 0
        return text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1
    }
}
