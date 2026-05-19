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
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.power.gitinsight.domain.ai.AiOptions
import com.power.gitinsight.domain.ai.AiResult
import com.power.gitinsight.domain.ai.AiSettings
import com.power.gitinsight.domain.ai.CommitMessagePrompt

/**
 * team : gitInsight.
 * Class Name: GenerateCommitMessageAction
 * Description: Toolbar / Tools menu action — collects the project's pending changes, asks the configured
 *              AI provider for a Conventional Commits message, and either injects it into the commit
 *              message field (when invoked from the commit dialog) or pops a notification with the text.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class GenerateCommitMessageAction : AnAction(
    "✨ Commit Radar: AI Commit Message",
    "Generate a commit message from the staged changes",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val changes = ChangeListManager.getInstance(project).defaultChangeList.changes.toList()
        if (changes.isEmpty()) {
            notify(project, "Commit Radar: 当前 changelist 为空，没有内容可总结。", NotificationType.INFORMATION)
            return
        }
        val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Commit Radar: Generating commit message", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Composing prompt..."
                    val messages = listOf(
                        CommitMessagePrompt.systemMessage(),
                        CommitMessagePrompt.userMessage(changes)
                    )

                    indicator.text = "Calling AI provider..."
                    val provider = AiSettings.getInstance().activeProvider()
                    val result = provider.complete(messages, AiOptions(maxTokens = 512, temperature = 0.3))

                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        when (result) {
                            is AiResult.Success -> handleSuccess(project, commitMessageControl, result.text)
                            is AiResult.Error -> {
                                thisLogger().info("[GitInsight] AI commit message failed: ${result.message}")
                                notify(project, "Commit Radar: ${result.message}", NotificationType.WARNING)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun handleSuccess(project: Project, control: CommitMessage?, text: String) {
        val trimmed = text.trim().removeSurrounding("```").trim()
        if (control != null) {
            control.setCommitMessage(trimmed)
            notify(project, "Commit Radar: 已写入 commit message。", NotificationType.INFORMATION)
        } else {
            notify(
                project,
                "Commit Radar 建议的 commit message：\n\n$trimmed",
                NotificationType.INFORMATION
            )
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Commit Radar Notifications")
            .createNotification(message, type)
            .notify(project)
    }
}
