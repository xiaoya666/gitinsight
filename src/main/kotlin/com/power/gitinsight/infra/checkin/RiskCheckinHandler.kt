package com.power.gitinsight.infra.checkin

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.checkin.CheckinHandler

/**
 * team : gitInsight.
 * Class Name: RiskCheckinHandler
 * Description: T11 stub — observes the set of files being committed and logs the count.
 *              T12 plugs in RiskEngine; T13 shows the Risk Dialog before returning ReturnResult.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 18:14
 **/
internal class RiskCheckinHandler(private val panel: CheckinProjectPanel) : CheckinHandler() {

    override fun beforeCheckin(): ReturnResult {
        val files = panel.virtualFiles
        thisLogger().info(
            "[GitInsight] CheckinHandler observed ${files.size} file(s): " +
                files.joinToString(limit = 5) { it.path }
        )
        return ReturnResult.COMMIT
    }
}
