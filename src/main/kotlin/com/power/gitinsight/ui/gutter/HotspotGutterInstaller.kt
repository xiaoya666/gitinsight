package com.power.gitinsight.ui.gutter

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.domain.hotspot.HotspotService

/**
 * team : gitInsight.
 * Class Name: HotspotGutterInstaller
 * Description: On file open, attaches a Hotspot color strip to the editor gutter for files with a cached hotspot score.
 *              Highlighters live on the editor's markup model and dispose with the editor — no manual cleanup needed.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 16:07
 **/
internal class HotspotGutterInstaller(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val score = project.service<HotspotService>().getHotspot(file)?.hotspotScore ?: return
        source.getEditors(file).filterIsInstance<TextEditor>().forEach { editor ->
            attachStrip(editor, score)
        }
    }

    private fun attachStrip(textEditor: TextEditor, score: Double) {
        val editor = textEditor.editor
        val document = editor.document
        if (document.textLength == 0) return
        val highlighter = editor.markupModel.addRangeHighlighter(
            null,
            0,
            document.textLength,
            HighlighterLayer.LAST,
            HighlighterTargetArea.LINES_IN_RANGE
        )
        highlighter.lineMarkerRenderer = HotspotGutterRenderer(score)
    }
}
