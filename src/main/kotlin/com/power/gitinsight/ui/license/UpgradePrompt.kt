package com.power.gitinsight.ui.license

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.power.gitinsight.ui.ai.AiSettingsConfigurable

/**
 * team : gitInsight.
 * Class Name: UpgradePrompt
 * Description: Shared "this is a Pro feature" nudge reused by every gated entry point (AI Diff Review,
 *              AI Commit quota, YAML overrides). Per plan TODO-A it never hard-disables an action — it
 *              shows a non-blocking notification with two outs: bring-your-own AI key (free, unlimited)
 *              or upgrade to Pro. During the 1.0.x preview window the gates don't fire, so this is dormant
 *              until PREVIEW_WINDOW_OPEN flips at the 1.1.x launch.
 *
 * @author: power
 * on Date: 2026/06/10 Time: 17:44
 **/
internal object UpgradePrompt {

    fun show(project: Project, reason: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("GitInsight Notifications")
            .createNotification(
                "GitInsight Pro",
                "$reason\n你可以填写自己的 AI Key 免费无限使用，或升级到 Pro 使用托管 Key。",
                NotificationType.INFORMATION
            )
        notification.addAction(NotificationAction.createSimple("填写自己的 Key") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AiSettingsConfigurable::class.java)
            notification.expire()
        })
        notification.addAction(NotificationAction.createSimple("升级到 Pro") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LicenseSettingsConfigurable::class.java)
            notification.expire()
        })
        notification.notify(project)
    }
}
