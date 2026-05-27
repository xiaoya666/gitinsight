package com.power.gitinsight.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.power.gitinsight.domain.activity.ActivityCommit
import com.power.gitinsight.domain.activity.ActivityService
import com.power.gitinsight.domain.activity.ActivityStats
import com.power.gitinsight.domain.incident.IncidentReason
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.GridLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * team : gitInsight.
 * Class Name: ActivityDashboardPanel
 * Description: "Activity" tab inside the GitInsight tool window — surfaces the current user's last-30-days commit volume, incident rate, and recent commit list. Pulls data via ActivityService; falls back to a friendly empty state when git is unconfigured.
 *
 * @author: power
 * on Date: 2026/05/27 Time: 13:20
 **/
internal class ActivityDashboardPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val windowDays = 30
    private val summary = JPanel(GridLayout(1, 3, 12, 0)).apply {
        border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
    }
    private val totalCell = StatCell("Commits / ${windowDays}d")
    private val incidentCell = StatCell("Incidents")
    private val rateCell = StatCell("Incident rate")
    private val tableModel = RecentTableModel()
    private val table = JBTable(tableModel).apply {
        rowHeight = 22
        autoCreateRowSorter = true
        setShowGrid(false)
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
    }

    init {
        summary.add(totalCell)
        summary.add(incidentCell)
        summary.add(rateCell)

        configureColumns()

        add(buildToolbar(), BorderLayout.NORTH)
        val center = JPanel(BorderLayout())
        center.add(summary, BorderLayout.NORTH)
        center.add(JScrollPane(table), BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)

        refresh()
    }

    private fun configureColumns() {
        val cm = table.columnModel
        cm.getColumn(0).apply {
            preferredWidth = 110
            maxWidth = 160
        }
        cm.getColumn(1).apply {
            preferredWidth = 80
            maxWidth = 110
            cellRenderer = IncidentBadgeRenderer()
        }
        cm.getColumn(2).preferredWidth = 320
    }

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup(
            object : AnAction("Refresh", "Recompute personal stats from git log", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) = refresh()
            }
        )
        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
            .also { it.targetComponent = this }
            .component
    }

    private fun refresh() {
        val root = GitRepositoryManager.getInstance(project).repositories.firstOrNull()?.root
        val stats = if (root != null) {
            project.service<ActivityService>().computeStats(root, windowDays)
        } else {
            ActivityStats.empty(windowDays)
        }
        applyStats(stats)
    }

    private fun applyStats(stats: ActivityStats) {
        totalCell.setValue(stats.totalCommits.toString())
        incidentCell.setValue(stats.incidentCommits.toString())
        val pct = (stats.incidentRate * 100).coerceIn(0.0, 100.0)
        rateCell.setValue(String.format(Locale.US, "%.1f%%", pct))
        tableModel.replaceAll(stats.recent)
    }

    /** Two-line cell with a big number and a small label, mirroring the IDE's stat tiles. */
    private class StatCell(label: String) : JPanel() {
        private val value = JBLabel("0").apply {
            font = font.deriveFont(font.size2D + 6f)
            horizontalAlignment = SwingConstants.LEFT
        }
        private val caption = JBLabel(label).apply {
            foreground = JBColor(Color(110, 110, 110), Color(160, 160, 160))
        }

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(value)
            add(caption)
        }

        fun setValue(text: String) { value.text = text }
    }

    private class IncidentBadgeRenderer : DefaultTableCellRenderer() {
        init { horizontalAlignment = SwingConstants.LEFT }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val reason = value as? IncidentReason
            text = reason?.name ?: ""
            if (!isSelected) {
                background = if (reason != null) BADGE else table.background
                foreground = if (reason != null) FG_BADGE else table.foreground
            }
            return c
        }

        private companion object {
            val BADGE = JBColor(Color(239, 154, 154), Color(110, 46, 46))
            val FG_BADGE = JBColor(Color(33, 33, 33), Color(245, 245, 245))
        }
    }

    private class RecentTableModel : AbstractTableModel() {
        private var rows: List<ActivityCommit> = emptyList()
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        fun replaceAll(newRows: List<ActivityCommit>) {
            rows = newRows
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = when (column) {
            0 -> "When"
            1 -> "Incident"
            2 -> "Subject"
            else -> ""
        }

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            1 -> IncidentReason::class.java
            else -> String::class.java
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> dateFmt.format(Date(row.timestamp))
                1 -> row.incidentReason ?: ""
                2 -> row.subject
                else -> ""
            }
        }
    }
}
