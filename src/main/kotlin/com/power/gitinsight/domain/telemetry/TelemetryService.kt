package com.power.gitinsight.domain.telemetry

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.power.gitinsight.domain.ai.HttpJsonClient
import com.power.gitinsight.domain.ai.Json
import java.io.PrintWriter
import java.io.StringWriter

/**
 * team : gitInsight.
 * Class Name: TelemetryService
 * Description: App-level, fire-and-forget anonymous crash reporter. Never throws back to callers.
 *              Strictly contract-limited per plan §11.5:
 *                ✓ exception stack (truncated)
 *                ✓ IDE build (e.g. IC-242.20224.91)
 *                ✓ plugin version
 *                ✓ anonymous installId (random UUID; not derived from any user identity)
 *                ✗ no code content, file paths, usernames, or git URLs
 *
 *              Disabled by default. Even when enabled, every send is wrapped in runCatching so a
 *              backend outage never bubbles into user-visible errors.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
@Service(Service.Level.APP)
internal class TelemetryService {

    fun reportException(throwable: Throwable, hint: String = "") {
        val settings = TelemetrySettings.getInstance()
        if (!settings.enabled) return

        val body = buildPayload(
            kind = "exception",
            message = (hint.ifBlank { throwable.javaClass.simpleName }).take(MAX_MESSAGE),
            stack = stackToString(throwable).take(MAX_STACK),
            installId = settings.installId
        )
        sendAsync(settings.endpoint, body)
    }

    /** Manual round-trip check used by the Settings UI to validate the endpoint is reachable. */
    fun sendPing(): Boolean {
        val settings = TelemetrySettings.getInstance()
        if (!settings.enabled) return false

        val body = buildPayload(
            kind = "ping",
            message = "consent-test",
            stack = "",
            installId = settings.installId
        )
        return runCatching {
            HttpJsonClient.postJson(settings.endpoint, body, timeoutSeconds = 10)
            true
        }.getOrElse {
            thisLogger().info("TelemetryService.sendPing failed: ${it.message}")
            false
        }
    }

    private fun sendAsync(url: String, body: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                HttpJsonClient.postJson(url, body, timeoutSeconds = 5)
            }.onFailure {
                // Never surface telemetry failures to users.
                thisLogger().info("TelemetryService.sendAsync failed: ${it.message}")
            }
        }
    }

    private fun buildPayload(
        kind: String,
        message: String,
        stack: String,
        installId: String
    ): String {
        val ide = ApplicationInfo.getInstance()
        return buildString {
            append('{')
            append(Json.escape("installId")).append(':').append(Json.escape(installId)).append(',')
            append(Json.escape("kind")).append(':').append(Json.escape(kind)).append(',')
            append(Json.escape("pluginVersion")).append(':').append(Json.escape(pluginVersionString())).append(',')
            append(Json.escape("ideBuild")).append(':').append(Json.escape(ide.build.asString())).append(',')
            append(Json.escape("os")).append(':').append(Json.escape(System.getProperty("os.name") ?: "")).append(',')
            append(Json.escape("message")).append(':').append(Json.escape(message)).append(',')
            append(Json.escape("stack")).append(':').append(Json.escape(stack))
            append('}')
        }
    }

    private fun pluginVersionString(): String {
        val id = PluginId.findId(PLUGIN_ID) ?: return ""
        val descriptor = com.intellij.ide.plugins.PluginManagerCore.getPlugin(id) ?: return ""
        return descriptor.version ?: ""
    }

    private fun stackToString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    companion object {
        // Matches the <id> in plugin.xml (com.power.gitinsight).
        const val PLUGIN_ID = "com.power.gitinsight"
        private const val MAX_MESSAGE = 500
        private const val MAX_STACK = 4096

        fun getInstance(): TelemetryService =
            ApplicationManager.getApplication().getService(TelemetryService::class.java)
    }
}
