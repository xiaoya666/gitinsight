package com.power.gitinsight.domain.telemetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.UUID

/**
 * team : gitInsight.
 * Class Name: TelemetrySettings
 * Description: App-level persistent state controlling the anonymous crash-report channel. Default is
 *              disabled — telemetry only turns on after explicit consent (see GitInsightStartupActivity
 *              balloon). installId is a random UUID generated on first access; the user's identity is
 *              never derived from this value and is never persisted.
 *
 *              Per plan §11.5 we only ever ship: exception stack, IDE version, plugin version, installId.
 *              Never: code content, file paths, usernames, git URLs.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
@State(
    name = "GitInsightTelemetry",
    storages = [Storage("gitInsightTelemetry.xml")]
)
@Service(Service.Level.APP)
internal class TelemetrySettings : PersistentStateComponent<TelemetrySettings.State> {

    data class State(
        var enabled: Boolean = false,
        var consentPrompted: Boolean = false,
        var installId: String = "",
        var endpoint: String = DEFAULT_ENDPOINT
    )

    private var state = State()

    override fun getState(): State {
        if (state.installId.isBlank()) {
            state.installId = UUID.randomUUID().toString()
        }
        return state
    }

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    val enabled: Boolean get() = state.enabled
    val consentPrompted: Boolean get() = state.consentPrompted
    val installId: String get() {
        if (state.installId.isBlank()) state.installId = UUID.randomUUID().toString()
        return state.installId
    }
    val endpoint: String get() = state.endpoint.ifBlank { DEFAULT_ENDPOINT }

    fun grantConsent(enable: Boolean) {
        state.enabled = enable
        state.consentPrompted = true
    }

    fun setEnabled(value: Boolean) {
        state.enabled = value
    }

    fun setEndpoint(value: String) {
        state.endpoint = value.ifBlank { DEFAULT_ENDPOINT }
    }

    companion object {
        // Placeholder Cloudflare Pages subdomain reserved in §C-d of the plan. The Worker behind
        // /telemetry doesn't exist yet — when telemetry is off (the default), nothing is sent.
        const val DEFAULT_ENDPOINT = "https://api.gitinsight.pages.dev/telemetry"

        fun getInstance(): TelemetrySettings =
            ApplicationManager.getApplication().getService(TelemetrySettings::class.java)
    }
}
