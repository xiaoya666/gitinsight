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
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.awt.Component
import javax.swing.SwingConstants
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

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
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
    }

    init {
        configureColumns()
        installHeaderTooltips()
        add(buildToolbar(), BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        wireDoubleClickToOpen()
        refresh()
    }

    private fun installHeaderTooltips() {
        table.tableHeader = object : javax.swing.table.JTableHeader(table.columnModel) {
            override fun getToolTipText(event: java.awt.event.MouseEvent): String? {
                val viewIdx = columnModel.getColumnIndexAtX(event.x)
                if (viewIdx < 0) return null
                val modelIdx = table.convertColumnIndexToModel(viewIdx)
                return columnTooltip(modelIdx)
            }
        }
    }

    private fun columnTooltip(modelColumn: Int): String? = when (modelColumn) {
        1 -> """
            <html>
            <b>Risk score</b> (0 lowest, 100 highest).<br/><br/>
            <b>raw</b> = 1.0·ln(1+modifies) + 2.0·recency + 1.5·conflicts + 3.0·rollbacks + 0.8·ln(1+authors)<br/>
            <b>score</b> = tanh(raw / 30) × 100<br/><br/>
            Bands: <span style='color:#388e3c'>LOW &lt; 40</span> ·
            <span style='color:#f57c00'>MED 40–69</span> ·
            <span style='color:#d32f2f'>HIGH ≥ 70</span>
            </html>
        """.trimIndent()
        else -> null
    }

    private fun configureColumns() {
        val cm = table.columnModel
        cm.getColumn(0).apply {
            preferredWidth = 320
            minWidth = 180
            cellRenderer = FilePathRenderer()
        }
        cm.getColumn(1).apply {
            preferredWidth = 64
            maxWidth = 90
            cellRenderer = ScoreCellRenderer()
        }
        listOf(2, 3).forEach { i ->
            cm.getColumn(i).apply {
                preferredWidth = 64
                maxWidth = 90
            }
        }
        cm.getColumn(4).preferredWidth = 130
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

    /**
     * Paints the Score column background green/amber/red by threshold. Matches the RiskLevel bands
     * (LOW < 40, MEDIUM 40-69, HIGH >= 70) so a quick scan of the dashboard lines up with the
     * commit-time dialog risk colors.
     */
    private class ScoreCellRenderer : DefaultTableCellRenderer() {
        init { horizontalAlignment = SwingConstants.RIGHT }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val score = (value as? Number)?.toDouble() ?: 0.0
            // Selection highlight wins; otherwise paint by score.
            if (!isSelected) background = bandColor(score)
            foreground = if (isSelected) table.selectionForeground else FG
            return c
        }

        private fun bandColor(score: Double): Color = when {
            score >= 70 -> RED
            score >= 40 -> AMBER
            else -> GREEN
        }

        private companion object {
            // Near-primary saturation — Material A400 / A700. Same tone in both themes; dark text
            // keeps WCAG AA contrast (>= 6.5:1) on every band. Picks the most-saturated Material slot
            // that still leaves room for black text to read cleanly.
            val GREEN = JBColor(Color(0, 230, 118), Color(0, 230, 118))    // green A400
            val AMBER = JBColor(Color(255, 234, 0), Color(255, 234, 0))    // yellow A700 (peak chroma)
            val RED = JBColor(Color(255, 23, 68), Color(255, 23, 68))      // red A400
            val FG = JBColor(Color(33, 33, 33), Color(33, 33, 33))         // always dark text
        }
    }

    /** Shows the full file path as a tooltip so truncated cells stay readable. */
    private class FilePathRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            toolTipText = value?.toString()
            return c
        }
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
