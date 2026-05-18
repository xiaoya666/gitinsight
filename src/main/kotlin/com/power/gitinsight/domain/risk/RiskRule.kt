package com.power.gitinsight.domain.risk

/**
 * team : gitInsight.
 * Class Name: RiskRule
 * Description: One rule of the commit risk engine. Each rule is a stateless object; evaluate(...) returns
 *              a RiskMatch when triggered or null when the commit doesn't violate it. The 8 defaults below
 *              implement the §4.3 spec table; users will be able to override deltas in T14.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 18:20
 **/
internal interface RiskRule {
    val id: String
    val defaultDelta: Int
    fun evaluate(context: DiffContext): RiskMatch?
}

/** Rule 1: payment / money paths or types. Path OR content keyword match. */
internal object PaymentTouchRule : RiskRule {
    override val id = "payment-touch"
    override val defaultDelta = 30
    private val patterns = listOf(
        Regex("(?i)pay(ment)?\\b"),
        Regex("(?i)order.*amount"),
        Regex("(?i)bigdecimal"),
        Regex("(?i)\\bwallet\\b"),
        Regex("(?i)\\binvoice\\b")
    )

    override fun evaluate(context: DiffContext): RiskMatch? {
        val hits = context.files.filter { f ->
            patterns.any { p -> p.containsMatchIn(f.filePath) || p.containsMatchIn(f.contentSnippet) }
        }
        if (hits.isEmpty()) return null
        return RiskMatch(id, defaultDelta, "修改支付 / 金额相关", hits.map { it.filePath })
    }
}

/** Rule 2: SQL files and migrations. Pure path check. */
internal object SqlMigrationRule : RiskRule {
    override val id = "sql-migration"
    override val defaultDelta = 20
    private val patterns = listOf(
        Regex("\\.sql$", RegexOption.IGNORE_CASE),
        Regex("Mapper\\.xml$", RegexOption.IGNORE_CASE),
        Regex("/migrations?/", RegexOption.IGNORE_CASE),
        Regex("/flyway/", RegexOption.IGNORE_CASE),
        Regex("/liquibase/", RegexOption.IGNORE_CASE)
    )

    override fun evaluate(context: DiffContext): RiskMatch? {
        val hits = context.files.filter { f ->
            patterns.any { it.containsMatchIn(f.filePath) }
        }
        if (hits.isEmpty()) return null
        return RiskMatch(id, defaultDelta, "修改 SQL / Migration", hits.map { it.filePath })
    }
}

/** Rule 3: concurrency primitives or transactional boundaries. Content-keyword based. */
internal object ConcurrencyRule : RiskRule {
    override val id = "concurrency-lock"
    override val defaultDelta = 20
    private val keywords = listOf(
        "synchronized", "ReentrantLock", "ReadWriteLock", "Redisson",
        "@Transactional", "CountDownLatch", "Semaphore", "AtomicReference",
        "volatile ", "CompletableFuture", "ExecutorService"
    )

    override fun evaluate(context: DiffContext): RiskMatch? {
        val hits = mutableListOf<String>()
        context.files.forEach { f ->
            val matched = keywords.filter { kw -> f.contentSnippet.contains(kw) }
            if (matched.isNotEmpty()) hits += "${f.filePath} (${matched.joinToString(", ")})"
        }
        if (hits.isEmpty()) return null
        return RiskMatch(id, defaultDelta, "修改并发 / 锁", hits)
    }
}

/** Rule 4: large deletion (>100 lines absolute, or >50% of original file). */
internal object LargeDeleteRule : RiskRule {
    override val id = "large-delete"
    override val defaultDelta = 15

    override fun evaluate(context: DiffContext): RiskMatch? {
        val hits = context.files.filter { f ->
            f.deletedLines > 100 ||
                (f.totalLinesBefore > 0 && f.deletedLines.toDouble() / f.totalLinesBefore > 0.5)
        }
        if (hits.isEmpty()) return null
        return RiskMatch(
            id, defaultDelta,
            "删除大段代码",
            hits.map { "${it.filePath} (-${it.deletedLines} lines)" }
        )
    }
}

/** Rule 5: changes spread across ≥3 distinct module buckets. */
internal object CrossModuleRule : RiskRule {
    override val id = "cross-module"
    override val defaultDelta = 15

    override fun evaluate(context: DiffContext): RiskMatch? {
        val buckets = context.files.mapNotNull { moduleBucket(it.filePath) }.toSet()
        if (buckets.size < 3) return null
        return RiskMatch(id, defaultDelta, "跨 ${buckets.size} 个模块修改", buckets.toList())
    }

    /**
     * Map a path to a "feature folder" bucket.
     *  - Take the parent directory of the file.
     *  - Strip well-known source roots (src/main/{java,kotlin,scala,resources}, src/test/{java,kotlin}).
     *  - Strip a leading `<top-level>/<owner>/` like `com/foo/` so files under `com/foo/order/...`
     *    and `com/foo/payment/...` collapse to "order" vs "payment".
     *  - Root files (no slash) and files sitting directly under a source root return null — they
     *    don't carry enough signal to be called their own module.
     */
    private fun moduleBucket(path: String): String? {
        val dir = path.substringBeforeLast('/', "")
        if (dir.isEmpty()) return null
        val withSlash = "$dir/"
        val sourceStripped = withSlash
            .removePrefix("src/main/java/")
            .removePrefix("src/main/kotlin/")
            .removePrefix("src/main/scala/")
            .removePrefix("src/main/resources/")
            .removePrefix("src/test/java/")
            .removePrefix("src/test/kotlin/")
            .removeSuffix("/")
        if (sourceStripped.isEmpty()) return null
        val orgStripped = sourceStripped.replace(Regex("^(com|org|io|net|edu)/[^/]+/"), "")
        return orgStripped.ifEmpty { null }
    }
}

/** Rule 6: non-test source files changed but no test files in the diff. */
internal object MissingTestsRule : RiskRule {
    override val id = "no-tests"
    override val defaultDelta = 20
    private val sourceExtensions = setOf(
        ".kt", ".java", ".scala", ".groovy",
        ".py", ".js", ".ts", ".tsx", ".jsx",
        ".go", ".rs", ".rb", ".cs"
    )

    override fun evaluate(context: DiffContext): RiskMatch? {
        val nonTestSources = context.files.filter { f ->
            !f.isTest && sourceExtensions.any { ext -> f.filePath.endsWith(ext) }
        }
        if (nonTestSources.isEmpty()) return null
        val anyTestTouched = context.files.any { it.isTest }
        if (anyTestTouched) return null
        return RiskMatch(
            id, defaultDelta,
            "改动包含源文件但无测试文件变更",
            nonTestSources.take(5).map { it.filePath }
        )
    }
}

/** Rule 7: CI / build / container files. */
internal object CiInfraRule : RiskRule {
    override val id = "ci-infra"
    override val defaultDelta = 10
    private val patterns = listOf(
        Regex("(?:^|/)\\.github/"),
        Regex("(?i)(?:^|/)Dockerfile"),
        Regex("\\.gradle(?:\\.kts)?$"),
        Regex("(?:^|/)pom\\.xml$"),
        Regex("(?i)(?:^|/)Jenkinsfile"),
        Regex("(?i)(?:^|/)\\.circleci/"),
        Regex("(?i)(?:^|/)\\.gitlab-ci\\.yml$")
    )

    override fun evaluate(context: DiffContext): RiskMatch? {
        val hits = context.files.filter { f ->
            patterns.any { it.containsMatchIn(f.filePath) }
        }
        if (hits.isEmpty()) return null
        return RiskMatch(id, defaultDelta, "修改 CI / 部署文件", hits.map { it.filePath })
    }
}

/** Rule 8: touches a file whose hotspot score is above 70. */
internal object HotspotTouchRule : RiskRule {
    override val id = "hotspot-touch"
    override val defaultDelta = 15
    private const val HOTSPOT_THRESHOLD = 70.0

    override fun evaluate(context: DiffContext): RiskMatch? {
        val hits = context.files.mapNotNull { f ->
            val score = context.hotspotLookup(f.filePath) ?: return@mapNotNull null
            if (score > HOTSPOT_THRESHOLD) "${f.filePath} (score=${"%.1f".format(score)})" else null
        }
        if (hits.isEmpty()) return null
        return RiskMatch(id, defaultDelta, "触碰高 Hotspot 文件", hits)
    }
}
