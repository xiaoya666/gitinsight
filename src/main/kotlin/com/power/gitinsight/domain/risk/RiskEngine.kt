package com.power.gitinsight.domain.risk

/**
 * team : gitInsight.
 * Class Name: RiskEngine
 * Description: Aggregates rule outcomes into a single RiskReport. Pure function; injectable rule list so
 *              tests can isolate one rule at a time and T14 settings can pass a user-trimmed list.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 18:20
 **/
internal object RiskEngine {

    /** The eight v1 rules from spec §4.3, in declaration order. */
    val DEFAULT_RULES: List<RiskRule> = listOf(
        PaymentTouchRule,
        SqlMigrationRule,
        ConcurrencyRule,
        LargeDeleteRule,
        CrossModuleRule,
        MissingTestsRule,
        CiInfraRule,
        HotspotTouchRule
    )

    /** Score band thresholds. HIGH >= 70, MEDIUM 40-69, LOW < 40. */
    private const val HIGH_THRESHOLD = 70
    private const val MEDIUM_THRESHOLD = 40
    private const val SCORE_CEILING = 100

    fun evaluate(context: DiffContext, rules: List<RiskRule> = DEFAULT_RULES): RiskReport {
        val matches = rules.mapNotNull { it.evaluate(context) }
        val total = matches.sumOf { it.scoreDelta }.coerceIn(0, SCORE_CEILING)
        val level = when {
            total >= HIGH_THRESHOLD -> RiskLevel.HIGH
            total >= MEDIUM_THRESHOLD -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        return RiskReport(
            totalScore = total,
            level = level,
            matches = matches,
            suggestions = buildSuggestions(matches)
        )
    }

    /** Surface practical next steps tied to whichever rules fired. */
    private fun buildSuggestions(matches: List<RiskMatch>): List<String> {
        val triggeredIds = matches.mapTo(HashSet()) { it.ruleId }
        val out = mutableListOf<String>()
        if ("no-tests" in triggeredIds) out += "补充单元测试再提交"
        if ("hotspot-touch" in triggeredIds) out += "邀请该热点文件近期作者评审"
        if ("payment-touch" in triggeredIds) out += "金额逻辑请求双人 review，加打印 / 审计日志"
        if ("sql-migration" in triggeredIds) out += "DDL 变更确认是否需要 staging 验证 / 回滚脚本"
        if ("concurrency-lock" in triggeredIds) out += "并发改动建议跑压测 + 死锁检查"
        if ("large-delete" in triggeredIds) out += "确认大段删除背后已有备份 / 不破坏调用方"
        if ("cross-module" in triggeredIds) out += "跨模块改动建议拆成多个 PR"
        if ("ci-infra" in triggeredIds) out += "CI / 部署变更先在 feature 分支验证"
        return out
    }
}
