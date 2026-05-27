package com.power.gitinsight.ui.license

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.license.LicenseSettings
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: LicenseSettingsConfigurable
 * Description: Settings ▸ Tools ▸ GitInsight: License. 1.0.x renders an informational "Pro Preview"
 *              banner and a disabled license-key field — both wired to LicenseSettings so the 1.1.x
 *              activation flow only has to enable the field, not rewire the UI.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 17:07
 **/
internal class LicenseSettingsConfigurable : Configurable {

    private val licenseKeyField = JBTextField().apply {
        isEditable = false
        toolTipText = "License activation will be available starting GitInsight 1.1.x."
    }

    override fun getDisplayName(): String = "License"

    override fun createComponent(): JComponent {
        val settings = LicenseSettings.getInstance()
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        panel.add(leftAlign(JBLabel(
            "<html><b>Current tier:</b> ${settings.tier.displayName}" +
                " &nbsp;<i>(全部功能 1.0.x 期间免费开放)</i></html>"
        )))
        panel.add(Box.createVerticalStrut(8))
        panel.add(leftAlign(JBLabel(
            "<html>GitInsight 1.0.x 处于 <b>Pro Preview</b> 期间：包括 <b>AI Diff Review</b>、" +
                "<code>.gitinsight/risk.yml</code> 项目级规则覆盖等原本计划放在 Pro 档的功能，对所有用户开放。" +
                "<br/>我们用这段时间收集真实使用数据再决定如何切分。<br/><br/>" +
                "1.1.x 起将正式区分 Free / Pro 档位，届时本面板会启用 License Key 激活。" +
                "当前订阅状态保持 Pro Preview，无需操作。</html>"
        )))
        panel.add(Box.createVerticalStrut(12))
        panel.add(leftAlign(JBLabel("License Key (1.1.x 启用):")))
        licenseKeyField.text = settings.licenseKey
        panel.add(leftAlign(licenseKeyField))
        return panel
    }

    override fun isModified(): Boolean = false  // read-only in 1.0.x
    override fun apply() { /* no-op until 1.1.x */ }

    override fun reset() {
        licenseKeyField.text = LicenseSettings.getInstance().licenseKey
    }

    private fun leftAlign(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }
}
