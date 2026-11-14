package com.cubyy.timer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.random.Random

private data class ConfettiPiece(
    val startX: Float,
    val delay: Float,
    val duration: Float,
    val drift: Float,
    val phase: Float,
    val widthDp: Float,
    val heightDp: Float,
    val initialRotation: Float,
    val spin: Float,
    val colorIndex: Int,
    val shape: Int
)

@Composable
fun ConfettiOverlay(eventId: Long, onFinished: () -> Unit) {
    val progress = remember(eventId) { Animatable(0f) }
    LaunchedEffect(eventId) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 2800, easing = LinearEasing))
        onFinished()
    }

    val colors = remember {
        listOf(
            Color(0xFFFF5252), Color(0xFFFFC400), Color(0xFF00C853),
            Color(0xFF448AFF), Color(0xFFE040FB), Color(0xFFFF6D00)
        )
    }
    val pieces = remember(eventId) {
        val random = Random(eventId)
        List(68) {
            ConfettiPiece(
                startX = random.nextFloat(),
                delay = random.nextFloat() * 0.12f,
                duration = 0.76f + random.nextFloat() * 0.12f,
                drift = (random.nextFloat() - 0.5f) * 0.24f,
                phase = random.nextFloat() * (2f * Math.PI.toFloat()),
                widthDp = 3.5f + random.nextFloat() * 3.5f,
                heightDp = 6f + random.nextFloat() * 5f,
                initialRotation = random.nextFloat() * 360f,
                spin = (random.nextFloat() - 0.5f) * 300f,
                colorIndex = random.nextInt(colors.size),
                shape = random.nextInt(3)
            )
        }
    }
    val density = LocalDensity.current.density

    Canvas(Modifier.fillMaxSize()) {
        val animation = progress.value
        pieces.forEach { piece ->
            val local = ((animation - piece.delay) / piece.duration).coerceIn(0f, 1f)
            if (animation < piece.delay) return@forEach

            // Gravity speeds the pieces up as they fall; a small side drift and
            // flutter keep them from moving like a synchronized curtain.
            val fall = local * local * (1.5f - 0.5f * local)
            val width = piece.widthDp * density
            val height = piece.heightDp * density
            val sway = kotlin.math.sin(local * 5.5f + piece.phase) * (5f + piece.widthDp) * density
            val x = (piece.startX + piece.drift * fall) * size.width + sway
            val y = -height + fall * (size.height + height * 2f)
            val fade = ((1f - local) / 0.14f).coerceIn(0f, 1f)
            val color = colors[piece.colorIndex].copy(alpha = fade)
            val rotation = piece.initialRotation + piece.spin * local +
                kotlin.math.sin(local * 6f + piece.phase) * 22f

            rotate(rotation, pivot = Offset(x, y)) {
                when (piece.shape) {
                    0 -> drawRect(color, Offset(x - width / 2f, y - height / 2f), Size(width, height))
                    1 -> drawOval(color, Offset(x - width / 2f, y - height / 2f), Size(width, height))
                    else -> drawCircle(color, radius = width / 2f, center = Offset(x, y))
                }
            }
        }
    }
}
