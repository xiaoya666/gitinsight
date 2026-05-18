package com.power.gitinsight.infra.checkin

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory

/**
 * team : gitInsight.
 * Class Name: RiskCheckinHandlerFactory
 * Description: Spawns a RiskCheckinHandler per commit dialog. Registered in plugin.xml as a checkinHandlerFactory.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 18:14
 **/
internal class RiskCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
        RiskCheckinHandler(panel)
}
