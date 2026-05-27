package com.power.gitinsight.domain.ai

/**
 * team : gitInsight.
 * Class Name: OpenAiStyleProvider
 * Description: One implementation that covers every OpenAI-compatible chat-completions API — OpenAI itself,
 *              DeepSeek, and Ollama's OpenAI-compat endpoint. Parameterized at construction so AiSettings
 *              can swap baseUrl / default model / auth requirement without subclassing.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class OpenAiStyleProvider(
    override val id: String,
    override val displayName: String,
    override val requiresApiKey: Boolean,
    private val baseUrl: String,
    private val defaultModel: String,
    /** Returns the user-configured key, or null if absent / not required. */
    private val apiKey: () -> String?
) : AiProvider {

    override fun complete(messages: List<AiMessage>, opts: AiOptions): AiResult {
        val key = apiKey()
        if (requiresApiKey && key.isNullOrBlank()) {
            return AiResult.Error("$displayName 需要 API key — 请在 Preferences > Tools > GitInsight: AI 中配置")
        }

        val body = buildRequestBody(messages, opts)
        val headers = if (key.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $key")

        return try {
            val responseBody = HttpJsonClient.postJson(
                url = "${baseUrl.trimEnd('/')}/chat/completions",
                body = body,
                headers = headers,
                timeoutSeconds = opts.timeoutSeconds
            )
            val text = Json.extractFirstStringField(responseBody, "content")
                ?: return AiResult.Error("$displayName 返回的响应缺少 content 字段")
            AiResult.Success(text.trim())
        } catch (e: Exception) {
            AiResult.Error("$displayName 调用失败: ${e.message}", e)
        }
    }

    private fun buildRequestBody(messages: List<AiMessage>, opts: AiOptions): String {
        val model = opts.model ?: defaultModel
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"model\":").append(Json.escape(model)).append(',')
        sb.append("\"messages\":[")
        messages.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            sb.append("{\"role\":").append(Json.escape(m.role.name.lowercase()))
                .append(",\"content\":").append(Json.escape(m.content)).append('}')
        }
        sb.append("],")
        sb.append("\"max_tokens\":").append(opts.maxTokens).append(',')
        sb.append("\"temperature\":").append(opts.temperature)
        sb.append('}')
        return sb.toString()
    }

    companion object {
        fun openAi(apiKeySupplier: () -> String?) = OpenAiStyleProvider(
            id = "openai",
            displayName = "OpenAI",
            requiresApiKey = true,
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            apiKey = apiKeySupplier
        )

        fun deepSeek(apiKeySupplier: () -> String?) = OpenAiStyleProvider(
            id = "deepseek",
            displayName = "DeepSeek",
            requiresApiKey = true,
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            apiKey = apiKeySupplier
        )

        fun ollama(baseUrl: String = "http://localhost:11434/v1", model: String = "llama3") =
            OpenAiStyleProvider(
                id = "ollama",
                displayName = "Ollama (local)",
                requiresApiKey = false,
                baseUrl = baseUrl,
                defaultModel = model,
                apiKey = { null }
            )
    }
}
