package com.alal.notes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Empty-state illustration: a pen nib drawing a looping line. Pure Canvas, no assets,
 * replaces the Lottie file so the project builds fully offline.
 */
@Composable
fun WritingPenAnimation(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "pen")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "progress",
    )
    val paperColor = MaterialTheme.colorScheme.surfaceContainer
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(180.dp)) {
        val w = size.width
        val h = size.height
        // paper
        drawRoundRect(paperColor, topLeft = Offset(w * 0.12f, h * 0.18f), size = Size(w * 0.76f, h * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
        // written lines that grow with t
        val lines = 4
        for (i in 0 until lines) {
            val y = h * (0.34f + i * 0.12f)
            val start = (t * lines - i).coerceIn(0f, 1f)
            if (start > 0f) {
                drawLine(lineColor.copy(alpha = 0.5f), Offset(w * 0.22f, y), Offset(w * (0.22f + 0.5f * start), y), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
        // pen position follows the current line
        val lineIdx = (t * lines).toInt().coerceIn(0, lines - 1)
        val frac = (t * lines - lineIdx).coerceIn(0f, 1f)
        val px = w * (0.22f + 0.5f * frac)
        val py = h * (0.34f + lineIdx * 0.12f)
        rotate(-35f, pivot = Offset(px, py)) {
            val pen = Path().apply {
                moveTo(px, py)
                lineTo(px + w * 0.05f, py - h * 0.08f)
                lineTo(px + w * 0.05f, py - h * 0.42f)
                lineTo(px - w * 0.05f, py - h * 0.42f)
                lineTo(px - w * 0.05f, py - h * 0.08f)
                close()
            }
            drawPath(pen, tint)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(px, py), Offset(px, py - h * 0.08f), strokeWidth = 2f)
        }
    }
}

private class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float)

/** Short confetti burst when the word goal is reached. Call inside a Box overlay. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    if (trigger == 0) return
    val scheme = MaterialTheme.colorScheme
    val particles = remember(trigger) {
        val rnd = Random(trigger)
        val colors = listOf(scheme.primary, scheme.tertiary, Color(0xFFF2A900), Color(0xFFE0645C), Color(0xFF2E9E5B))
        List(64) {
            Particle(
                angle = rnd.nextFloat() * 360f,
                speed = 0.35f + rnd.nextFloat() * 0.65f,
                color = colors[rnd.nextInt(colors.size)],
                size = 6f + rnd.nextFloat() * 8f,
                spin = rnd.nextFloat() * 720f,
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1400))
        onFinished()
    }
    val p = progress.value
    if (p >= 1f) return
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.35f
            val radius = size.minDimension * 0.9f
            for (pt in particles) {
                val rad = Math.toRadians(pt.angle.toDouble())
                val dist = radius * pt.speed * p
                val gravity = size.height * 0.6f * p * p
                val x = cx + (cos(rad) * dist).toFloat()
                val y = cy + (sin(rad) * dist).toFloat() + gravity
                val alpha = (1f - p).coerceIn(0f, 1f)
                rotate(pt.spin * p, pivot = Offset(x, y)) {
                    drawRoundRect(
                        pt.color.copy(alpha = alpha),
                        topLeft = Offset(x - pt.size / 2, y - pt.size / 4),
                        size = Size(pt.size, pt.size / 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                    )
                }
            }
        }
    }
}

/** Thin progress bar used under the editor status strip. */
@Composable
fun GoalProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(track, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h))
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h))
    }
}
