package com.power.gitinsight.ui.license

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.license.LicenseSettings
import java.awt.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: LicenseSettingsConfigurable
 * Description: Settings ▸ Tools ▸ GitInsight: License. Paste a license key and Apply to activate it
 *              offline (LicenseVerifier checks the Ed25519 signature + expiry against the embedded
 *              public key); clearing the field and Apply deactivates. Status shows the resolved tier
 *              and expiry. During the 1.0.x Pro Preview every feature is free regardless, so activating
 *              only changes the displayed tier — but the flow is the real one shipped for the 1.1.x Pro
 *              launch (when PREVIEW_WINDOW_OPEN flips and FREE users start hitting the gates).
 *
 * @author: power
 * on Date: 2026/05/27 Time: 17:07
 **/
internal class LicenseSettingsConfigurable : Configurable {

    private val licenseKeyField = JBTextField(40)
    private val statusLabel = JBLabel()

    override fun getDisplayName(): String = "License"

    override fun createComponent(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        panel.add(leftAlign(JBLabel(
            "<html>粘贴你的 License Key 后点击 <b>Apply</b> 激活。" +
                "购买后会通过邮件收到 Key；清空输入框再 Apply 可注销当前授权。<br/>" +
                "1.0.x 处于 Pro Preview，全部功能免费开放，激活仅改变显示档位。</html>"
        )))
        panel.add(Box.createVerticalStrut(12))
        panel.add(leftAlign(JBLabel("License Key:")))
        panel.add(leftAlign(licenseKeyField))
        panel.add(Box.createVerticalStrut(8))
        panel.add(leftAlign(statusLabel))
        reset()
        return panel
    }

    override fun isModified(): Boolean =
        licenseKeyField.text.trim() != LicenseSettings.getInstance().licenseKey

    override fun apply() {
        val settings = LicenseSettings.getInstance()
        val key = licenseKeyField.text.trim()
        if (key.isEmpty()) {
            settings.deactivate()
            refreshStatus()
            return
        }
        val result = settings.activate(key)
        refreshStatus()
        if (!result.valid) {
            throw ConfigurationException(reasonMessage(result.reason), "License 激活失败")
        }
    }

    override fun reset() {
        licenseKeyField.text = LicenseSettings.getInstance().licenseKey
        refreshStatus()
    }

    private fun refreshStatus() {
        val settings = LicenseSettings.getInstance()
        statusLabel.text = "<html><b>当前档位:</b> ${settings.tier.displayName}" +
            " &nbsp; <b>有效期:</b> ${formatExpiry(settings.expiresAt)}</html>"
    }

    private fun formatExpiry(epochSeconds: Long): String =
        if (epochSeconds <= 0L) {
            "永久"
        } else {
            Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }

    private fun reasonMessage(reason: String): String = when (reason) {
        "empty" -> "License Key 不能为空。"
        "malformed" -> "License Key 格式不正确。"
        "bad-signature" -> "License Key 签名校验失败（可能被篡改，或不是本产品的 Key）。"
        "verify-error" -> "无法验证 License Key，请稍后重试。"
        "bad-payload" -> "License Key 内容无法解析。"
        "expired" -> "License Key 已过期。"
        else -> "License Key 无效（$reason）。"
    }

    private fun leftAlign(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }
}
