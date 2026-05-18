package com.power.gitinsight.infra.git

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.domain.blame.BlameLine
import com.power.gitinsight.domain.blame.BlameSnapshot
import com.power.gitinsight.domain.hotspot.FileChangeEvent
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

/**
 * team : gitInsight.
 * Class Name: Git4IdeaAdapter
 * Description: GitAdapter implementation backed by IntelliJ's VCS AnnotationProvider; primary path for blame inside an IDE process.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:07
 **/
internal class Git4IdeaAdapter : GitAdapter {

    override fun blame(project: Project, file: VirtualFile): BlameSnapshot? {
        val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file)
            ?: return null
        val provider = vcs.annotationProvider ?: return null

        val annotation: FileAnnotation = try {
            provider.annotate(file)
        } catch (e: Exception) {
            thisLogger().info("annotate() failed for ${file.path}: ${e.message}")
            return null
        }

        return try {
            val revisionByNumber = annotation.revisions
                .orEmpty()
                .associateBy { it.revisionNumber }

            val lines = (0 until annotation.lineCount).mapNotNull { idx ->
                val rev = annotation.getLineRevisionNumber(idx) ?: return@mapNotNull null
                val fileRev = revisionByNumber[rev]
                BlameLine(
                    lineNumber = idx + 1,
                    commitId = rev.asString(),
                    author = fileRev?.author.orEmpty(),
                    authorEmail = null, // platform API does not expose email; enrich later via git log
                    timestamp = fileRev?.revisionDate?.time ?: 0L,
                    summary = fileRev?.commitMessage?.lineSequence()?.firstOrNull().orEmpty()
                )
            }

            BlameSnapshot(
                filePath = file.path,
                headCommitId = annotation.currentRevision?.asString().orEmpty(),
                lines = lines
            )
        } finally {
            annotation.dispose()
        }
    }

    override fun scanFileHistory(
        project: Project,
        root: VirtualFile,
        sinceEpochMs: Long
    ): List<FileChangeEvent> {
        return try {
            val handler = GitLineHandler(project, root, GitCommand.LOG)
            val sinceSec = (sinceEpochMs / 1000L).coerceAtLeast(0L)
            handler.addParameters(
                "--name-only",
                "--no-renames",
                "--encoding=UTF-8",
                "--pretty=format:${COMMIT_MARKER}%H${SEP}%an${SEP}%at${SEP}%s",
                "--since=$sinceSec"
            )
            val result = Git.getInstance().runCommand(handler)
            if (!result.success()) {
                thisLogger().info("git log failed for ${root.path}: ${result.errorOutputAsJoinedString}")
                return emptyList()
            }
            parseGitLogOutput(result.output)
        } catch (e: Exception) {
            thisLogger().info("scanFileHistory() failed for ${root.path}: ${e.message}")
            emptyList()
        }
    }

    private fun parseGitLogOutput(lines: List<String>): List<FileChangeEvent> {
        val events = mutableListOf<FileChangeEvent>()
        var current: ParsedCommit? = null

        for (line in lines) {
            if (line.isEmpty()) continue
            if (line.startsWith(COMMIT_MARKER)) {
                val parts = line.substring(COMMIT_MARKER.length).split(SEP, limit = 4)
                if (parts.size < 4) continue
                val timestampSec = parts[2].toLongOrNull() ?: 0L
                current = ParsedCommit(
                    commitId = parts[0],
                    author = parts[1],
                    timestampMs = timestampSec * 1000L,
                    isRevert = isRevertSubject(parts[3])
                )
            } else {
                val commit = current ?: continue
                events.add(
                    FileChangeEvent(
                        filePath = line,
                        commitId = commit.commitId,
                        author = commit.author,
                        timestamp = commit.timestampMs,
                        isRevert = commit.isRevert
                    )
                )
            }
        }
        return events
    }

    private fun isRevertSubject(subject: String): Boolean =
        subject.startsWith("Revert \"", ignoreCase = true) ||
            subject.startsWith("Revert:", ignoreCase = true)

    private data class ParsedCommit(
        val commitId: String,
        val author: String,
        val timestampMs: Long,
        val isRevert: Boolean
    )

    private companion object {
        // Unique marker for commit header lines; `\u001F` (Unit Separator) practically never appears in commit metadata.
        private const val SEP = "\u001F"
        private const val COMMIT_MARKER = "GI_COMMIT$SEP"
    }
}
