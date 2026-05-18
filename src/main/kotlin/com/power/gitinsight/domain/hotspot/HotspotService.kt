package com.power.gitinsight.domain.hotspot

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.infra.storage.BlameStorage
import git4idea.repo.GitRepositoryManager
import java.security.MessageDigest

/**
 * team : gitInsight.
 * Class Name: HotspotService
 * Description: Project-scoped facade over the hotspot SQLite cache plus the rescan trigger; UI / domain layers talk only to this service.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 13:25
 **/
@Service(Service.Level.PROJECT)
internal class HotspotService(private val project: Project) {

    /** Launch a full rescan in the background. Safe to call frequently (Backgroundable runs one at a time). */
    fun rescan() {
        thisLogger().info("HotspotService.rescan() requested for ${project.name}")
        ProgressManager.getInstance().run(HotspotScanTask(project))
    }

    /** Lookup the cached hotspot row for [file]. Returns null when file is unversioned or not yet scanned. */
    fun getHotspot(file: VirtualFile): FileHotspot? {
        val repo = GitRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null
        val rootPath = repo.root.path
        if (!file.path.startsWith(rootPath)) return null
        val relative = file.path.removePrefix(rootPath).trimStart('/')
        val repoId = sha1Hex16(rootPath)

        val storage = project.service<BlameStorage>()
        val row = storage.hotspotQueries
            .selectFileHotspot(repoId, relative)
            .executeAsOneOrNull()
            ?: return null

        return FileHotspot(
            repoId = repoId,
            filePath = relative,
            modifyCount = row.modify_count.toInt(),
            rollbackCount = row.rollback_count.toInt(),
            authorCount = row.author_count.toInt(),
            lastModified = row.last_modified,
            hotspotScore = row.hotspot_score
        )
    }

    /** Top N hottest files in [root]. Empty list if cache hasn't been populated. */
    fun getTopHotspots(root: VirtualFile, limit: Int = 20): List<FileHotspot> {
        val repoId = sha1Hex16(root.path)
        val storage = project.service<BlameStorage>()
        return storage.hotspotQueries
            .selectTopHotspots(repoId, limit.toLong())
            .executeAsList()
            .map {
                FileHotspot(
                    repoId = repoId,
                    filePath = it.file_path,
                    modifyCount = it.modify_count.toInt(),
                    rollbackCount = it.rollback_count.toInt(),
                    authorCount = it.author_count.toInt(),
                    lastModified = it.last_modified,
                    hotspotScore = it.hotspot_score
                )
            }
    }

    private fun sha1Hex16(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
