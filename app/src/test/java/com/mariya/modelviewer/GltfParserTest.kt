package com.mariya.modelviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class GltfParserTest {

    @Test
    fun testParseBulb() {
        val file = File("src/main/assets/models/Bulb.glb")
        val bytes = file.readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.position(0)

        val labels = GltfParser.parseLabels(buffer)
        println("Bulb labels: $labels")
        assertEquals(6, labels.size)
        assertTrue(labels.any { it.text == "Filament" })
        assertTrue(labels.any { it.text == "Glass Bulb" })
    }

    @Test
    fun testParseAllModels() {
        val models = listOf("Bulb.glb", "Fiagena.glb", "Lungs.glb", "Microscope.glb", "solarsystem.glb")
        for (modelName in models) {
            val file = File("src/main/assets/models/$modelName")
            assertTrue("File exists: $modelName", file.exists())
            val bytes = file.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size)
            buffer.put(bytes)
            buffer.position(0)

            val labels = GltfParser.parseLabels(buffer)
            println("$modelName has ${labels.size} labels:")
            for (l in labels) {
                println("  Node ${l.nodeIndex} (${l.name}): ${l.text}")
            }
            assertTrue("$modelName has labels", labels.isNotEmpty())
        }
    }
}
