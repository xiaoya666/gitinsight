package com.power.gitinsight.domain.risk

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.power.gitinsight.domain.license.LicenseSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * team : gitInsight.
 * Class Name: RiskRulesProjectLoader
 * Description: Reads .gitinsight/risk.yml from the project root and caches the parsed RuleOverrides until
 *              the file's mtime changes. Bypassed entirely when the file doesn't exist, so a project that
 *              never authored one pays no cost. Parse errors are swallowed and logged; the engine falls
 *              back to defaults so a bad YAML file can never block a commit.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
@Service(Service.Level.PROJECT)
internal class RiskRulesProjectLoader(private val project: Project) {

    private var cached: RuleOverrides = RuleOverrides.EMPTY
    private var cachedMTime: FileTime? = null
    private var cachedPathSeen: Path? = null

    /** Snapshot the current overrides, re-reading the YAML if it changed since last call. */
    @Synchronized
    fun load(): RuleOverrides {
        // YAML rule overrides are a Pro feature (dormant during the 1.0.x preview — unlocksProFeatures() is true for everyone).
        if (!LicenseSettings.getInstance().unlocksProFeatures()) return RuleOverrides.EMPTY
        val base = project.basePath ?: return RuleOverrides.EMPTY
        val path = Path.of(base, RELATIVE_PATH)

        if (!Files.exists(path)) {
            // File deleted since last read? Drop the cache.
            if (cached != RuleOverrides.EMPTY) {
                cached = RuleOverrides.EMPTY
                cachedMTime = null
                cachedPathSeen = null
            }
            return RuleOverrides.EMPTY
        }

        val mtime = runCatching { Files.getLastModifiedTime(path) }.getOrNull()
        if (path == cachedPathSeen && mtime != null && mtime == cachedMTime) {
            return cached
        }

        val text = runCatching { Files.readString(path) }.getOrElse {
            thisLogger().info("RiskRulesProjectLoader: failed to read $path: ${it.message}")
            return cached
        }

        val parsed = runCatching { RiskRulesYamlParser.parse(text) }.getOrElse {
            thisLogger().warn("RiskRulesProjectLoader: parse failed for $path", it)
            return cached
        }

        cached = parsed
        cachedMTime = mtime
        cachedPathSeen = path
        thisLogger().info(
            "RiskRulesProjectLoader: loaded ${parsed.disabledIds.size} disabled / " +
                "${parsed.deltaOverrides.size} delta override(s) from $path"
        )
        return parsed
    }

    companion object {
        const val RELATIVE_PATH = ".gitinsight/risk.yml"
    }
}
