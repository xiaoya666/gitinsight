package com.power.gitinsight.ui.gutter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

/**
 * team : gitInsight.
 * Class Name: HotspotGutterRenderer
 * Description: Paints a 3px vertical color strip on the editor gutter; color interpolates green→yellow→red over [0, 100].
 *
 * @author: power
 * on Date: 2026/05/18 Time: 16:07
 **/
internal class HotspotGutterRenderer(private val score: Double) : LineMarkerRenderer {

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        g.color = colorFor(score)
        g.fillRect(r.x, r.y, STRIP_WIDTH, r.height)
    }

    companion object {
        const val STRIP_WIDTH = 3

        /**
         * Two linear gradients meeting at 50: green→yellow for [0,50], yellow→red for [50,100].
         * Score is clamped so out-of-range values still render the closest extreme.
         */
        fun colorFor(score: Double): Color {
            val s = score.coerceIn(0.0, 100.0)
            return if (s <= 50.0) {
                val t = s / 50.0
                lerp(GREEN, YELLOW, t)
            } else {
                val t = (s - 50.0) / 50.0
                lerp(YELLOW, RED, t)
            }
        }

        private val GREEN = Color(76, 175, 80)
        private val YELLOW = Color(255, 193, 7)
        private val RED = Color(244, 67, 54)

        private fun lerp(a: Color, b: Color, t: Double): Color {
            val r = (a.red + (b.red - a.red) * t).toInt().coerceIn(0, 255)
            val g = (a.green + (b.green - a.green) * t).toInt().coerceIn(0, 255)
            val bl = (a.blue + (b.blue - a.blue) * t).toInt().coerceIn(0, 255)
            return Color(r, g, bl)
        }
    }
}
