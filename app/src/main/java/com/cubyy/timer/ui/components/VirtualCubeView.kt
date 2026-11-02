package com.cubyy.timer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.delay
import com.cubyy.timer.smartcube.model.CubeMove
import com.cubyy.timer.smartcube.model.CubeColor
import com.cubyy.timer.smartcube.model.CubeFace
import com.cubyy.timer.smartcube.model.CubeOrientation
import com.cubyy.timer.smartcube.model.VirtualCube
import com.cubyy.timer.timer.CubeTurnAnimation
import kotlinx.coroutines.launch

@Composable
fun VirtualCubeView(
    virtualCube: VirtualCube,
    animation: CubeTurnAnimation? = null,
    orientation: CubeOrientation = CubeOrientation.IDENTITY,
    appearance: String = "CLASSIC",
    autoRotate: Boolean = false,
    modifier: Modifier = Modifier
) {
    CubeIsometric3D(virtualCube, animation, orientation, appearance, autoRotate, modifier.fillMaxWidth().aspectRatio(1.2f))
}

private fun toComposeColor(cubeColor: CubeColor): Color {
    return when (cubeColor) {
        CubeColor.WHITE -> Color(0xFFF0F0F0)
        CubeColor.YELLOW -> Color(0xFFFFD600)
        CubeColor.GREEN -> Color(0xFF00E676)
        CubeColor.BLUE -> Color(0xFF2979FF)
        CubeColor.RED -> Color(0xFFFF1744)
        CubeColor.ORANGE -> Color(0xFFFF9100)
    }
}

private data class CubePoint3D(val x: Float, val y: Float, val z: Float)

private data class CubeFaceGeometry(
    val face: CubeFace,
    val normal: CubePoint3D,
    val right: CubePoint3D,
    val up: CubePoint3D
)

private data class CubeletPosition(val x: Int, val y: Int, val z: Int)

private data class StickerKey(
    val cubelet: CubeletPosition,
    val nx: Int,
    val ny: Int,
    val nz: Int
)

private data class RenderFace(
    val points: List<Offset>,
    val color: Color,
    val depth: Float,
    val cornerRadius: Float,
    val outlineColor: Color,
    val outlineWidth: Float,
    val cornerRadii: List<Float>? = null,
    val centerRecess: Boolean = false
)

private val cubeOrientationVectorConverter = TwoWayConverter<CubeOrientation, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.x, it.y, it.z, it.w) },
    convertFromVector = { CubeOrientation(it.v1, it.v2, it.v3, it.v4).normalized() }
)

private val CUBE_FACES = listOf(
    CubeFaceGeometry(CubeFace.U, CubePoint3D(0f, 1f, 0f), CubePoint3D(1f, 0f, 0f), CubePoint3D(0f, 0f, -1f)),
    CubeFaceGeometry(CubeFace.D, CubePoint3D(0f, -1f, 0f), CubePoint3D(1f, 0f, 0f), CubePoint3D(0f, 0f, 1f)),
    CubeFaceGeometry(CubeFace.F, CubePoint3D(0f, 0f, 1f), CubePoint3D(1f, 0f, 0f), CubePoint3D(0f, 1f, 0f)),
    CubeFaceGeometry(CubeFace.B, CubePoint3D(0f, 0f, -1f), CubePoint3D(-1f, 0f, 0f), CubePoint3D(0f, 1f, 0f)),
    CubeFaceGeometry(CubeFace.R, CubePoint3D(1f, 0f, 0f), CubePoint3D(0f, 0f, -1f), CubePoint3D(0f, 1f, 0f)),
    CubeFaceGeometry(CubeFace.L, CubePoint3D(-1f, 0f, 0f), CubePoint3D(0f, 0f, 1f), CubePoint3D(0f, 1f, 0f))
)

@Composable
private fun CubeIsometric3D(
    virtualCube: VirtualCube,
    animation: CubeTurnAnimation?,
    orientation: CubeOrientation,
    appearance: String,
    autoRotate: Boolean,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableFloatStateOf(-0.65f) }
    var pitch by remember { mutableFloatStateOf(0.52f) }
    val turnProgress = remember { Animatable(1f) }
    val cubePulse = remember { Animatable(0f) }
    val smoothedOrientation = remember {
        Animatable(CubeOrientation.IDENTITY, cubeOrientationVectorConverter)
    }

    LaunchedEffect(orientation) {
        smoothedOrientation.animateTo(
            targetValue = orientation.normalized(),
            animationSpec = tween(durationMillis = 55, easing = LinearEasing)
        )
    }

    LaunchedEffect(autoRotate) {
        if (autoRotate) {
            while (true) {
                delay(32)
                yaw += 0.0035f
            }
        }
    }

    LaunchedEffect(animation?.sequence) {
        if (animation != null) {
            turnProgress.snapTo(0f)
            cubePulse.snapTo(0f)
            launch {
                cubePulse.animateTo(1f, tween(durationMillis = 90))
                cubePulse.animateTo(0f, tween(durationMillis = 250))
            }
            turnProgress.animateTo(1f, tween(durationMillis = 300))
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                yaw += dragAmount.x * 0.012f
                pitch = (pitch - dragAmount.y * 0.012f).coerceIn(-1.25f, 1.25f)
            }
        }
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val cubeScale = minOf(size.width, size.height) * (0.32f + cubePulse.value * 0.006f)
        val animatedYaw = yaw + cubePulse.value * 0.08f
        val cosYaw = kotlin.math.cos(animatedYaw.toDouble()).toFloat()
        val sinYaw = kotlin.math.sin(animatedYaw.toDouble()).toFloat()
        val cosPitch = kotlin.math.cos(pitch.toDouble()).toFloat()
        val sinPitch = kotlin.math.sin(pitch.toDouble()).toFloat()

        fun rotate(point: CubePoint3D): CubePoint3D {
            val gyroRotated = smoothedOrientation.value.rotate(floatArrayOf(point.x, point.y, point.z))
            val yawX = gyroRotated[0] * cosYaw + gyroRotated[2] * sinYaw
            val yawZ = -gyroRotated[0] * sinYaw + gyroRotated[2] * cosYaw
            return CubePoint3D(
                yawX,
                gyroRotated[1] * cosPitch - yawZ * sinPitch,
                gyroRotated[1] * sinPitch + yawZ * cosPitch
            )
        }

        fun project(point: CubePoint3D): Offset {
            val rotated = rotate(point)
            val perspective = 4f / (4f - rotated.z)
            return Offset(
                centerX + rotated.x * cubeScale * perspective,
                centerY - rotated.y * cubeScale * perspective
            )
        }

        val isTurning = animation != null && turnProgress.value < 1f
        val stickerCube = if (isTurning) animation!!.before else virtualCube
        val turnMove = if (isTurning) animation!!.move else null
        val stickerColors = mutableMapOf<StickerKey, CubeColor>()
        for (geometry in CUBE_FACES) {
            val stickers = stickerCube.getFace(geometry.face)
            for (row in 0..2) {
                for (col in 0..2) {
                    val center = facePoint(geometry, (col - 1) * (2f / 3f), (1 - row) * (2f / 3f), 2f / 3f)
                    stickerColors[StickerKey(center.toCubeletPosition(), geometry.normal.x.toInt(), geometry.normal.y.toInt(), geometry.normal.z.toInt())] =
                        stickers[row * 3 + col]
                }
            }
        }

        val renderedFaces = mutableListOf<RenderFace>()
        val stickerless = appearance == "STICKERLESS"
        val halfCubelet = if (stickerless) 0.329f else 0.305f
        val bodyHalfCubelet = if (stickerless) 0.333f else halfCubelet
        for (x in -1..1) for (y in -1..1) for (z in -1..1) {
            val cubelet = CubeletPosition(x, y, z)
            val center = CubePoint3D(x * (2f / 3f), y * (2f / 3f), z * (2f / 3f))
            val movesWithLayer = turnMove?.let { isInTurningLayer(center, it.face) } == true

            for (geometry in CUBE_FACES) {
                val localNormal = geometry.normal
                val normal = if (movesWithLayer) rotateTurningLayerPoint(localNormal, turnMove!!, turnProgress.value) else localNormal
                val cameraNormal = rotate(normal)
                if (cameraNormal.z <= 0.01f) continue
                val stickerColor = stickerColors[StickerKey(cubelet, localNormal.x.toInt(), localNormal.y.toInt(), localNormal.z.toInt())]
                val cubieColor = stickerColor ?: stickerColors.entries.firstOrNull { it.key.cubelet == cubelet }?.value

                fun cubeletPoint(horizontal: Float, vertical: Float, depth: Float): CubePoint3D {
                    val point = CubePoint3D(
                        center.x + geometry.right.x * horizontal + geometry.up.x * vertical + geometry.normal.x * depth,
                        center.y + geometry.right.y * horizontal + geometry.up.y * vertical + geometry.normal.y * depth,
                        center.z + geometry.right.z * horizontal + geometry.up.z * vertical + geometry.normal.z * depth
                    )
                    return if (movesWithLayer) rotateTurningLayerPoint(point, turnMove!!, turnProgress.value) else point
                }

                val faceCenter = cubeletPoint(0f, 0f, halfCubelet)
                val bodyPoints = listOf(
                    cubeletPoint(-bodyHalfCubelet, bodyHalfCubelet, halfCubelet),
                    cubeletPoint(bodyHalfCubelet, bodyHalfCubelet, halfCubelet),
                    cubeletPoint(bodyHalfCubelet, -bodyHalfCubelet, halfCubelet),
                    cubeletPoint(-bodyHalfCubelet, -bodyHalfCubelet, halfCubelet)
                ).map(::project)
                val bodyColor = if (stickerless) Color.Black else Color(0xFF101214)
                val bodyOutline = if (stickerless) Color.Black else Color(0xFF101214)
                renderedFaces += RenderFace(
                    bodyPoints,
                    bodyColor,
                    rotate(faceCenter).z,
                    if (stickerless) 0f else size.minDimension * 0.014f,
                    bodyOutline,
                    size.minDimension * if (stickerless) 0.0014f else 0.006f
                )

                if (stickerColor != null) {
                    val isCenterPiece =
                        (cubelet.x * geometry.right.x + cubelet.y * geometry.right.y + cubelet.z * geometry.right.z) == 0f &&
                        (cubelet.x * geometry.up.x + cubelet.y * geometry.up.y + cubelet.z * geometry.up.z) == 0f
                    val stickerHalf = when {
                        !stickerless -> 0.275f
                        // Keep a visible but smaller black recess around the center cap.
                        isCenterPiece -> halfCubelet - 0.026f
                        else -> halfCubelet - 0.003f
                    }
                    val stickerDepth = halfCubelet + 0.006f
                    val stickerPoints = listOf(
                        cubeletPoint(-stickerHalf, stickerHalf, stickerDepth),
                        cubeletPoint(stickerHalf, stickerHalf, stickerDepth),
                        cubeletPoint(stickerHalf, -stickerHalf, stickerDepth),
                        cubeletPoint(-stickerHalf, -stickerHalf, stickerDepth)
                    ).map(::project)
                    val light = (0.68f + cameraNormal.z * 0.18f + cameraNormal.y.coerceAtLeast(0f) * 0.12f).coerceIn(0.55f, 1f)
                    val color = toComposeColor(stickerColor)
                    val shadedColor = Color(color.red * light, color.green * light, color.blue * light, color.alpha)
                    val stickerOutline = Color.Black
                    val tileU = (cubelet.x * geometry.right.x + cubelet.y * geometry.right.y + cubelet.z * geometry.right.z).toInt()
                    val tileV = (cubelet.x * geometry.up.x + cubelet.y * geometry.up.y + cubelet.z * geometry.up.z).toInt()
                    val innerRadius = size.minDimension * 0.092f
                    val outerRadius = size.minDimension * 0.006f
                    val cornerRadii = if (!stickerless) null else when {
                        isCenterPiece -> List(4) { size.minDimension * 0.205f }
                        tileU == 0 && tileV > 0 -> listOf(outerRadius, outerRadius, innerRadius, innerRadius)
                        tileU == 0 && tileV < 0 -> listOf(innerRadius, innerRadius, outerRadius, outerRadius)
                        tileV == 0 && tileU > 0 -> listOf(innerRadius, outerRadius, outerRadius, innerRadius)
                        tileV == 0 && tileU < 0 -> listOf(outerRadius, innerRadius, innerRadius, outerRadius)
                        tileU > 0 && tileV > 0 -> listOf(outerRadius, outerRadius, outerRadius, innerRadius)
                        tileU > 0 && tileV < 0 -> listOf(innerRadius, outerRadius, outerRadius, outerRadius)
                        tileU < 0 && tileV > 0 -> listOf(outerRadius, outerRadius, innerRadius, outerRadius)
                        else -> listOf(outerRadius, innerRadius, outerRadius, outerRadius)
                    }
                    renderedFaces += RenderFace(
                        stickerPoints,
                        shadedColor,
                        rotate(faceCenter).z + 0.002f,
                        size.minDimension * if (stickerless) 0.014f else 0.018f,
                        stickerOutline,
                        size.minDimension * if (stickerless) 0.0012f else 0.006f,
                        cornerRadii,
                        centerRecess = stickerless && isCenterPiece
                    )
                }
            }
        }
        renderedFaces.sortedBy { it.depth }.forEach { face ->
            drawRoundedQuad(
                face.points,
                face.color,
                face.cornerRadius,
                face.outlineColor,
                face.outlineWidth,
                face.cornerRadii,
                highlightEdges = stickerless && face.cornerRadii != null && !face.centerRecess,
                centerRecess = face.centerRecess
            )
        }
    }
}

private fun facePoint(geometry: CubeFaceGeometry, horizontal: Float, vertical: Float, depth: Float = 1.008f) = CubePoint3D(
    geometry.normal.x * depth + geometry.right.x * horizontal + geometry.up.x * vertical,
    geometry.normal.y * depth + geometry.right.y * horizontal + geometry.up.y * vertical,
    geometry.normal.z * depth + geometry.right.z * horizontal + geometry.up.z * vertical
)

private fun isInTurningLayer(point: CubePoint3D, face: CubeFace): Boolean {
    val outward = when (face) {
        CubeFace.U -> point.y
        CubeFace.D -> -point.y
        CubeFace.R -> point.x
        CubeFace.L -> -point.x
        CubeFace.F -> point.z
        CubeFace.B -> -point.z
    }
    return outward > 0.5f
}

private fun CubePoint3D.toCubeletPosition() = CubeletPosition(
    (x * 1.5f).toInt().coerceIn(-1, 1),
    (y * 1.5f).toInt().coerceIn(-1, 1),
    (z * 1.5f).toInt().coerceIn(-1, 1)
)

private fun rotateTurningLayerPoint(point: CubePoint3D, move: CubeMove, progress: Float): CubePoint3D {
    val axis = when (move.face) {
        CubeFace.U -> CubePoint3D(0f, 1f, 0f)
        CubeFace.D -> CubePoint3D(0f, -1f, 0f)
        CubeFace.R -> CubePoint3D(1f, 0f, 0f)
        CubeFace.L -> CubePoint3D(-1f, 0f, 0f)
        CubeFace.F -> CubePoint3D(0f, 0f, 1f)
        CubeFace.B -> CubePoint3D(0f, 0f, -1f)
    }
    val amount = when (move.amount) {
        2 -> 2f
        -1, 3 -> -1f
        else -> 1f
    }
    val angle = -amount * Math.PI.toFloat() * 0.5f * progress
    val cosine = kotlin.math.cos(angle.toDouble()).toFloat()
    val sine = kotlin.math.sin(angle.toDouble()).toFloat()
    val dot = axis.x * point.x + axis.y * point.y + axis.z * point.z
    val cross = CubePoint3D(
        axis.y * point.z - axis.z * point.y,
        axis.z * point.x - axis.x * point.z,
        axis.x * point.y - axis.y * point.x
    )
    return CubePoint3D(
        point.x * cosine + cross.x * sine + axis.x * dot * (1f - cosine),
        point.y * cosine + cross.y * sine + axis.y * dot * (1f - cosine),
        point.z * cosine + cross.z * sine + axis.z * dot * (1f - cosine)
    )
}

private fun DrawScope.drawRoundedQuad(
    points: List<Offset>,
    color: Color,
    cornerRadius: Float,
    outlineColor: Color,
    outlineWidth: Float,
    cornerRadii: List<Float>? = null,
    highlightEdges: Boolean = false,
    centerRecess: Boolean = false
) {
    if (centerRecess) {
        // Fill only the four pockets outside the rounded center cap. This keeps
        // its edge in the center's color instead of drawing a black halo around it.
        val radii = cornerRadii ?: List(points.size) { cornerRadius }
        points.indices.forEach { index ->
            val vertex = points[index]
            val previous = points[(index - 1 + points.size) % points.size]
            val next = points[(index + 1) % points.size]
            val radius = radii[index]
            fun towardCorner(target: Offset): Offset {
                val dx = target.x - vertex.x
                val dy = target.y - vertex.y
                val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val fraction = (radius / length).coerceAtMost(0.35f)
                return Offset(vertex.x + dx * fraction, vertex.y + dy * fraction)
            }
            val pocket = Path().apply {
                moveTo(vertex.x, vertex.y)
                val before = towardCorner(previous)
                lineTo(before.x, before.y)
                val after = towardCorner(next)
                quadraticTo(vertex.x, vertex.y, after.x, after.y)
                close()
            }
            drawPath(pocket, Color.Black)
        }
    }
    val path = roundedQuadPath(points, cornerRadii ?: List(points.size) { cornerRadius })
    drawPath(path, color)
    drawPath(path, outlineColor, style = Stroke(width = maxOf(0.7f, outlineWidth)))
    if (highlightEdges && points.size == 4) {
        val highlight = Color(
            color.red + (1f - color.red) * 0.24f,
            color.green + (1f - color.green) * 0.24f,
            color.blue + (1f - color.blue) * 0.24f,
            0.72f
        )
        val shadow = Color(color.red * 0.70f, color.green * 0.70f, color.blue * 0.70f, 0.42f)
        val width = maxOf(0.8f, outlineWidth * 1.2f)
        fun insetEdge(start: Offset, end: Offset, shade: Color) {
            val from = Offset(start.x * 0.36f + end.x * 0.64f, start.y * 0.36f + end.y * 0.64f)
            val to = Offset(start.x * 0.64f + end.x * 0.36f, start.y * 0.64f + end.y * 0.36f)
            drawLine(shade, from, to, strokeWidth = width)
        }
        insetEdge(points[0], points[1], highlight)
        insetEdge(points[0], points[3], highlight)
        insetEdge(points[1], points[2], shadow)
        insetEdge(points[3], points[2], shadow)
    }
}

private fun roundedQuadPath(points: List<Offset>, cornerRadii: List<Float>): Path {
    fun distance(a: Offset, b: Offset) = kotlin.math.sqrt(
        (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    )
    fun toward(from: Offset, to: Offset, radius: Float): Offset {
        val length = distance(from, to).coerceAtLeast(0.001f)
        val fraction = (radius / length).coerceAtMost(0.35f)
        return Offset(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction)
    }

    return Path().apply {
        val before = points.indices.map { index ->
            toward(points[index], points[(index - 1 + points.size) % points.size], cornerRadii[index])
        }
        val after = points.indices.map { index ->
            toward(points[index], points[(index + 1) % points.size], cornerRadii[index])
        }
        moveTo(before[0].x, before[0].y)
        for (index in points.indices) {
            quadraticTo(points[index].x, points[index].y, after[index].x, after[index].y)
            val next = (index + 1) % points.size
            lineTo(before[next].x, before[next].y)
        }
        close()
    }
}

