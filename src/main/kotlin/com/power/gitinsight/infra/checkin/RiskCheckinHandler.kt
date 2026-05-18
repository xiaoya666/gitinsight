package com.power.gitinsight.infra.checkin

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.power.gitinsight.domain.risk.RiskEngine
import com.power.gitinsight.domain.risk.RiskLevel
import com.power.gitinsight.domain.risk.RiskReport
import com.power.gitinsight.ui.checkin.RiskDialog

/**
 * team : gitInsight.
 * Class Name: RiskCheckinHandler
 * Description: Builds a DiffContext for the current commit on a background progress task, runs RiskEngine,
 *              and shows the RiskDialog when the result clears the LOW threshold. Returns CANCEL only if
 *              the user explicitly aborts; fails open (COMMIT) on any internal error so tooling never
 *              blocks a real commit.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 18:14
 **/
internal class RiskCheckinHandler(private val panel: CheckinProjectPanel) : CheckinHandler() {

    override fun beforeCheckin(): ReturnResult {
        val project = panel.project
        val report: RiskReport = try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable<RiskReport, Exception> {
                    val context = DiffContextBuilder.build(project, panel)
                    RiskEngine.evaluate(context)
                },
                "GitInsight: Analyzing Commit Risk",
                /* canBeCanceled = */ true,
                project
            )
        } catch (e: Exception) {
            thisLogger().warn("[GitInsight] Risk analysis failed; allowing commit to proceed", e)
            return ReturnResult.COMMIT
        }

        thisLogger().info(
            "[GitInsight] Risk: score=${report.totalScore} level=${report.level} " +
                "rules=${report.matches.size}"
        )

        // Don't add friction for clean commits — only surface dialog for MEDIUM/HIGH.
        if (report.level == RiskLevel.LOW) return ReturnResult.COMMIT

        val proceed = RiskDialog(project, report).showAndGet()
        return if (proceed) ReturnResult.COMMIT else ReturnResult.CANCEL
    }
}
