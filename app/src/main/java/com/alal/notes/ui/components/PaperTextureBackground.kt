package com.alal.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alal.notes.domain.model.PaperTexture

/** Draws dotted / lined / grid paper with plain Canvas (no bitmaps). */
@Composable
fun PaperTextureBackground(
    texture: PaperTexture,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Dp = 28.dp,
) {
    if (texture == PaperTexture.PLAIN) return
    Canvas(modifier) { drawTexture(texture, color, spacing.toPx()) }
}

fun DrawScope.drawTexture(texture: PaperTexture, color: Color, step: Float) {
    val w = size.width
    val h = size.height
    when (texture) {
        PaperTexture.PLAIN -> Unit
        PaperTexture.DOTTED -> {
            var y = step
            while (y < h) {
                var x = step
                while (x < w) {
                    drawCircle(color, radius = 1.2f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        PaperTexture.LINED -> {
            var y = step
            while (y < h) {
                drawLine(color, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += step
            }
        }
        PaperTexture.GRID -> {
            var y = step
            while (y < h) {
                drawLine(color, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += step
            }
            var x = step
            while (x < w) {
                drawLine(color, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += step
            }
        }
    }
}
