package com.power.gitinsight.domain.ai

/**
 * team : gitInsight.
 * Class Name: AiModel
 * Description: Provider-agnostic data types for the AI layer. No IntelliJ deps so providers can be
 *              unit-tested with HTTP fakes and the engine stays portable across IDEs.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/

internal enum class AiRole { SYSTEM, USER, ASSISTANT }

internal data class AiMessage(val role: AiRole, val content: String)

internal data class AiOptions(
    val maxTokens: Int = 512,
    val temperature: Double = 0.3,
    /** Provider-specific model override; null = use provider default. */
    val model: String? = null,
    /** Hard ceiling on the whole call. Network timeout below this is also enforced. */
    val timeoutSeconds: Long = 30
)

/** Result of a provider call. Errors carry a human-readable message and the originating exception (if any). */
internal sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String, val cause: Throwable? = null) : AiResult()
}
