package com.power.gitinsight.domain.risk

/**
 * team : gitInsight.
 * Class Name: RiskRulesYamlParser
 * Description: Tiny YAML reader for the restricted .gitinsight/risk.yml shape. We do NOT depend on
 *              SnakeYAML to keep the plugin jar small — the file format is a closed grammar (top-level
 *              `rules:` followed by `<id>:` blocks with `enabled:` and `delta:` keys) so a line-based
 *              parser is enough. Unrecognised lines are silently ignored so a user typo never blocks
 *              their commit; the engine falls back to defaults for those rules.
 *
 *              Accepted shape:
 *                rules:
 *                  payment-touch:
 *                    enabled: false
 *                  hotspot-touch:
 *                    delta: 25
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
internal object RiskRulesYamlParser {

    private val RULE_HEADER = Regex("^([ \\t]+)([a-z][a-z0-9_-]*):\\s*$")
    private val PROP_LINE = Regex("^([ \\t]+)(enabled|delta):\\s*(\\S+)\\s*$")

    fun parse(text: String): RuleOverrides {
        val disabled = mutableSetOf<String>()
        val deltas = mutableMapOf<String, Int>()

        var inRulesSection = false
        var currentRule: String? = null
        var ruleIndent: Int = -1

        for (raw in text.lines()) {
            // Strip trailing comments first, then trailing whitespace. Leading whitespace matters (indent).
            val noComment = raw.substringBefore('#').trimEnd()
            if (noComment.isBlank()) continue

            if (!startsWithIndent(noComment)) {
                inRulesSection = noComment.startsWith("rules:")
                currentRule = null
                ruleIndent = -1
                continue
            }
            if (!inRulesSection) continue

            val indent = noComment.takeWhile { it == ' ' || it == '\t' }.length

            // Rule header? -> set as current rule and remember its indent so we know what counts as a child.
            RULE_HEADER.matchEntire(noComment)?.let { m ->
                currentRule = m.groupValues[2]
                ruleIndent = indent
                return@let
            } ?: PROP_LINE.matchEntire(noComment)?.let { m ->
                val rule = currentRule ?: return@let
                // Children must be deeper than the rule header to belong to it.
                if (ruleIndent < 0 || indent <= ruleIndent) return@let
                val key = m.groupValues[2]
                val value = m.groupValues[3]
                applyProperty(rule, key, value, disabled, deltas)
            }
            // anything else is ignored on purpose
        }

        return RuleOverrides(disabledIds = disabled.toSet(), deltaOverrides = deltas.toMap())
    }

    private fun startsWithIndent(line: String): Boolean {
        val first = line.firstOrNull() ?: return false
        return first == ' ' || first == '\t'
    }

    private fun applyProperty(
        ruleId: String,
        key: String,
        value: String,
        disabled: MutableSet<String>,
        deltas: MutableMap<String, Int>
    ) {
        when (key) {
            "enabled" -> when (value.lowercase()) {
                "false", "no", "off" -> disabled.add(ruleId)
                "true", "yes", "on" -> disabled.remove(ruleId)
                else -> Unit // typo — ignore
            }
            "delta" -> value.toIntOrNull()?.let { delta ->
                // Clamp to a sensible range so users can't accidentally over-weight a single rule
                // beyond the engine's 100-point ceiling.
                deltas[ruleId] = delta.coerceIn(0, 100)
            }
        }
    }
}
