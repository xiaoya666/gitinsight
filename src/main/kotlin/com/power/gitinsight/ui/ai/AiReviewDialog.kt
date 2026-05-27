package com.power.gitinsight.ui.ai

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * team : gitInsight.
 * Class Name: AiReviewDialog
 * Description: Read-only modal that displays the AI's diff-review markdown response. Monospace + word
 *              wrap so file paths and code excerpts stay readable. Only a Close button — the dialog is
 *              advisory; the user copies what they need.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal class AiReviewDialog(
    project: Project,
    private val providerName: String,
    private val markdown: String
) : DialogWrapper(project) {

    init {
        title = "GitInsight: AI Review ($providerName)"
        setOKButtonText("关闭")
        init()
    }

    /** Hide the Cancel button — there's nothing to commit; this is read-only. */
    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val text = JTextArea(markdown).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            caretPosition = 0
        }
        val scroll = JScrollPane(text).apply {
            preferredSize = Dimension(720, 480)
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }
}
