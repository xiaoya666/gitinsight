package com.power.gitinsight.domain.ai

/**
 * team : gitInsight.
 * Class Name: AiProvider
 * Description: Stable contract for an AI completion backend. Implementations are stateless and MUST NOT
 *              be called from the EDT — every concrete provider does blocking HTTP I/O. Errors are
 *              returned as AiResult.Error rather than thrown so callers can branch without try/catch.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal interface AiProvider {
    /** Stable id used by AiSettings to remember the user's selection (e.g. "openai", "claude"). */
    val id: String

    /** Human-readable name for the settings dropdown. */
    val displayName: String

    /** Whether this provider needs the user to supply an API key. */
    val requiresApiKey: Boolean

    /**
     * Run a chat-style completion. Implementations should respect [opts.timeoutSeconds] for the network
     * call and pass [opts.maxTokens] / [opts.temperature] / [opts.model] through to their backend.
     *
     * NEVER call from the EDT.
     */
    fun complete(messages: List<AiMessage>, opts: AiOptions = AiOptions()): AiResult
}
