package com.power.gitinsight.ui.tooltip

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import com.power.gitinsight.domain.blame.BlameLine
import com.power.gitinsight.domain.blame.BlameService
import java.util.function.Supplier

/**
 * team : gitInsight.
 * Class Name: BlameLineMarkerProvider
 * Description: Enhanced Blame entry point; renders per-line gutter markers whose tooltip surfaces the BlameSnapshot data.
 *
 * @author: power
 * on Date: 2026/05/17 Time: 20:40
 **/
internal class BlameLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val first = elements.first()
        val project = first.project
        val containingFile = first.containingFile ?: return
        val virtualFile = containingFile.virtualFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile) ?: return

        thisLogger().info("[GitInsight] collectSlowLineMarkers entry: file=${virtualFile.path} elements=${elements.size}")

        val snapshot = project.service<BlameService>().getBlame(virtualFile)
        if (snapshot == null) {
            thisLogger().info("[GitInsight] getBlame returned null for ${virtualFile.path}")
            return
        }
        if (snapshot.lines.isEmpty()) {
            thisLogger().info("[GitInsight] snapshot.lines empty for ${virtualFile.path}")
            return
        }
        val byLine = snapshot.lines.associateBy { it.lineNumber }
        thisLogger().info("[GitInsight] snapshot has ${snapshot.lines.size} blame lines, head=${snapshot.headCommitId.take(8)}")

        val seen = HashSet<Int>(elements.size)
        var emitted = 0
        for (element in elements) {
            if (element.firstChild != null) continue  // only leaves
            if (element.containingFile !== containingFile) continue

            val offset = element.textRange.startOffset
            if (offset < 0 || offset > document.textLength) continue
            val line = document.getLineNumber(offset) + 1
            if (!seen.add(line)) continue
            if (!BlameMarkerStrategy.shouldEmit(line, byLine)) continue

            val blame = byLine[line] ?: continue
            result.add(buildMarker(element, blame))
            emitted++
        }
        thisLogger().info("[GitInsight] emitted=$emitted markers for ${virtualFile.path}")
    }

    private fun buildMarker(element: PsiElement, blame: BlameLine): LineMarkerInfo<PsiElement> {
        val tooltipProvider = Function<PsiElement, String> { BlameTooltipRenderer.renderHtml(blame) }
        val accessibleName = Supplier { "GitInsight blame: ${blame.author}" }
        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Vcs.History,
            tooltipProvider,
            null,
            GutterIconRenderer.Alignment.LEFT,
            accessibleName
        )
    }
}
