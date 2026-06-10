package com.power.gitinsight.ui.ai

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.power.gitinsight.domain.ai.AiOptions
import com.power.gitinsight.domain.ai.AiResult
import com.power.gitinsight.domain.ai.AiReviewPrompt
import com.power.gitinsight.domain.ai.AiSettings
import com.power.gitinsight.domain.license.LicenseSettings
import com.power.gitinsight.ui.license.UpgradePrompt

/**
 * team : gitInsight.
 * Class Name: AiReviewAction
 * Description: "AI Review This Diff" — runs the active AI provider against the current changes (either the
 *              caller's selection from the changelist popup, or the default changelist) and shows the
 *              markdown response in AiReviewDialog. Always returns control quickly via Task.Backgroundable.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class AiReviewAction : AnAction(
    "🔎 GitInsight: AI Review Diff [Pro Preview]",
    "Pro Preview — free during 1.0.x. Asks the configured AI provider to review the current changes.",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // Pro gate (dormant during the 1.0.x preview window — unlocksProFeatures() is true for everyone).
        if (!LicenseSettings.getInstance().unlocksProFeatures()) {
            UpgradePrompt.show(project, "AI Diff Review 是 Pro 功能。")
            return
        }
        val changes = collectChanges(e, project)
        if (changes.isEmpty()) {
            notify(project, "GitInsight: 当前 changelist 为空，没有内容可审。", NotificationType.INFORMATION)
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "GitInsight: Running AI diff review", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Composing prompt..."
                    val messages = listOf(
                        AiReviewPrompt.systemMessage(),
                        AiReviewPrompt.userMessage(changes)
                    )

                    indicator.text = "Calling AI provider..."
                    val provider = AiSettings.getInstance().activeProvider()
                    // Reviews need more room than a commit message — 2048 tokens covers most diffs.
                    val result = provider.complete(
                        messages,
                        AiOptions(maxTokens = 2048, temperature = 0.2, timeoutSeconds = 60)
                    )

                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        when (result) {
                            is AiResult.Success -> AiReviewDialog(project, provider.displayName, result.text).show()
                            is AiResult.Error -> {
                                thisLogger().info("[GitInsight] AI review failed: ${result.message}")
                                notify(project, "GitInsight: ${result.message}", NotificationType.WARNING)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun collectChanges(e: AnActionEvent, project: Project): List<Change> {
        // Prefer the user's selection (right-click on specific files in the changelist).
        val selected = e.getData(VcsDataKeys.CHANGES)?.toList().orEmpty()
        if (selected.isNotEmpty()) return selected
        return ChangeListManager.getInstance(project).defaultChangeList.changes.toList()
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GitInsight Notifications")
            .createNotification(message, type)
            .notify(project)
    }
}
