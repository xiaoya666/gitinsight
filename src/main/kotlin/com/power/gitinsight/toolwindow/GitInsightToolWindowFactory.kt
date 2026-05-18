package com.power.gitinsight.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * team : gitInsight.
 * Class Name: GitInsightToolWindowFactory
 * Description: Placeholder Dashboard tool window; real content lands in Sprint 2.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 19:55
 **/
internal class GitInsightToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel().apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(JBLabel("GitInsight dashboard — coming in Sprint 2.", SwingConstants.CENTER))
        }
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
