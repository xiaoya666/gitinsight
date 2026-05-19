package com.power.gitinsight.domain.ai

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * team : gitInsight.
 * Class Name: AiSettings
 * Description: App-level persisted AI configuration: selected provider id, per-provider baseUrl + model
 *              overrides. API keys are NEVER stored in plain XML — they go through IDE PasswordSafe so
 *              they can land in the OS keychain when available and stay out of synced settings.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
@State(
    name = "GitInsightAiSettings",
    storages = [Storage("gitInsightAi.xml")]
)
@Service(Service.Level.APP)
internal class AiSettings : PersistentStateComponent<AiSettings.State> {

    /** Non-sensitive config. API keys live in PasswordSafe, not here. */
    data class State(
        // Default to Workers AI so the plugin works out-of-the-box without any user config.
        var providerId: String = "cf-workers-ai",

        var openAiBaseUrl: String = "https://api.openai.com/v1",
        var openAiModel: String = "gpt-4o-mini",

        var deepSeekBaseUrl: String = "https://api.deepseek.com/v1",
        var deepSeekModel: String = "deepseek-chat",

        var claudeBaseUrl: String = "https://api.anthropic.com",
        var claudeModel: String = "claude-haiku-4-5-20251001",

        var ollamaBaseUrl: String = "http://localhost:11434/v1",
        var ollamaModel: String = "llama3",

        var workerUrl: String = CloudflareWorkersAiProvider.DEFAULT_WORKER_URL,
        var workerModel: String = CloudflareWorkersAiProvider.DEFAULT_MODEL
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(loaded: State) { XmlSerializerUtil.copyBean(loaded, state) }

    // --- API key (PasswordSafe) ------------------------------------------------

    fun getApiKey(providerId: String): String? =
        PasswordSafe.instance.getPassword(credentialAttrs(providerId))

    fun setApiKey(providerId: String, key: String?) {
        val attrs = credentialAttrs(providerId)
        if (key.isNullOrBlank()) {
            PasswordSafe.instance.setPassword(attrs, null)
        } else {
            PasswordSafe.instance.setPassword(attrs, key)
        }
    }

    private fun credentialAttrs(providerId: String): CredentialAttributes =
        CredentialAttributes("GitInsight:$providerId")

    // --- Provider construction -------------------------------------------------

    /** Build the currently selected provider with overrides applied. */
    fun activeProvider(): AiProvider = when (state.providerId) {
        "openai" -> OpenAiStyleProvider(
            id = "openai",
            displayName = "OpenAI",
            requiresApiKey = true,
            baseUrl = state.openAiBaseUrl,
            defaultModel = state.openAiModel,
            apiKey = { getApiKey("openai") }
        )
        "deepseek" -> OpenAiStyleProvider(
            id = "deepseek",
            displayName = "DeepSeek",
            requiresApiKey = true,
            baseUrl = state.deepSeekBaseUrl,
            defaultModel = state.deepSeekModel,
            apiKey = { getApiKey("deepseek") }
        )
        "ollama" -> OpenAiStyleProvider(
            id = "ollama",
            displayName = "Ollama (local)",
            requiresApiKey = false,
            baseUrl = state.ollamaBaseUrl,
            defaultModel = state.ollamaModel,
            apiKey = { null }
        )
        "claude" -> ClaudeProvider(
            apiKey = { getApiKey("claude") },
            defaultModel = state.claudeModel,
            baseUrl = state.claudeBaseUrl
        )
        else -> CloudflareWorkersAiProvider(
            workerUrl = state.workerUrl,
            defaultModel = state.workerModel
        )
    }

    /** Provider catalog for the settings dropdown. */
    fun catalog(): List<Pair<String, String>> = listOf(
        "cf-workers-ai" to "Workers AI (free fallback)",
        "openai" to "OpenAI",
        "claude" to "Anthropic Claude",
        "deepseek" to "DeepSeek",
        "ollama" to "Ollama (local)"
    )

    companion object {
        fun getInstance(): AiSettings =
            ApplicationManager.getApplication().getService(AiSettings::class.java)
    }
}
