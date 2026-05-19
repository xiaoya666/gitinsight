package com.power.gitinsight.ui.ai

import com.intellij.openapi.options.Configurable
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.power.gitinsight.domain.ai.AiSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * team : gitInsight.
 * Class Name: AiSettingsConfigurable
 * Description: Preferences > Tools > GitInsight: AI. Lets the user pick which provider runs, override
 *              base URL / model, and enter API keys. Keys round-trip through PasswordSafe; the rest live
 *              in AiSettings.State. Apply only writes back when isModified() returns true.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class AiSettingsConfigurable : Configurable {

    private val providerCombo = JComboBox<ProviderEntry>()

    private val openAiBaseUrl = textField()
    private val openAiModel = textField()
    private val openAiKey = passwordField()

    private val claudeBaseUrl = textField()
    private val claudeModel = textField()
    private val claudeKey = passwordField()

    private val deepSeekBaseUrl = textField()
    private val deepSeekModel = textField()
    private val deepSeekKey = passwordField()

    private val ollamaBaseUrl = textField()
    private val ollamaModel = textField()

    private val workerUrl = textField()
    private val workerModel = textField()

    override fun getDisplayName(): String = "AI"

    override fun createComponent(): JComponent {
        val settings = AiSettings.getInstance()
        providerCombo.removeAllItems()
        settings.catalog().forEach { (id, label) -> providerCombo.addItem(ProviderEntry(id, label)) }

        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        root.add(leftAlign(JBLabel("选择默认 AI 提供商，并按需填写 API key / 自定义 endpoint。Keys 存放在 IDE PasswordSafe（系统 keychain）。")))
        root.add(Box.createVerticalStrut(8))

        val providerRow = JPanel(BorderLayout()).apply {
            add(JBLabel("默认 Provider:"), BorderLayout.WEST)
            add(providerCombo, BorderLayout.CENTER)
        }
        root.add(leftAlign(providerRow))
        root.add(Box.createVerticalStrut(12))

        root.add(leftAlign(section("OpenAI", FormBuilder.createFormBuilder()
            .addLabeledComponent("Base URL", openAiBaseUrl)
            .addLabeledComponent("Model", openAiModel)
            .addLabeledComponent("API Key", openAiKey)
            .panel)))
        root.add(Box.createVerticalStrut(8))

        root.add(leftAlign(section("Anthropic Claude", FormBuilder.createFormBuilder()
            .addLabeledComponent("Base URL", claudeBaseUrl)
            .addLabeledComponent("Model", claudeModel)
            .addLabeledComponent("API Key", claudeKey)
            .panel)))
        root.add(Box.createVerticalStrut(8))

        root.add(leftAlign(section("DeepSeek", FormBuilder.createFormBuilder()
            .addLabeledComponent("Base URL", deepSeekBaseUrl)
            .addLabeledComponent("Model", deepSeekModel)
            .addLabeledComponent("API Key", deepSeekKey)
            .panel)))
        root.add(Box.createVerticalStrut(8))

        root.add(leftAlign(section("Ollama (local)", FormBuilder.createFormBuilder()
            .addLabeledComponent("Base URL", ollamaBaseUrl)
            .addLabeledComponent("Model", ollamaModel)
            .panel)))
        root.add(Box.createVerticalStrut(8))

        root.add(leftAlign(section("Workers AI (fallback)", FormBuilder.createFormBuilder()
            .addLabeledComponent("Worker URL", workerUrl)
            .addLabeledComponent("Model", workerModel)
            .panel)))

        reset()
        return root
    }

    override fun isModified(): Boolean {
        val s = AiSettings.getInstance()
        val state = s.state
        val combo = (providerCombo.selectedItem as? ProviderEntry)?.id ?: return false
        if (combo != state.providerId) return true
        if (openAiBaseUrl.text != state.openAiBaseUrl) return true
        if (openAiModel.text != state.openAiModel) return true
        if (claudeBaseUrl.text != state.claudeBaseUrl) return true
        if (claudeModel.text != state.claudeModel) return true
        if (deepSeekBaseUrl.text != state.deepSeekBaseUrl) return true
        if (deepSeekModel.text != state.deepSeekModel) return true
        if (ollamaBaseUrl.text != state.ollamaBaseUrl) return true
        if (ollamaModel.text != state.ollamaModel) return true
        if (workerUrl.text != state.workerUrl) return true
        if (workerModel.text != state.workerModel) return true
        if (pwd(openAiKey) != s.getApiKey("openai").orEmpty()) return true
        if (pwd(claudeKey) != s.getApiKey("claude").orEmpty()) return true
        if (pwd(deepSeekKey) != s.getApiKey("deepseek").orEmpty()) return true
        return false
    }

    override fun apply() {
        val s = AiSettings.getInstance()
        val state = s.state
        state.providerId = (providerCombo.selectedItem as? ProviderEntry)?.id ?: state.providerId
        state.openAiBaseUrl = openAiBaseUrl.text.trim()
        state.openAiModel = openAiModel.text.trim()
        state.claudeBaseUrl = claudeBaseUrl.text.trim()
        state.claudeModel = claudeModel.text.trim()
        state.deepSeekBaseUrl = deepSeekBaseUrl.text.trim()
        state.deepSeekModel = deepSeekModel.text.trim()
        state.ollamaBaseUrl = ollamaBaseUrl.text.trim()
        state.ollamaModel = ollamaModel.text.trim()
        state.workerUrl = workerUrl.text.trim()
        state.workerModel = workerModel.text.trim()
        s.setApiKey("openai", pwd(openAiKey).ifEmpty { null })
        s.setApiKey("claude", pwd(claudeKey).ifEmpty { null })
        s.setApiKey("deepseek", pwd(deepSeekKey).ifEmpty { null })
    }

    override fun reset() {
        val s = AiSettings.getInstance()
        val state = s.state
        for (i in 0 until providerCombo.itemCount) {
            if (providerCombo.getItemAt(i).id == state.providerId) {
                providerCombo.selectedIndex = i
                break
            }
        }
        openAiBaseUrl.text = state.openAiBaseUrl
        openAiModel.text = state.openAiModel
        claudeBaseUrl.text = state.claudeBaseUrl
        claudeModel.text = state.claudeModel
        deepSeekBaseUrl.text = state.deepSeekBaseUrl
        deepSeekModel.text = state.deepSeekModel
        ollamaBaseUrl.text = state.ollamaBaseUrl
        ollamaModel.text = state.ollamaModel
        workerUrl.text = state.workerUrl
        workerModel.text = state.workerModel
        openAiKey.text = s.getApiKey("openai").orEmpty()
        claudeKey.text = s.getApiKey("claude").orEmpty()
        deepSeekKey.text = s.getApiKey("deepseek").orEmpty()
    }

    private fun textField(): JBTextField = JBTextField().apply {
        preferredSize = Dimension(360, preferredSize.height)
    }

    private fun passwordField(): JBPasswordField = JBPasswordField().apply {
        preferredSize = Dimension(360, preferredSize.height)
    }

    private fun pwd(field: JBPasswordField): String = String(field.password)

    private fun leftAlign(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }

    private fun section(title: String, inner: JComponent): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = IdeBorderFactory.createTitledBorder(title)
        }
        panel.add(inner, BorderLayout.CENTER)
        return panel
    }

    private data class ProviderEntry(val id: String, val label: String) {
        override fun toString(): String = label
    }
}
