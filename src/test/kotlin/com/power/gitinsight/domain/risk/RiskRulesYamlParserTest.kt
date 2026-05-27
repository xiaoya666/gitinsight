package com.power.gitinsight.domain.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * team : gitInsight.
 * Class Name: RiskRulesYamlParserTest
 * Description: Tests the restricted-grammar YAML reader for .gitinsight/risk.yml — pins the recognised
 *              shapes, comment stripping, mixed enabled/delta blocks, and lenient handling of typos.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
class RiskRulesYamlParserTest {

    @Test fun `empty input returns no overrides`() {
        val out = RiskRulesYamlParser.parse("")
        assertEquals(RuleOverrides.EMPTY, out)
    }

    @Test fun `whitespace and comments only returns no overrides`() {
        val text = """
            # nothing real here

              # more comments
        """.trimIndent()
        assertEquals(RuleOverrides.EMPTY, RiskRulesYamlParser.parse(text))
    }

    @Test fun `single disabled rule`() {
        val text = """
            rules:
              payment-touch:
                enabled: false
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(setOf("payment-touch"), out.disabledIds)
        assertTrue(out.deltaOverrides.isEmpty())
    }

    @Test fun `single delta override`() {
        val text = """
            rules:
              hotspot-touch:
                delta: 25
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertTrue(out.disabledIds.isEmpty())
        assertEquals(mapOf("hotspot-touch" to 25), out.deltaOverrides)
    }

    @Test fun `mixed enabled and delta within one rule`() {
        val text = """
            rules:
              payment-touch:
                enabled: true
                delta: 45
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertTrue(out.disabledIds.isEmpty(), "enabled:true must not disable")
        assertEquals(45, out.deltaOverrides["payment-touch"])
    }

    @Test fun `multiple rules parsed independently`() {
        val text = """
            rules:
              payment-touch:
                enabled: false
              sql-migration:
                delta: 30
              hotspot-touch:
                delta: 25
                enabled: false
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(setOf("payment-touch", "hotspot-touch"), out.disabledIds)
        assertEquals(mapOf("sql-migration" to 30, "hotspot-touch" to 25), out.deltaOverrides)
    }

    @Test fun `comments after values are stripped`() {
        val text = """
            rules:
              payment-touch:
                enabled: false   # disable payment for this repo
                delta: 50        # ignored if disabled, but parser still records it
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(setOf("payment-touch"), out.disabledIds)
        assertEquals(50, out.deltaOverrides["payment-touch"])
    }

    @Test fun `delta is clamped to 0 to 100`() {
        val text = """
            rules:
              over-shoot:
                delta: 999
              under-shoot:
                delta: -5
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(100, out.deltaOverrides["over-shoot"])
        assertEquals(0, out.deltaOverrides["under-shoot"])
    }

    @Test fun `unknown keys are ignored without breaking surrounding rule`() {
        val text = """
            rules:
              hotspot-touch:
                fooBar: nonsense
                delta: 33
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(33, out.deltaOverrides["hotspot-touch"])
    }

    @Test fun `non-rules top-level sections are ignored`() {
        val text = """
            version: 1
            rules:
              payment-touch:
                enabled: false
            metadata:
              owner: alice
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(setOf("payment-touch"), out.disabledIds)
    }

    @Test fun `enabled accepts on off yes no`() {
        val text = """
            rules:
              a:
                enabled: off
              b:
                enabled: no
              c:
                enabled: yes
              d:
                enabled: on
        """.trimIndent()
        val out = RiskRulesYamlParser.parse(text)
        assertEquals(setOf("a", "b"), out.disabledIds)
    }
}
