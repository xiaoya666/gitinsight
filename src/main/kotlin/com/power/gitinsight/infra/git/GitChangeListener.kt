package com.power.gitinsight.infra.git

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.power.gitinsight.domain.hotspot.HotspotService
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.ConcurrentHashMap

/**
 * team : gitInsight.
 * Class Name: GitChangeListener
 * Description: Forces line-marker recompute when HEAD moves (checkout / rebase / pull) so blame info never goes stale on screen.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 23:33
 **/
@Service(Service.Level.PROJECT)
internal class GitChangeListener(private val project: Project) : Disposable {

    private val lastHeadByRoot = ConcurrentHashMap<String, String>()

    init {
        seedKnownRepos()
        project.messageBus.connect(this).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { repo -> onRepoChanged(repo) }
        )
    }

    private fun seedKnownRepos() {
        GitRepositoryManager.getInstance(project).repositories.forEach { repo ->
            lastHeadByRoot[repo.root.path] = repo.currentRevision.orEmpty()
        }
    }

    private fun onRepoChanged(repo: GitRepository) {
        val key = repo.root.path
        val newHead = repo.currentRevision.orEmpty()
        val prev = lastHeadByRoot.put(key, newHead)
        if (prev != null && prev != newHead) {
            thisLogger().info("HEAD changed in $key: ${prev.take(8)} -> ${newHead.take(8)}; restarting daemon + rescanning hotspots")
            DaemonCodeAnalyzer.getInstance(project).restart()
            project.service<HotspotService>().rescan()
        }
    }

    override fun dispose() {
        lastHeadByRoot.clear()
    }
}
