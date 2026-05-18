package com.power.gitinsight.ui.gutter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.power.gitinsight.domain.hotspot.HotspotService

/**
 * team : gitInsight.
 * Class Name: HotspotGutterInstaller
 * Description: Attaches a Hotspot color strip to editor gutters. Listens for file-open events for the live path,
 *              and exposes a refresh hook so HotspotScanTask can repaint already-open editors after a rescan.
 *              git4idea's getRepositoryForFile() is background-thread-only, so DB reads are pushed off the EDT
 *              and the UI mutation hops back to the EDT via invokeLater.
 *
 * @author: power
 * on Date: 2026/05/18 Time: 16:07
 **/
internal class HotspotGutterInstaller(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        installFor(project, source, file)
    }

    companion object {
        /** Tracks our highlighter per-editor so a rescan can replace it instead of stacking duplicates. */
        private val HIGHLIGHTER_KEY = Key.create<RangeHighlighter>("gitinsight.hotspot.highlighter")

        /** Attach (or replace) the gutter strip for [file]'s editors using the current cached score. */
        fun installFor(project: Project, fem: FileEditorManager, file: VirtualFile) {
            val app = ApplicationManager.getApplication()
            app.executeOnPooledThread {
                if (project.isDisposed) return@executeOnPooledThread
                val score = runCatching { project.service<HotspotService>().getHotspot(file)?.hotspotScore }.getOrNull()
                    ?: return@executeOnPooledThread
                app.invokeLater {
                    if (project.isDisposed) return@invokeLater
                    fem.getEditors(file).filterIsInstance<TextEditor>().forEach { textEditor ->
                        attachStrip(textEditor, score)
                    }
                }
            }
        }

        /** Repaint every open editor — call after a scan commits so already-open files pick up fresh scores. */
        fun refreshAllOpen(project: Project) {
            val app = ApplicationManager.getApplication()
            val fem = FileEditorManager.getInstance(project)
            val files = fem.openFiles.toList()  // snapshot before going async
            app.executeOnPooledThread {
                if (project.isDisposed) return@executeOnPooledThread
                val service = project.service<HotspotService>()
                val scores: Map<VirtualFile, Double> = files.mapNotNull { f ->
                    runCatching { service.getHotspot(f)?.hotspotScore }.getOrNull()?.let { f to it }
                }.toMap()
                if (scores.isEmpty()) return@executeOnPooledThread
                app.invokeLater {
                    if (project.isDisposed) return@invokeLater
                    scores.forEach { (file, score) ->
                        fem.getEditors(file).filterIsInstance<TextEditor>().forEach { textEditor ->
                            attachStrip(textEditor, score)
                        }
                    }
                }
            }
        }

        private fun attachStrip(textEditor: TextEditor, score: Double) {
            val editor = textEditor.editor
            val document = editor.document
            if (document.textLength == 0) return
            editor.getUserData(HIGHLIGHTER_KEY)?.let { old ->
                editor.markupModel.removeHighlighter(old)
            }
            val highlighter = editor.markupModel.addRangeHighlighter(
                null,
                0,
                document.textLength,
                HighlighterLayer.LAST,
                HighlighterTargetArea.LINES_IN_RANGE
            )
            highlighter.lineMarkerRenderer = HotspotGutterRenderer(score)
            editor.putUserData(HIGHLIGHTER_KEY, highlighter)
        }
    }
}
