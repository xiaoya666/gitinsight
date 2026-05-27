package com.power.gitinsight.ui.checkin

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.risk.RiskSettings
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: RiskSettingsConfigurable
 * Description: Preferences > Tools > GitInsight > Commit Risk. One checkbox per default rule; un-checking
 *              a rule writes its id into RiskSettings.disabledRuleIds so the engine skips it on next eval.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 00:17
 **/
internal class RiskSettingsConfigurable : Configurable {

    /** Display label for each rule; ordered to match spec §4.3 table. */
    private val displayLabels: Map<String, String> = linkedMapOf(
        "payment-touch" to "修改支付 / 金额相关 (+30)",
        "sql-migration" to "修改 SQL / Migration (+20)",
        "concurrency-lock" to "修改并发 / 锁 (+20)",
        "large-delete" to "删除大段代码 (+15)",
        "cross-module" to "跨模块修改 (+15)",
        "no-tests" to "无测试覆盖 (+20)",
        "ci-infra" to "修改 CI / 部署 (+10)",
        "hotspot-touch" to "触碰高 Hotspot 文件 (+15)"
    )

    private val checkboxes: Map<String, JBCheckBox> = displayLabels.mapValues { (_, label) -> JBCheckBox(label) }

    override fun getDisplayName(): String = "Commit Risk"

    override fun createComponent(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        panel.add(leftAlign(JBLabel("勾选启用的提交风险规则。取消勾选的规则将不会被计入分值。")))
        panel.add(Box.createVerticalStrut(8))
        checkboxes.values.forEach { cb -> panel.add(leftAlign(cb)) }
        panel.add(Box.createVerticalStrut(12))
        panel.add(leftAlign(JBLabel(
            "<html><i><b>[Pro Preview]</b> 项目级规则覆盖（1.0.x 期间免费开放，1.1.x 起需 Pro）：" +
                "在项目根目录建 <code>.gitinsight/risk.yml</code> 即可按项目覆盖" +
                "上面的开关 / 分值。格式：<br/>" +
                "<code>rules:<br/>" +
                "&nbsp;&nbsp;payment-touch:<br/>" +
                "&nbsp;&nbsp;&nbsp;&nbsp;enabled: false<br/>" +
                "&nbsp;&nbsp;hotspot-touch:<br/>" +
                "&nbsp;&nbsp;&nbsp;&nbsp;delta: 25</code></i></html>"
        )))
        reset()
        return panel
    }

    override fun isModified(): Boolean =
        currentDisabledFromUi() != RiskSettings.getInstance().disabledSnapshot()

    override fun apply() {
        RiskSettings.getInstance().replaceDisabled(currentDisabledFromUi())
    }

    override fun reset() {
        val disabled = RiskSettings.getInstance().disabledSnapshot()
        checkboxes.forEach { (id, cb) -> cb.isSelected = id !in disabled }
    }

    private fun currentDisabledFromUi(): Set<String> =
        checkboxes.filterValues { !it.isSelected }.keys

    private fun leftAlign(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }
}
