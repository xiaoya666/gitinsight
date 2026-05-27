package com.power.gitinsight.ui.checkin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.changes.Change
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.ai.AiOptions
import com.power.gitinsight.domain.ai.AiResult
import com.power.gitinsight.domain.ai.AiReviewPrompt
import com.power.gitinsight.domain.ai.AiSettings
import com.power.gitinsight.domain.risk.RiskLevel
import com.power.gitinsight.domain.risk.RiskReport
import com.power.gitinsight.ui.ai.AiReviewDialog
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: RiskDialog
 * Description: Modal commit-time view of a RiskReport — score header (colored by level), triggered rules
 *              with evidence, suggestions. OK = continue submitting; Cancel = abort the commit.
 *              Left-side "让 AI 审一下" button kicks off the AI review flow without dismissing the dialog,
 *              so the user can act on AI feedback and then decide whether to proceed.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 21:08
 **/
internal class RiskDialog(
    private val project: Project,
    private val changes: List<Change>,
    private val report: RiskReport
) : DialogWrapper(project) {

    init {
        title = "GitInsight: Commit Risk Score"
        setOKButtonText("继续提交")
        setCancelButtonText("取消")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        root.add(leftAlign(buildHeader()))
        root.add(Box.createVerticalStrut(12))
        root.add(leftAlign(buildRulesList()))
        if (report.suggestions.isNotEmpty()) {
            root.add(Box.createVerticalStrut(12))
            root.add(leftAlign(buildSuggestions()))
        }
        return root
    }

    /** Left-side actions live next to Cancel — used here for the non-dismissing AI Review trigger. */
    override fun createLeftSideActions(): Array<Action> =
        if (changes.isEmpty()) emptyArray() else arrayOf(
            object : AbstractAction("🔎 让 AI 审一下") {
                override fun actionPerformed(e: ActionEvent?) {
                    isEnabled = false  // disable while running so the user can't pile up requests
                    triggerAiReview { isEnabled = true }
                }
            }
        )

    private fun triggerAiReview(onDone: () -> Unit) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "GitInsight: Running AI diff review", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val messages = listOf(
                        AiReviewPrompt.systemMessage(),
                        AiReviewPrompt.userMessage(changes)
                    )
                    val provider = AiSettings.getInstance().activeProvider()
                    val result = provider.complete(
                        messages,
                        AiOptions(maxTokens = 2048, temperature = 0.2, timeoutSeconds = 60)
                    )
                    ApplicationManager.getApplication().invokeLater {
                        onDone()
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

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GitInsight Notifications")
            .createNotification(message, type)
            .notify(project)
    }

    private fun buildHeader(): JComponent {
        val label = JBLabel("Risk Score: ${report.totalScore} / 100  (${report.level})")
        label.font = label.font.deriveFont(Font.BOLD, 18f)
        label.foreground = levelColor(report.level)
        return label
    }

    private fun buildRulesList(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createTitledBorder("触发规则")
        }
        if (report.matches.isEmpty()) {
            panel.add(leftAlign(JBLabel("（无规则触发）")))
        } else {
            report.matches.forEach { match ->
                panel.add(leftAlign(JBLabel("• ${match.message} (+${match.scoreDelta})")))
                match.evidence.take(MAX_EVIDENCE_LINES).forEach { ev ->
                    val muted = JBLabel("    $ev").apply { foreground = JBColor.GRAY }
                    panel.add(leftAlign(muted))
                }
                if (match.evidence.size > MAX_EVIDENCE_LINES) {
                    val rest = match.evidence.size - MAX_EVIDENCE_LINES
                    val overflow = JBLabel("    ... 还有 $rest 项").apply { foreground = JBColor.GRAY }
                    panel.add(leftAlign(overflow))
                }
            }
        }
        return panel
    }

    private fun buildSuggestions(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = IdeBorderFactory.createTitledBorder("建议")
        }
        report.suggestions.forEach { panel.add(leftAlign(JBLabel("• $it"))) }
        return panel
    }

    private fun leftAlign(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }

    private fun levelColor(level: RiskLevel): Color = when (level) {
        RiskLevel.HIGH -> JBColor(Color(244, 67, 54), Color(255, 99, 71))
        RiskLevel.MEDIUM -> JBColor(Color(255, 152, 0), Color(255, 165, 0))
        RiskLevel.LOW -> JBColor(Color(76, 175, 80), Color(120, 200, 120))
    }

    private companion object {
        const val MAX_EVIDENCE_LINES = 3
    }
}
