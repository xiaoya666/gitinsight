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
    }

    private var state = LicenseState()

    val tier: LicenseTier get() = state.tier
    val licenseKey: String get() = state.licenseKey

    fun unlocksProFeatures(): Boolean = state.tier.unlocksProFeatures()

    override fun getState(): LicenseState = state

    override fun loadState(loaded: LicenseState) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    companion object {
        fun getInstance(): LicenseSettings =
            ApplicationManager.getApplication().getService(LicenseSettings::class.java)
    }
}
