package com.power.gitinsight

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.power.gitinsight.domain.hotspot.HotspotService
import com.power.gitinsight.domain.telemetry.TelemetrySettings
import com.power.gitinsight.infra.git.GitChangeListener
import kotlinx.coroutines.delay

/**
 * team : gitInsight.
 * Class Name: GitInsightStartupActivity
 * Description: GitInsight 插件入口；IDE 打开项目后初始化日志与后续服务挂载点。
 *
 * @author: power
 * on Date: 2026/05/17 Time: 18:24
 **/
internal class GitInsightStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        thisLogger().info("GitInsight initialized for project: ${project.name}")
        // Eagerly instantiate the git change listener so it subscribes to the message bus
        // at startup (project services are otherwise lazy and would never wake up).
        project.service<GitChangeListener>()

        // First-launch telemetry consent prompt. Default stays disabled; we only ask once.
        maybeAskTelemetryConsent(project)

        // Delay first hotspot scan so it doesn't compete with IDE indexing on startup.
        delay(5_000)
        project.service<HotspotService>().rescan()
    }

    private fun maybeAskTelemetryConsent(project: Project) {
        val settings = TelemetrySettings.getInstance()
        if (settings.consentPrompted) return

        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("GitInsight Notifications") ?: return

        val notification = group.createNotification(
            "GitInsight 匿名遥测",
            "默认关闭。是否允许在崩溃时发送匿名堆栈（仅版本号 + 匿名 ID，不含代码 / 路径 / 用户名）？",
            NotificationType.INFORMATION
        )
        notification.addAction(NotificationAction.createSimple("启用") {
            settings.grantConsent(enable = true)
            notification.expire()
        })
        notification.addAction(NotificationAction.createSimple("拒绝") {
            settings.grantConsent(enable = false)
            notification.expire()
        })
        notification.addAction(object : NotificationAction("稍后再问") {
            override fun actionPerformed(e: AnActionEvent, n: Notification) {
                // Intentionally do NOT mark consentPrompted; user wants to defer the decision.
                n.expire()
            }
        })
        notification.notify(project)
    }
}
