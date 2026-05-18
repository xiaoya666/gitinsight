package com.power.gitinsight

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.power.gitinsight.domain.hotspot.HotspotService
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

        // Delay first hotspot scan so it doesn't compete with IDE indexing on startup.
        delay(5_000)
        project.service<HotspotService>().rescan()
    }
}
