package com.power.gitinsight.infra.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.power.gitinsight.infra.storage.db.GitInsightDatabase
import java.nio.file.Files
import java.security.MessageDigest

/**
 * team : gitInsight.
 * Class Name: BlameStorage
 * Description: Owns the per-project SQLite connection and exposes SQLDelight queries; lifetime tied to the Project.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:13
 **/
@Service(Service.Level.PROJECT)
internal class BlameStorage(private val project: Project) : Disposable {

    private val driver: JdbcSqliteDriver by lazy { openDriver() }
    private val database: GitInsightDatabase by lazy {
        GitInsightDatabase(driver).also { GitInsightDatabase.Schema.create(driver) }
    }

    val blameQueries get() = database.blameQueries
    val hotspotQueries get() = database.fileHotspotQueries

    private fun openDriver(): JdbcSqliteDriver {
        // IDE's PluginClassLoader doesn't expose JDBC drivers via ServiceLoader to DriverManager;
        // force the static initializer to run so the driver self-registers in this classloader.
        Class.forName("org.sqlite.JDBC", true, BlameStorage::class.java.classLoader)

        val dir = PathManager.getSystemDir().resolve("gitInsight")
        Files.createDirectories(dir)
        val dbFile = dir.resolve("${projectKey(project)}.db")
        val url = "jdbc:sqlite:${dbFile.toAbsolutePath()}"
        thisLogger().info("Opening SQLite at $url")
        return JdbcSqliteDriver(url)
    }

    override fun dispose() {
        runCatching { driver.close() }
            .onFailure { thisLogger().info("Failed to close SQLite driver: ${it.message}") }
    }

    private fun projectKey(project: Project): String {
        val raw = project.locationHash.ifBlank { project.name }
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
