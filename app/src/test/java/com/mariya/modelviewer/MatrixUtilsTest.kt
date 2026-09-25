package com.mariya.modelviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixUtilsTest {

    @Test
    fun testMultiplyIdentity() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        val vec = floatArrayOf(2f, 3f, 4f, 1f)
        val result = FloatArray(4)

        MatrixUtils.multiplyMV(result, identity, vec)

        assertEquals(2f, result[0], 0.001f)
        assertEquals(3f, result[1], 0.001f)
        assertEquals(4f, result[2], 0.001f)
        assertEquals(1f, result[3], 0.001f)
    }

    @Test
    fun testProjectWorldToScreenCenter() {
        // Perspective-like matrix with camera at (0, 0, 4) looking at origin
        // Clip pos for (0, 0, 0) gives NDC (0, 0)
        val viewProj = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, -1f,
            0f, 0f, -4f, 4f
        )
        val clipPos = FloatArray(4)
        val worldPos = FloatArray(4)
        val outScreen = FloatArray(2)

        val visible = MatrixUtils.projectWorldToScreen(
            0f, 0f, 0f,
            viewProj,
            600f,
            600f,
            clipPos,
            worldPos,
            outScreen
        )

        assertTrue(visible)
        assertEquals(300f, outScreen[0], 0.01f) // Center X
        assertEquals(300f, outScreen[1], 0.01f) // Center Y
    }

    @Test
    fun testBehindCameraNotVisible() {
        val viewProj = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, -1f,
            0f, 0f, 0f, -1f // Negative W means behind camera
        )
        val clipPos = FloatArray(4)
        val worldPos = FloatArray(4)
        val outScreen = FloatArray(2)

        val visible = MatrixUtils.projectWorldToScreen(
            0f, 0f, 0f,
            viewProj,
            600f,
            600f,
            clipPos,
            worldPos,
            outScreen
        )

        assertFalse(visible)
    }
}
