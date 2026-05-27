package com.power.gitinsight.domain.risk

/**
 * team : gitInsight.
 * Class Name: RiskRulesOverride
 * Description: Project-level override knobs for the commit risk engine. Loaded from .gitinsight/risk.yml by
 *              RiskRulesProjectLoader and merged on top of the app-level RiskSettings (which only tracks
 *              disabled ids). Two override channels: turn a rule off, and change the score delta a rule
 *              contributes when it fires.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
internal data class RuleOverrides(
    val disabledIds: Set<String> = emptySet(),
    val deltaOverrides: Map<String, Int> = emptyMap()
) {
    companion object {
        val EMPTY = RuleOverrides()
    }
}

/**
 * Decorator that swaps the delta returned by the wrapped rule. The wrapped rule's matching logic is
 * unchanged; only the contributed score is replaced. evaluate() still returns null when the base rule
 * declines, so disabling and delta-override compose cleanly.
 */
internal class OverriddenRule(
    private val base: RiskRule,
    private val newDelta: Int
) : RiskRule {
    override val id: String = base.id
    override val defaultDelta: Int = newDelta

    override fun evaluate(context: DiffContext): RiskMatch? {
        val match = base.evaluate(context) ?: return null
        return match.copy(scoreDelta = newDelta)
    }
}

/**
 * Combine the canonical 8 rules with project-level overrides to produce the rules the engine should run.
 *  - Drop disabled ones.
 *  - Wrap survivors that have a delta override.
 */
internal fun applyOverrides(rules: List<RiskRule>, overrides: RuleOverrides): List<RiskRule> {
    if (overrides == RuleOverrides.EMPTY) return rules
    return rules.mapNotNull { rule ->
        if (rule.id in overrides.disabledIds) return@mapNotNull null
        val newDelta = overrides.deltaOverrides[rule.id]
        if (newDelta != null) OverriddenRule(rule, newDelta) else rule
    }
}
