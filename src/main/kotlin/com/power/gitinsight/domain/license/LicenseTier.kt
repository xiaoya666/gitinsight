package com.power.gitinsight.domain.license

/**
 * team : gitInsight.
 * Class Name: LicenseTier
 * Description: User tier enum used to gate Pro features. 1.0.x ships in PRO_PREVIEW for everyone — all
 *              features unlocked, "Pro Preview" badges shown so users know which surface area will
 *              require a paid license starting 1.1.x. The FREE branch exists so the gating contract
 *              compiles today; it only takes effect once PREVIEW_WINDOW_OPEN is flipped.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 17:03
 **/
internal enum class LicenseTier(val displayName: String) {
    FREE("Free"),
    PRO_PREVIEW("Pro Preview"),
    PRO("Pro"),
    ;

    fun unlocksProFeatures(): Boolean = when (this) {
        FREE -> PREVIEW_WINDOW_OPEN
        PRO_PREVIEW, PRO -> true
    }

    companion object {
        // Flip to false alongside the 1.1.x Pro launch to start gating FREE users.
        const val PREVIEW_WINDOW_OPEN: Boolean = true

        fun default(): LicenseTier = PRO_PREVIEW
    }
}
