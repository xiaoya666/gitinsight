package com.power.gitinsight.ui.telemetry

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.telemetry.TelemetryService
import com.power.gitinsight.domain.telemetry.TelemetrySettings
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: TelemetrySettingsConfigurable
 * Description: Preferences > Tools > GitInsight: Telemetry. Lets the user toggle the anonymous
 *              crash-report channel, change the endpoint, and ping it for connectivity. Read-only
 *              display of the installId so users know exactly what we'd send.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
internal class TelemetrySettingsConfigurable : Configurable {

    private val enabledBox = JBCheckBox("启用匿名遥测（仅崩溃堆栈 + IDE / 插件版本 + 匿名 ID）")
    private val endpointField = JBTextField(40)
    private val installIdLabel = JBLabel("")
    private val pingButton = JButton("发送测试请求")

    override fun getDisplayName(): String = "Telemetry"

    override fun createComponent(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        panel.add(leftAlign(JBLabel(
            "<html>默认关闭。开启后我们仅会上报：异常堆栈、IDE 版本、插件版本、匿名安装 ID。<br/>" +
                "<b>绝不</b>上报代码、文件路径、用户名、Git URL。</html>"
        )))
        panel.add(Box.createVerticalStrut(10))
        panel.add(leftAlign(enabledBox))
        panel.add(Box.createVerticalStrut(8))
        panel.add(leftAlign(JBLabel("上报端点")))
        panel.add(leftAlign(endpointField))
        panel.add(Box.createVerticalStrut(8))
        panel.add(leftAlign(installIdLabel))
        panel.add(Box.createVerticalStrut(8))
        panel.add(leftAlign(pingButton))

        pingButton.addActionListener {
            // Apply pending changes first so the ping uses what the user just typed.
            if (isModified) apply()
            val ok = TelemetryService.getInstance().sendPing()
            if (ok) {
                Messages.showInfoMessage("测试请求已发出（HTTP 2xx）。", "GitInsight Telemetry")
            } else {
                Messages.showWarningDialog(
                    "测试请求失败：端点不可达，或当前开关处于关闭状态。",
                    "GitInsight Telemetry"
                )
            }
        }

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = TelemetrySettings.getInstance()
        return enabledBox.isSelected != settings.enabled ||
            endpointField.text.trim() != settings.endpoint
    }

    override fun apply() {
        val settings = TelemetrySettings.getInstance()
        settings.setEnabled(enabledBox.isSelected)
        settings.setEndpoint(endpointField.text.trim())
        // Mark prompted so the startup balloon won't appear again after the user visited this page.
        settings.grantConsent(enabledBox.isSelected)
    }

    override fun reset() {
        val settings = TelemetrySettings.getInstance()
        enabledBox.isSelected = settings.enabled
        endpointField.text = settings.endpoint
        installIdLabel.text = "<html>安装 ID（只读）：<code>${settings.installId}</code></html>"
    }

    private fun leftAlign(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }
}
