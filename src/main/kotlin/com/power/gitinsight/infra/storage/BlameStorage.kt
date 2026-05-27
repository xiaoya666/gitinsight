package com.power.gitinsight.infra.storage

import app.cash.sqldelight.db.QueryResult
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
        ensureSchema()
        GitInsightDatabase(driver)
    }

    val blameQueries get() = database.blameQueries
    val hotspotQueries get() = database.hotspotQueries
    val incidentQueries get() = database.incidentQueries

    /**
     * Idempotent schema bootstrap using SQLite's PRAGMA user_version.
     * Was previously unconditional Schema.create, which threw "table already exists" on every
     * non-first run and silently broke hotspot writes (BlameService swallowed it via runCatching).
     */
    private fun ensureSchema() {
        val schema = GitInsightDatabase.Schema
        var current = readUserVersion()

        if (current == 0L) {
            if (tablesExist()) {
                // Legacy DB created before we tracked user_version (0.1.0). Pin to v1 baseline
                // so the forward-migration path below can take it from v1 to the current version.
                current = 1L
                writeUserVersion(1L)
                thisLogger().info("BlameStorage: legacy DB detected, stamped baseline v1")
            } else {
                schema.create(driver)
                writeUserVersion(schema.version)
                thisLogger().info("BlameStorage: created schema v${schema.version}")
                return
            }
        }

        if (current < schema.version) {
            schema.migrate(driver, current, schema.version)
            writeUserVersion(schema.version)
            thisLogger().info("BlameStorage: migrated schema $current -> ${schema.version}")
        } else {
            thisLogger().info("BlameStorage: schema up-to-date at v$current")
        }
    }

    private fun tablesExist(): Boolean {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='blame_line'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
            },
            parameters = 0
        ).value
    }

    private fun readUserVersion(): Long {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0
        ).value
    }

    private fun writeUserVersion(version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }

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
