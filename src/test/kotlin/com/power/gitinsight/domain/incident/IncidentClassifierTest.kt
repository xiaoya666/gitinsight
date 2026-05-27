package com.power.gitinsight.domain.incident

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * team : gitInsight.
 * Class Name: IncidentClassifierTest
 * Description: TDD harness for IncidentClassifier; locks the exact subject patterns we count as incident commits so future regex tweaks don't drift silently.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 11:51
 **/
class IncidentClassifierTest {

    @Test fun `git auto-revert subject is classified as REVERT`() {
        assertEquals(IncidentReason.REVERT, IncidentClassifier.classify("Revert \"feat: add payment\""))
    }

    @Test fun `revert with colon prefix is REVERT`() {
        assertEquals(IncidentReason.REVERT, IncidentClassifier.classify("Revert: broken migration"))
    }

    @Test fun `conventional revert with scope is REVERT`() {
        assertEquals(IncidentReason.REVERT, IncidentClassifier.classify("revert(payment): rollback rounding fix"))
    }

    @Test fun `hotfix conventional commit is HOTFIX`() {
        assertEquals(IncidentReason.HOTFIX, IncidentClassifier.classify("hotfix: NPE in checkout"))
    }

    @Test fun `hotfix scoped is HOTFIX`() {
        assertEquals(IncidentReason.HOTFIX, IncidentClassifier.classify("hotfix(order): missing index"))
    }

    @Test fun `hot-fix variant is HOTFIX`() {
        assertEquals(IncidentReason.HOTFIX, IncidentClassifier.classify("hot-fix: prod 500s"))
    }

    @Test fun `rollback prefix is ROLLBACK`() {
        assertEquals(IncidentReason.ROLLBACK, IncidentClassifier.classify("rollback: switch payment provider back"))
    }

    @Test fun `chinese rollback subject is ROLLBACK`() {
        assertEquals(IncidentReason.ROLLBACK, IncidentClassifier.classify("回滚 支付服务变更"))
    }

    @Test fun `subject merely containing hotfix word is not HOTFIX`() {
        assertNull(IncidentClassifier.classify("feat: rewrite the hotfix guide"))
    }

    @Test fun `subject merely containing revert word is not REVERT`() {
        assertNull(IncidentClassifier.classify("docs: explain how to revert in CI"))
    }

    @Test fun `ordinary feat commit is not an incident`() {
        assertNull(IncidentClassifier.classify("feat(login): support SSO"))
    }

    @Test fun `empty or blank subject is not an incident`() {
        assertNull(IncidentClassifier.classify(""))
        assertNull(IncidentClassifier.classify("   "))
    }

    @Test fun `case-insensitive Hotfix prefix is HOTFIX`() {
        assertEquals(IncidentReason.HOTFIX, IncidentClassifier.classify("Hotfix: queue stuck"))
    }
}
