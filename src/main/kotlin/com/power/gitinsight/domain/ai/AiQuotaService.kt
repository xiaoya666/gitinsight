package com.power.gitinsight.domain.ai

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.power.gitinsight.domain.license.LicenseSettings
import java.time.LocalDate

/**
 * team : gitInsight.
 * Class Name: AiQuotaService
 * Description: Local daily quota for the free Cloudflare Workers AI fallback (plan §11.3 C-b: 30/day).
 *              Only the bundled fallback is metered — users on their own OpenAI/Claude/DeepSeek/Ollama key
 *              are unlimited and never counted here. Pro / Preview tiers bypass the meter entirely. Counting
 *              is intentionally local (not server-side): the fallback is cheap, so we only deter casual
 *              overuse, not determined resets. The day rolls over on the first call of a new local date.
 *
 * @author: power
 * on Date: 2026/06/10 Time: 17:43
 **/
@Service(Service.Level.APP)
@State(name = "GitInsightAiQuota", storages = [Storage("gitInsightAiQuota.xml")])
internal class AiQuotaService : PersistentStateComponent<AiQuotaService.QuotaState> {

    class QuotaState {
        var date: String = ""      // yyyy-MM-dd of the counted day
        var usedCount: Int = 0
    }

    private var state = QuotaState()

    override fun getState(): QuotaState = state
    override fun loadState(loaded: QuotaState) { XmlSerializerUtil.copyBean(loaded, state) }

    /** True when a free user has already used today's fallback budget. Pro/Preview never exhaust. */
    @Synchronized
    fun fallbackQuotaExceeded(): Boolean {
        if (LicenseSettings.getInstance().unlocksProFeatures()) return false
        rolloverIfNewDay()
        return state.usedCount >= FREE_DAILY_LIMIT
    }

    /** Record one successful fallback generation. No-op for Pro/Preview (they aren't metered). */
    @Synchronized
    fun recordFallbackUse() {
        if (LicenseSettings.getInstance().unlocksProFeatures()) return
        rolloverIfNewDay()
        state.usedCount++
    }

    /** Remaining free fallback calls today (clamped at 0). Returns the full limit for Pro/Preview. */
    @Synchronized
    fun remainingToday(): Int {
        if (LicenseSettings.getInstance().unlocksProFeatures()) return FREE_DAILY_LIMIT
        rolloverIfNewDay()
        return (FREE_DAILY_LIMIT - state.usedCount).coerceAtLeast(0)
    }

    private fun rolloverIfNewDay() {
        val today = LocalDate.now().toString()
        if (state.date != today) {
            state.date = today
            state.usedCount = 0
        }
    }

    companion object {
        const val FREE_DAILY_LIMIT = 30

        fun getInstance(): AiQuotaService =
            ApplicationManager.getApplication().getService(AiQuotaService::class.java)
    }
}
