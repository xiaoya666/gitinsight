package com.power.gitinsight.domain.risk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * team : gitInsight.
 * Class Name: RiskSettings
 * Description: App-level persisted state for the commit risk engine. Stores only the set of rule ids the
 *              user has DISABLED — empty default keeps all 8 rules active out of the box.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 00:17
 **/
@State(
    name = "GitInsightRiskSettings",
    storages = [Storage("gitInsightRiskSettings.xml")]
)
@Service(Service.Level.APP)
internal class RiskSettings : PersistentStateComponent<RiskSettings.State> {

    data class State(
        var disabledRuleIds: MutableSet<String> = mutableSetOf()
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    fun isRuleEnabled(ruleId: String): Boolean = ruleId !in state.disabledRuleIds

    /** Snapshot copy used by the Configurable so live edits don't mutate state before Apply. */
    fun disabledSnapshot(): Set<String> = state.disabledRuleIds.toSet()

    fun replaceDisabled(newSet: Set<String>) {
        state.disabledRuleIds.clear()
        state.disabledRuleIds.addAll(newSet)
    }

    /** Filter DEFAULT_RULES down to whatever the user has left enabled. */
    fun activeRules(): List<RiskRule> = RiskEngine.DEFAULT_RULES.filter { isRuleEnabled(it.id) }

    companion object {
        fun getInstance(): RiskSettings =
            ApplicationManager.getApplication().getService(RiskSettings::class.java)
    }
}
