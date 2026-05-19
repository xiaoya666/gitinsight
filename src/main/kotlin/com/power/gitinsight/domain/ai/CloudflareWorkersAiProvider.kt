package com.power.gitinsight.domain.ai

/**
 * team : gitInsight.
 * Class Name: CloudflareWorkersAiProvider
 * Description: BYO-key-free fallback that calls a GitInsight-operated Cloudflare Worker which forwards
 *              the chat-style request to Workers AI (default `@cf/qwen/qwen1.5-7b-chat-awq`). The Worker
 *              owns the CF account credentials so users can try the AI features without configuring
 *              anything. Rate limits live on the Worker side, not here.
 *
 *              Default URL is a placeholder until the Worker is deployed; it's configurable in AiSettings.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class CloudflareWorkersAiProvider(
    private val workerUrl: String = DEFAULT_WORKER_URL,
    private val defaultModel: String = DEFAULT_MODEL
) : AiProvider {

    override val id: String = "cf-workers-ai"
    override val displayName: String = "Workers AI (free fallback)"
    override val requiresApiKey: Boolean = false

    override fun complete(messages: List<AiMessage>, opts: AiOptions): AiResult {
        val body = buildRequestBody(messages, opts)
        return try {
            val responseBody = HttpJsonClient.postJson(
                url = workerUrl,
                body = body,
                headers = emptyMap(),
                timeoutSeconds = opts.timeoutSeconds
            )
            val text = Json.extractFirstStringField(responseBody, "response")
                ?: return AiResult.Error("$displayName 返回的响应缺少 response 字段")
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
        // Placeholder until the GitInsight Worker is deployed; AiSettings exposes an override field.
        const val DEFAULT_WORKER_URL = "https://gitinsight-ai.pages.dev/chat"
        // Per spec §6 — Qwen 1.5 7B AWQ is on the CF free tier.
        const val DEFAULT_MODEL = "@cf/qwen/qwen1.5-7b-chat-awq"
    }
}
