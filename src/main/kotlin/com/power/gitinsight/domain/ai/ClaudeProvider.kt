package com.power.gitinsight.domain.ai

/**
 * team : gitInsight.
 * Class Name: ClaudeProvider
 * Description: Anthropic Messages API. Hoists a leading SYSTEM message into the top-level `system` field
 *              the API expects, leaves USER / ASSISTANT messages in the `messages` array. Default model
 *              is Haiku 4.5 — cheap enough for commit-message generation and fast enough for the dialog.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class ClaudeProvider(
    private val apiKey: () -> String?,
    private val defaultModel: String = DEFAULT_MODEL,
    private val baseUrl: String = "https://api.anthropic.com"
) : AiProvider {

    override val id: String = "claude"
    override val displayName: String = "Anthropic Claude"
    override val requiresApiKey: Boolean = true

    override fun complete(messages: List<AiMessage>, opts: AiOptions): AiResult {
        val key = apiKey()
        if (key.isNullOrBlank()) {
            return AiResult.Error("$displayName 需要 API key — 请在 Preferences > Tools > GitInsight: AI 中配置")
        }

        val body = buildRequestBody(messages, opts)
        val headers = mapOf(
            "x-api-key" to key,
            "anthropic-version" to "2023-06-01"
        )

        return try {
            val responseBody = HttpJsonClient.postJson(
                url = "${baseUrl.trimEnd('/')}/v1/messages",
                body = body,
                headers = headers,
                timeoutSeconds = opts.timeoutSeconds
            )
            val text = Json.extractFirstStringField(responseBody, "text")
                ?: return AiResult.Error("$displayName 返回的响应缺少 text 字段")
            AiResult.Success(text.trim())
        } catch (e: Exception) {
            AiResult.Error("$displayName 调用失败: ${e.message}", e)
        }
    }

    private fun buildRequestBody(messages: List<AiMessage>, opts: AiOptions): String {
        val model = opts.model ?: defaultModel
        val systemMessage = messages.firstOrNull { it.role == AiRole.SYSTEM }
        val turns = messages.filter { it.role != AiRole.SYSTEM }

        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"model\":").append(Json.escape(model)).append(',')
        sb.append("\"max_tokens\":").append(opts.maxTokens).append(',')
        sb.append("\"temperature\":").append(opts.temperature)
        if (systemMessage != null) {
            sb.append(',').append("\"system\":").append(Json.escape(systemMessage.content))
        }
        sb.append(',').append("\"messages\":[")
        turns.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            // Claude only accepts "user" or "assistant" in messages[].role
            val role = when (m.role) {
                AiRole.ASSISTANT -> "assistant"
                else -> "user"
            }
            sb.append("{\"role\":").append(Json.escape(role))
                .append(",\"content\":").append(Json.escape(m.content)).append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    companion object {
        // Knowledge cutoff Jan 2026 — Haiku 4.5 is current and cheap enough for commit-message work.
        private const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    }
}
