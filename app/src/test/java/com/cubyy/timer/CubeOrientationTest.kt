package com.cubyy.timer

import com.cubyy.timer.smartcube.model.CubeOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class CubeOrientationTest {

    @Test
    fun orientationMultipliedByInverseIsIdentity() {
        val orientation = CubeOrientation(0.2f, -0.3f, 0.1f, 0.9f).normalized()
        val identity = orientation * orientation.inverse()

        assertEquals(0f, identity.x, 0.0001f)
        assertEquals(0f, identity.y, 0.0001f)
        assertEquals(0f, identity.z, 0.0001f)
        assertEquals(1f, identity.w, 0.0001f)
    }

    @Test
    fun identityLeavesCubeAxesUnchanged() {
        val vector = CubeOrientation.IDENTITY.rotate(floatArrayOf(0f, 1f, 0f))

        assertEquals(0f, vector[0], 0.0001f)
        assertEquals(1f, vector[1], 0.0001f)
        assertEquals(0f, vector[2], 0.0001f)
    }

    @Test
    fun sphericalInterpolationPreservesEndpointsAndUnitLength() {
        val target = CubeOrientation(0f, 0.70710677f, 0f, 0.70710677f)

        val start = CubeOrientation.IDENTITY.slerp(target, 0f)
        val end = CubeOrientation.IDENTITY.slerp(target, 1f)
        assertEquals(CubeOrientation.IDENTITY.x, start.x, 0.0001f)
        assertEquals(CubeOrientation.IDENTITY.w, start.w, 0.0001f)
        assertEquals(target.y, end.y, 0.0001f)
        assertEquals(target.w, end.w, 0.0001f)
        val middle = CubeOrientation.IDENTITY.slerp(target, 0.5f)
        val length = kotlin.math.sqrt(middle.x * middle.x + middle.y * middle.y + middle.z * middle.z + middle.w * middle.w)
        assertEquals(1f, length, 0.0001f)
    }
}
