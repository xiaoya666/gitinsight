package com.power.gitinsight.domain.risk

/**
 * team : gitInsight.
 * Class Name: RiskModel
 * Description: Pure data types for the commit risk engine. No IntelliJ dependencies so the engine and its rules
 *              stay unit-testable. The IntelliJ adapter (T13) builds DiffContext from CheckinProjectPanel and
 *              passes it to RiskEngine.evaluate(...).
 *
 * @author: power
 * on Date: 2026/05/18 Time: 18:20
 **/

/** One file being committed, in the form the engine needs. */
internal data class FileDiff(
    val filePath: String,
    val addedLines: Int = 0,
    val deletedLines: Int = 0,
    /** Total line count of the file BEFORE this commit. Used by the "delete > 50%" rule. */
    val totalLinesBefore: Int = 0,
    /** Hunks concatenated as a single string (added + context lines). Used by content-keyword rules. */
    val contentSnippet: String = "",
    val isTest: Boolean = looksLikeTestPath(filePath)
)

/**
 * What the engine sees. hotspotLookup decouples the engine from HotspotService so tests can supply
 * a fake. Returns null when the file has no cached hotspot record.
 */
internal data class DiffContext(
    val files: List<FileDiff>,
    val hotspotLookup: (filePath: String) -> Double? = { null }
)

internal enum class RiskLevel { LOW, MEDIUM, HIGH }

/** One rule's contribution to the report. evidence holds matched paths or keywords for the UI. */
internal data class RiskMatch(
    val ruleId: String,
    val scoreDelta: Int,
    val message: String,
    val evidence: List<String> = emptyList()
)

internal data class RiskReport(
    /** Already clamped to [0, 100]. */
    val totalScore: Int,
    val level: RiskLevel,
    val matches: List<RiskMatch>,
    val suggestions: List<String>
)

/**
 * Heuristic for "is this file a test?". Path-based only — we deliberately avoid PSI here so the
 * engine remains a pure function. Treats common conventions across JVM, JS/TS, Python, and Go.
 */
internal fun looksLikeTestPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.contains("/test/") ||
        lower.contains("/tests/") ||
        lower.contains("/spec/") ||
        lower.contains("/__tests__/") ||
        lower.endsWith("test.kt") ||
        lower.endsWith("test.java") ||
        lower.endsWith("tests.kt") ||
        lower.endsWith("tests.java") ||
        lower.endsWith("spec.kt") ||
        lower.endsWith("spec.ts") ||
        lower.endsWith("spec.js") ||
        lower.endsWith(".test.ts") ||
        lower.endsWith(".test.js") ||
        lower.endsWith(".test.tsx") ||
        lower.endsWith("_test.go") ||
        lower.endsWith("_test.py")
}
