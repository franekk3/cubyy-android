package com.cubyy.timer.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.cubyy.timer.scramble.WcaEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Displays a TNoodle-rendered 2D scramble diagram. Multi-Blind shows each cube's diagram. */
@Composable
fun WcaScrambleImages(
    eventId: String,
    scramble: String,
    imageScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val lines = if (eventId == "333mbf") {
        scramble.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    } else listOf(scramble.trim()).filter(String::isNotEmpty)

    if (lines.size <= 1) {
        val renderEventId = if (eventId == "333mbf") WcaEvent.CUBE_3_BLIND.id else eventId
        WcaScrambleImage(renderEventId, lines.firstOrNull().orEmpty(), imageScale, modifier)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Cube ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    WcaScrambleImage(
                        WcaEvent.CUBE_3_BLIND.id,
                        line,
                        imageScale,
                        Modifier.fillMaxWidth().aspectRatio(1.35f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WcaScrambleImage(eventId: String, scramble: String, imageScale: Float, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, eventId, scramble) {
        value = if (scramble.isBlank()) null else withContext(Dispatchers.Default) {
            runCatching {
                val svgText = WcaEvent.fromId(eventId).drawScrambleSvg(scramble)
                val document = SVG.getFromString(svgText)
                // TNoodle's SVG width and height are the puzzle's natural drawing
                // dimensions. Rendering it into a much larger bitmap without scaling
                // the canvas leaves the actual diagram tiny in a mostly empty image.
                val viewBox = Regex("viewBox=[\"']\\s*[-+0-9.eE]+\\s+[-+0-9.eE]+\\s+([0-9.]+)\\s+([0-9.]+)[\"']")
                    .find(svgText)?.groupValues
                val width = viewBox?.getOrNull(1)?.toFloatOrNull()?.toInt()?.coerceAtLeast(1) ?: 130
                val height = viewBox?.getOrNull(2)?.toFloatOrNull()?.toInt()?.coerceAtLeast(1) ?: 98
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                val canvas = AndroidCanvas(bitmap)
                document.renderToCanvas(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var left = bitmap.width
                var top = bitmap.height
                var right = -1
                var bottom = -1
                pixels.forEachIndexed { index, color ->
                    if (color ushr 24 != 0) {
                        val x = index % bitmap.width
                        val y = index / bitmap.width
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x)
                        bottom = maxOf(bottom, y)
                    }
                }
                if (right >= left && bottom >= top) {
                    val inset = 2
                    left = (left - inset).coerceAtLeast(0)
                    top = (top - inset).coerceAtLeast(0)
                    right = (right + inset).coerceAtMost(bitmap.width - 1)
                    bottom = (bottom + inset).coerceAtMost(bitmap.height - 1)
                    val cropped = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
                    bitmap.recycle()
                    cropped.asImageBitmap()
                } else {
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (image == null) {
            if (scramble.isBlank()) {
                Text("No scramble", modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center)
            } else {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = image!!,
                contentDescription = "2D scramble diagram",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = imageScale
                    scaleY = imageScale
                },
                contentScale = ContentScale.Fit
            )
        }
    }
}
