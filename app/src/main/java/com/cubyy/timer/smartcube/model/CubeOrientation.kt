package com.cubyy.timer.smartcube.model

import kotlin.math.sqrt

data class CubeOrientation(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
) {
    fun normalized(): CubeOrientation {
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length <= 0.00001f) return IDENTITY
        return CubeOrientation(x / length, y / length, z / length, w / length)
    }

    fun inverse(): CubeOrientation {
        val unit = normalized()
        return CubeOrientation(-unit.x, -unit.y, -unit.z, unit.w)
    }

    operator fun times(other: CubeOrientation): CubeOrientation = CubeOrientation(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z
    ).normalized()

    fun rotate(vector: FloatArray): FloatArray {
        val unit = normalized()
        val qVector = floatArrayOf(unit.x, unit.y, unit.z)
        val cross1 = cross(qVector, vector)
        val cross2 = cross(qVector, cross1)
        return floatArrayOf(
            vector[0] + 2f * (unit.w * cross1[0] + cross2[0]),
            vector[1] + 2f * (unit.w * cross1[1] + cross2[1]),
            vector[2] + 2f * (unit.w * cross1[2] + cross2[2])
        )
    }

    fun slerp(target: CubeOrientation, fraction: Float): CubeOrientation {
        val start = normalized()
        var end = target.normalized()
        var dot = start.x * end.x + start.y * end.y + start.z * end.z + start.w * end.w
        if (dot < 0f) {
            end = CubeOrientation(-end.x, -end.y, -end.z, -end.w)
            dot = -dot
        }
        val t = fraction.coerceIn(0f, 1f)
        if (dot > 0.9995f) {
            return CubeOrientation(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t,
                start.z + (end.z - start.z) * t,
                start.w + (end.w - start.w) * t
            ).normalized()
        }
        val theta = kotlin.math.acos(dot.coerceIn(-1f, 1f))
        val sinTheta = kotlin.math.sin(theta)
        val startWeight = kotlin.math.sin((1f - t) * theta) / sinTheta
        val endWeight = kotlin.math.sin(t * theta) / sinTheta
        return CubeOrientation(
            start.x * startWeight + end.x * endWeight,
            start.y * startWeight + end.y * endWeight,
            start.z * startWeight + end.z * endWeight,
            start.w * startWeight + end.w * endWeight
        ).normalized()
    }

    companion object {
        val IDENTITY = CubeOrientation(0f, 0f, 0f, 1f)

        private fun cross(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
    }
}
