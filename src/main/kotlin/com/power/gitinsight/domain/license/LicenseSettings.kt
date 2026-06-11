package com.power.gitinsight.domain.license

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * team : gitInsight.
 * Class Name: LicenseSettings
 * Description: App-level license tier + reserved license-key holder. Persists to gitinsight-license.xml.
 *              The licenseKey field is intentionally non-functional in 1.0.x — it's a UX placeholder so
 *              the eventual 1.1.x activation flow only needs to enable the field, not rewire storage.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 17:05
 **/
@Service(Service.Level.APP)
@State(name = "GitInsightLicense", storages = [Storage("gitinsight-license.xml")])
internal class LicenseSettings : PersistentStateComponent<LicenseSettings.LicenseState> {

    class LicenseState {
        var tier: LicenseTier = LicenseTier.default()
        var licenseKey: String = ""
        var expiresAt: Long = 0L // epoch seconds, 0 = perpetual; mirrors the signed key's exp
    }

    private var state = LicenseState()

    val tier: LicenseTier get() = state.tier
    val licenseKey: String get() = state.licenseKey
    val expiresAt: Long get() = state.expiresAt

    fun unlocksProFeatures(): Boolean = state.tier.unlocksProFeatures()

    /**
     * Verify [key] offline and, if valid, persist its tier/key/expiry. A failed verification leaves
     * the current state untouched and returns the reason so the UI can surface it. Dormant in 1.0.x
     * (the activation UI is disabled and the embedded public key is a placeholder) but ready for 1.1.x.
     */
    fun activate(key: String): LicenseVerifier.Verified {
        val result = LicenseVerifier.verify(key)
        if (result.valid) {
            state.tier = result.tier
            state.licenseKey = key.trim()
            state.expiresAt = result.expiresAt
        }
        return result
    }

    /** Drop any stored license and fall back to the default tier (Pro Preview during 1.0.x). */
    fun deactivate() {
        state.tier = LicenseTier.default()
        state.licenseKey = ""
        state.expiresAt = 0L
    }

    override fun getState(): LicenseState = state

    override fun loadState(loaded: LicenseState) {
        XmlSerializerUtil.copyBean(loaded, state)
        // Re-verify a previously stored key on startup so an expired/revoked-then-edited key can't keep
        // unlocking Pro. Only runs when a key is present, so the keyless preview default is untouched.
        val key = state.licenseKey
        if (key.isNotBlank()) {
            val result = LicenseVerifier.verify(key)
            if (!result.valid) deactivate()
        }
    }

    companion object {
        fun getInstance(): LicenseSettings =
            ApplicationManager.getApplication().getService(LicenseSettings::class.java)
    }
}
