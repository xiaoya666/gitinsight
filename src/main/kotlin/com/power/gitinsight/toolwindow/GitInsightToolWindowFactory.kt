package com.power.gitinsight.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * team : gitInsight.
 * Class Name: GitInsightToolWindowFactory
 * Description: Hosts the Hotspot Dashboard panel (Top N hottest files).
 *
 * @author: power
 * on Date: 2026/05/17 Time: 19:55
 **/
internal class GitInsightToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val cf = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(
            cf.createContent(HotspotDashboardPanel(project), "Hotspots", false)
        )
        toolWindow.contentManager.addContent(
            cf.createContent(ActivityDashboardPanel(project), "Activity", false)
        )
    }
}
