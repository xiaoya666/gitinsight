package com.power.gitinsight.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.table.JBTable
import com.power.gitinsight.domain.hotspot.FileHotspot
import com.power.gitinsight.domain.hotspot.HotspotService
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel

/**
 * team : gitInsight.
 * Class Name: HotspotDashboardPanel
 * Description: Top-N hottest files for the project's first git root. Refresh re-reads the cache; Rescan kicks off a background scan.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 16:07
 **/
internal class HotspotDashboardPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val model = HotspotTableModel()
    private val table = JBTable(model).apply {
        autoCreateRowSorter = true
        rowHeight = 22
        setShowGrid(false)
    }

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        wireDoubleClickToOpen()
        refresh()
    }

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup(
            object : AnAction("Refresh", "Reload top hotspots from cache", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) = refresh()
            },
            object : AnAction("Rescan", "Force a full hotspot rescan", AllIcons.Actions.Rerun) {
                override fun actionPerformed(e: AnActionEvent) {
                    project.service<HotspotService>().rescan()
                }
            }
        )
        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
            .also { it.targetComponent = this }
            .component
    }

    private fun refresh() {
        val root = GitRepositoryManager.getInstance(project).repositories.firstOrNull()?.root
        val rows = if (root != null) {
            project.service<HotspotService>().getTopHotspots(root, limit = 50)
        } else {
            emptyList()
        }
        model.replaceAll(rows)
        table.rowSorter?.sortKeys = listOf(RowSorter.SortKey(1, SortOrder.DESCENDING))
    }

    private fun wireDoubleClickToOpen() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val viewRow = table.selectedRow.takeIf { it >= 0 } ?: return
                val relativePath = model.relativePathAt(table.convertRowIndexToModel(viewRow))
                val root = GitRepositoryManager.getInstance(project).repositories.firstOrNull()?.root ?: return
                val target = root.findFileByRelativePath(relativePath) ?: return
                FileEditorManager.getInstance(project).openFile(target, true)
            }
        })
    }

    private class HotspotTableModel : AbstractTableModel() {
        private var rows: List<FileHotspot> = emptyList()
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        fun replaceAll(newRows: List<FileHotspot>) {
            rows = newRows
            fireTableDataChanged()
        }

        fun relativePathAt(row: Int): String = rows[row].filePath

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 5
        override fun getColumnName(column: Int): String = when (column) {
            0 -> "File"
            1 -> "Score"
            2 -> "Modify"
            3 -> "Authors"
            4 -> "Last Modified"
            else -> ""
        }
        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            1 -> java.lang.Double::class.java
            2, 3 -> java.lang.Integer::class.java
            else -> String::class.java
        }
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.filePath
                1 -> "%.1f".format(row.hotspotScore).toDouble()
                2 -> row.modifyCount
                3 -> row.authorCount
                4 -> if (row.lastModified > 0) dateFmt.format(Date(row.lastModified)) else ""
                else -> ""
            }
        }
    }
}
