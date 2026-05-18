package com.power.gitinsight.domain.blame

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.infra.git.GitAdapter
import com.power.gitinsight.infra.storage.BlameStorage
import git4idea.repo.GitRepositoryManager
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * team : gitInsight.
 * Class Name: BlameService
 * Description: Cache-aside facade over GitAdapter + BlameStorage; UI talks only to this service.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:15
 **/
@Service(Service.Level.PROJECT)
internal class BlameService(private val project: Project) {

    private val cacheTtlMs = TimeUnit.DAYS.toMillis(30)

    fun getBlame(file: VirtualFile): BlameSnapshot? {
        pruneStaleEntries()

        val context = describeFile(file)
        if (context == null) {
            return service<GitAdapter>().blame(project, file)
        }

        readFromCache(context)?.let { return it }

        val fresh = service<GitAdapter>().blame(project, file) ?: return null
        writeToCache(context, fresh)
        return fresh.copy(filePath = context.relativePath, headCommitId = context.headCommitId)
    }

    private fun readFromCache(ctx: FileContext): BlameSnapshot? {
        val storage = project.service<BlameStorage>()
        val count = storage.blameQueries
            .countByCommit(ctx.repoId, ctx.relativePath, ctx.headCommitId)
            .executeAsOne()
        if (count <= 0L) return null

        val rows = storage.blameQueries
            .selectByCommit(ctx.repoId, ctx.relativePath, ctx.headCommitId)
            .executeAsList()
        val lines = rows.map {
            BlameLine(
                lineNumber = it.line_number.toInt(),
                commitId = it.line_commit_id,
                author = it.author,
                authorEmail = it.author_email,
                timestamp = it.timestamp,
                summary = it.summary
            )
        }
        return BlameSnapshot(
            filePath = ctx.relativePath,
            headCommitId = ctx.headCommitId,
            lines = lines
        )
    }

    private fun writeToCache(ctx: FileContext, snapshot: BlameSnapshot) {
        val storage = project.service<BlameStorage>()
        val now = System.currentTimeMillis()
        runCatching {
            storage.blameQueries.transaction {
                snapshot.lines.forEach { line ->
                    storage.blameQueries.upsertLine(
                        repo_id = ctx.repoId,
                        file_path = ctx.relativePath,
                        commit_id = ctx.headCommitId,
                        line_number = line.lineNumber.toLong(),
                        line_commit_id = line.commitId,
                        author = line.author,
                        author_email = line.authorEmail,
                        timestamp = line.timestamp,
                        summary = line.summary,
                        created_at = now
                    )
                }
            }
        }.onFailure { thisLogger().info("Cache write failed: ${it.message}") }
    }

    private fun pruneStaleEntries() {
        val cutoff = System.currentTimeMillis() - cacheTtlMs
        runCatching {
            project.service<BlameStorage>().blameQueries.deleteOlderThan(cutoff)
        }.onFailure { thisLogger().info("Cache prune failed: ${it.message}") }
    }

    private fun describeFile(file: VirtualFile): FileContext? {
        val repo = GitRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null
        val head = repo.currentRevision?.takeIf { it.isNotBlank() } ?: return null
        val rootPath = repo.root.path
        if (!file.path.startsWith(rootPath)) return null
        val relative = file.path.removePrefix(rootPath).trimStart('/')
        return FileContext(
            repoId = sha1Hex16(rootPath),
            relativePath = relative,
            headCommitId = head
        )
    }

    private fun sha1Hex16(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private data class FileContext(
        val repoId: String,
        val relativePath: String,
        val headCommitId: String
    )
}
