package com.power.gitinsight.ui.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.risk.RiskLevel
import com.power.gitinsight.domain.risk.RiskReport
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: RiskDialog
 * Description: Modal commit-time view of a RiskReport — score header (colored by level), triggered rules
 *              with evidence, suggestions. OK = continue submitting; Cancel = abort the commit.
 *              Sprint 4 will plug an "AI Review" side action; for now we ship the deterministic view.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 21:08
 **/
internal class RiskDialog(project: Project, private val report: RiskReport) : DialogWrapper(project) {

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
