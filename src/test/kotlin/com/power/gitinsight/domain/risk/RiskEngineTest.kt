package com.power.gitinsight.domain.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * team : gitInsight.
 * Class Name: RiskEngineTest
 * Description: Per-rule positive + negative cases and engine-level aggregation (score clamp, level bands, suggestions).
 *
 * @author: power
 * on Date: 2026/05/18 Time: 20:53
 **/
class RiskEngineTest {

    private fun ctx(vararg files: FileDiff, hotspots: Map<String, Double> = emptyMap()): DiffContext =
        DiffContext(files.toList()) { hotspots[it] }

    private fun src(
        path: String,
        adds: Int = 0,
        dels: Int = 0,
        before: Int = 0,
        content: String = ""
    ) = FileDiff(path, adds, dels, before, content)

    // --- Rule 1: PaymentTouchRule ---------------------------------------------

    @Test fun `payment rule fires on path containing payment`() {
        val match = PaymentTouchRule.evaluate(ctx(src("src/main/java/com/foo/payment/PaymentService.java")))
        assertNotNull(match)
        assertEquals(30, match!!.scoreDelta)
    }

    @Test fun `payment rule fires on BigDecimal content keyword`() {
        val match = PaymentTouchRule.evaluate(
            ctx(src("src/main/java/com/foo/order/Order.java", content = "import java.math.BigDecimal;"))
        )
        assertNotNull(match)
    }

    @Test fun `payment rule does not fire on unrelated files`() {
        assertNull(PaymentTouchRule.evaluate(ctx(src("src/main/kotlin/com/foo/util/Utils.kt"))))
    }

    // --- Rule 2: SqlMigrationRule ---------------------------------------------

    @Test fun `sql rule fires on _sql file`() {
        val match = SqlMigrationRule.evaluate(ctx(src("db/migrations/V001__create_users.sql")))
        assertNotNull(match)
        assertEquals(20, match!!.scoreDelta)
    }

    @Test fun `sql rule fires on mapper xml`() {
        val match = SqlMigrationRule.evaluate(ctx(src("src/main/resources/mapper/UserMapper.xml")))
        assertNotNull(match)
    }

    @Test fun `sql rule does not fire on plain xml`() {
        assertNull(SqlMigrationRule.evaluate(ctx(src("config/logback.xml"))))
    }

    // --- Rule 3: ConcurrencyRule ----------------------------------------------

    @Test fun `concurrency rule fires on synchronized keyword`() {
        val match = ConcurrencyRule.evaluate(
            ctx(src("src/main/kotlin/Foo.kt", content = "synchronized(lock) { foo() }"))
        )
        assertNotNull(match)
        assertEquals(20, match!!.scoreDelta)
    }

    @Test fun `concurrency rule fires on Transactional annotation`() {
        val match = ConcurrencyRule.evaluate(
            ctx(src("Service.kt", content = "@Transactional\nfun save() {}"))
        )
        assertNotNull(match)
    }

    @Test fun `concurrency rule ignores files without lock keywords`() {
        assertNull(ConcurrencyRule.evaluate(ctx(src("Foo.kt", content = "val x = 1"))))
    }

    // --- Rule 4: LargeDeleteRule ----------------------------------------------

    @Test fun `large delete fires on absolute threshold`() {
        val match = LargeDeleteRule.evaluate(ctx(src("Foo.kt", dels = 150)))
        assertNotNull(match)
        assertEquals(15, match!!.scoreDelta)
    }

    @Test fun `large delete fires on relative threshold`() {
        val match = LargeDeleteRule.evaluate(ctx(src("Foo.kt", dels = 60, before = 100)))
        assertNotNull(match)
    }

    @Test fun `large delete does not fire on small change`() {
        assertNull(LargeDeleteRule.evaluate(ctx(src("Foo.kt", dels = 10, before = 1000))))
    }

    // --- Rule 5: CrossModuleRule ----------------------------------------------

    @Test fun `cross-module fires on three or more distinct buckets`() {
        val match = CrossModuleRule.evaluate(
            ctx(
                src("src/main/java/com/foo/order/Order.java"),
                src("src/main/java/com/foo/payment/Payment.java"),
                src("src/main/java/com/foo/user/User.java")
            )
        )
        assertNotNull(match)
        assertEquals(15, match!!.scoreDelta)
    }

    @Test fun `cross-module does not fire on two buckets`() {
        assertNull(CrossModuleRule.evaluate(
            ctx(
                src("src/main/java/com/foo/order/Order.java"),
                src("src/main/java/com/foo/payment/Payment.java")
            )
        ))
    }

    // --- Rule 6: MissingTestsRule ---------------------------------------------

    @Test fun `missing tests fires when only source changed`() {
        val match = MissingTestsRule.evaluate(ctx(src("src/main/kotlin/Foo.kt")))
        assertNotNull(match)
        assertEquals(20, match!!.scoreDelta)
    }

    @Test fun `missing tests does not fire when tests included`() {
        assertNull(MissingTestsRule.evaluate(
            ctx(
                src("src/main/kotlin/Foo.kt"),
                src("src/test/kotlin/FooTest.kt")
            )
        ))
    }

    @Test fun `missing tests does not fire on docs-only commits`() {
        assertNull(MissingTestsRule.evaluate(ctx(src("README.md"))))
    }

    // --- Rule 7: CiInfraRule --------------------------------------------------

    @Test fun `ci rule fires on github workflows`() {
        val match = CiInfraRule.evaluate(ctx(src(".github/workflows/ci.yml")))
        assertNotNull(match)
        assertEquals(10, match!!.scoreDelta)
    }

    @Test fun `ci rule fires on Dockerfile and gradle`() {
        assertNotNull(CiInfraRule.evaluate(ctx(src("Dockerfile"))))
        assertNotNull(CiInfraRule.evaluate(ctx(src("build.gradle.kts"))))
    }

    @Test fun `ci rule does not fire on unrelated yml`() {
        assertNull(CiInfraRule.evaluate(ctx(src("src/main/resources/application.yml"))))
    }

    // --- Rule 8: HotspotTouchRule ---------------------------------------------

    @Test fun `hotspot touch fires above threshold`() {
        val match = HotspotTouchRule.evaluate(
            ctx(src("hot/Burner.kt"), hotspots = mapOf("hot/Burner.kt" to 85.0))
        )
        assertNotNull(match)
        assertEquals(15, match!!.scoreDelta)
    }

    @Test fun `hotspot touch does not fire at or below threshold`() {
        assertNull(HotspotTouchRule.evaluate(
            ctx(src("warm/Foo.kt"), hotspots = mapOf("warm/Foo.kt" to 70.0))
        ))
    }

    @Test fun `hotspot touch ignores files without cached score`() {
        assertNull(HotspotTouchRule.evaluate(ctx(src("cold/Foo.kt"))))
    }

    // --- Engine-level integration ---------------------------------------------

    @Test fun `clean commit yields LOW level and zero score`() {
        val report = RiskEngine.evaluate(ctx(
            src("docs/README.md"),
            src("docs/CHANGELOG.md")
        ))
        assertEquals(0, report.totalScore)
        assertEquals(RiskLevel.LOW, report.level)
        assertTrue(report.matches.isEmpty())
        assertTrue(report.suggestions.isEmpty())
    }

    @Test fun `score clamps at 100 when many rules fire`() {
        val report = RiskEngine.evaluate(ctx(
            src("src/main/java/com/foo/payment/Payment.java"),
            src("db/migrations/V001.sql"),
            src("src/main/kotlin/Lock.kt", content = "synchronized(this) {}"),
            src("src/main/kotlin/Other.kt", dels = 200, before = 300),
            src(".github/workflows/ci.yml"),
            hotspots = mapOf("src/main/java/com/foo/payment/Payment.java" to 90.0)
        ))
        assertEquals(100, report.totalScore)
        assertEquals(RiskLevel.HIGH, report.level)
    }

    @Test fun `medium band falls between 40 and 70`() {
        val report = RiskEngine.evaluate(ctx(
            src("db/migrations/V01.sql"),       // sql +20
            src(".github/workflows/ci.yml"),    // ci  +10
            src("src/main/kotlin/Foo.kt")       // no-tests +20
        ))
        assertEquals(50, report.totalScore)
        assertEquals(RiskLevel.MEDIUM, report.level)
    }

    @Test fun `score 70 is HIGH band`() {
        // payment(30) + sql(20) + no-tests(20) = 70
        val report = RiskEngine.evaluate(ctx(
            src("src/main/java/com/foo/payment/PaymentService.java"),
            src("db/migrations/V01.sql")
        ))
        assertEquals(70, report.totalScore)
        assertEquals(RiskLevel.HIGH, report.level)
    }

    @Test fun `score 40 is MEDIUM band`() {
        // payment-doc(30) + ci(10) = 40, payment file is non-source so no-tests skipped
        val report = RiskEngine.evaluate(ctx(
            src("docs/payment-guide.md"),
            src("Dockerfile")
        ))
        assertEquals(40, report.totalScore)
        assertEquals(RiskLevel.MEDIUM, report.level)
    }

    @Test fun `suggestions are keyed off triggered rules`() {
        val report = RiskEngine.evaluate(ctx(
            src("src/main/java/com/foo/Foo.java"),
            hotspots = mapOf("src/main/java/com/foo/Foo.java" to 90.0)
        ))
        assertTrue(report.suggestions.any { it.contains("单元测试") })
        assertTrue(report.suggestions.any { it.contains("热点") })
    }

    @Test fun `evaluate respects custom rule subset`() {
        val report = RiskEngine.evaluate(
            ctx(src("src/main/kotlin/Foo.kt")),
            rules = listOf(SqlMigrationRule)
        )
        assertEquals(0, report.totalScore)
        assertEquals(RiskLevel.LOW, report.level)
        assertTrue(report.matches.isEmpty())
    }
}
