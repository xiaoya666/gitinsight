package com.power.gitinsight.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * team : gitInsight.
 * Class Name: HelloAction
 * Description: Sanity-check action that proves the plugin's action system + notification API are wired up.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 19:44
 **/
internal class HelloAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val projectName = e.project?.name ?: "<no project>"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Commit Radar Notifications")
            .createNotification(
                "Commit Radar is alive in project $projectName",
                NotificationType.INFORMATION
            )
            .notify(e.project)
    }
}
