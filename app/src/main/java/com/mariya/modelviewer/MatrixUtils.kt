package com.mariya.modelviewer

/**
 * High-performance, zero-allocation 4x4 column-major matrix and vector math utilities.
 * Independent of Android OpenGL stubs for full testability and performance.
 */
object MatrixUtils {

    /**
     * Multiplies two 4x4 column-major matrices: result = lhs * rhs
     */
    fun multiplyMM(result: FloatArray, lhs: FloatArray, rhs: FloatArray) {
        for (col in 0 until 4) {
            val c = col * 4
            val rhs0 = rhs[c + 0]
            val rhs1 = rhs[c + 1]
            val rhs2 = rhs[c + 2]
            val rhs3 = rhs[c + 3]

            result[c + 0] = lhs[0] * rhs0 + lhs[4] * rhs1 + lhs[8] * rhs2 + lhs[12] * rhs3
            result[c + 1] = lhs[1] * rhs0 + lhs[5] * rhs1 + lhs[9] * rhs2 + lhs[13] * rhs3
            result[c + 2] = lhs[2] * rhs0 + lhs[6] * rhs1 + lhs[10] * rhs2 + lhs[14] * rhs3
            result[c + 3] = lhs[3] * rhs0 + lhs[7] * rhs1 + lhs[11] * rhs2 + lhs[15] * rhs3
        }
    }

    /**
     * Multiplies a 4x4 column-major matrix by a 4D vector: resultVec = lhsMat * rhsVec
     */
    fun multiplyMV(resultVec: FloatArray, lhsMat: FloatArray, rhsVec: FloatArray) {
        val x = rhsVec[0]
        val y = rhsVec[1]
        val z = rhsVec[2]
        val w = rhsVec[3]

        resultVec[0] = lhsMat[0] * x + lhsMat[4] * y + lhsMat[8] * z + lhsMat[12] * w
        resultVec[1] = lhsMat[1] * x + lhsMat[5] * y + lhsMat[9] * z + lhsMat[13] * w
        resultVec[2] = lhsMat[2] * x + lhsMat[6] * y + lhsMat[10] * z + lhsMat[14] * w
        resultVec[3] = lhsMat[3] * x + lhsMat[7] * y + lhsMat[11] * z + lhsMat[15] * w
    }

    /**
     * Projects a 3D world coordinate (worldX, worldY, worldZ) onto 2D screen coordinates
     * using the combined View-Projection matrix and viewport dimensions.
     *
     * Returns true if point is in front of the camera and within reasonable view bounds.
     * Output screen coordinates are written to outScreen[0] = x, outScreen[1] = y.
     */
    fun projectWorldToScreen(
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        viewProjMatrix: FloatArray,
        viewportWidth: Float,
        viewportHeight: Float,
        clipPosBuffer: FloatArray,
        worldPosBuffer: FloatArray,
        outScreen: FloatArray
    ): Boolean {
        worldPosBuffer[0] = worldX
        worldPosBuffer[1] = worldY
        worldPosBuffer[2] = worldZ
        worldPosBuffer[3] = 1.0f

        multiplyMV(clipPosBuffer, viewProjMatrix, worldPosBuffer)

        val clipW = clipPosBuffer[3]
        if (clipW <= 0.001f) {
            return false
        }

        val ndcX = clipPosBuffer[0] / clipW
        val ndcY = clipPosBuffer[1] / clipW
        val ndcZ = clipPosBuffer[2] / clipW

        // Check if within visible frustum
        if (ndcX < -1.4f || ndcX > 1.4f || ndcY < -1.4f || ndcY > 1.4f || ndcZ < -1.0f || ndcZ > 1.0f) {
            return false
        }

        // Convert NDC to Android 2D View coordinates (origin top-left)
        outScreen[0] = (ndcX + 1.0f) * 0.5f * viewportWidth
        outScreen[1] = (1.0f - ndcY) * 0.5f * viewportHeight
        return true
    }
}
