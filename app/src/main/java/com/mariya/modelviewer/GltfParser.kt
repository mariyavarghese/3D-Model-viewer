package com.mariya.modelviewer

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GltfParser {

    data class LabelInfo(
        val nodeIndex: Int,
        val name: String,
        val text: String,
        val translation: FloatArray = floatArrayOf(0f, 0f, 0f)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LabelInfo
            if (nodeIndex != other.nodeIndex) return false
            if (name != other.name) return false
            if (text != other.text) return false
            if (!translation.contentEquals(other.translation)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = nodeIndex
            result = 31 * result + name.hashCode()
            result = 31 * result + text.hashCode()
            result = 31 * result + translation.contentHashCode()
            return result
        }
    }

    /**
     * Parses the binary glTF (GLB) header and reads the JSON chunk to discover
     * all nodes containing an `extras.prop` label string.
     */
    fun parseLabels(buffer: ByteBuffer): List<LabelInfo> {
        val labels = mutableListOf<LabelInfo>()
        val originalPos = buffer.position()
        try {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(0)

            if (buffer.remaining() < 12) return labels
            val magic = buffer.int
            if (magic != 0x46546C67) return labels // 'glTF' (0x46546C67)
            val version = buffer.int
            val length = buffer.int

            if (buffer.remaining() < 8) return labels
            val chunk0Len = buffer.int
            val chunk0Type = buffer.int
            if (chunk0Type != 0x4E4F534A) return labels // 'JSON' (0x4E4F534A)

            if (buffer.remaining() < chunk0Len) return labels
            val jsonBytes = ByteArray(chunk0Len)
            buffer.get(jsonBytes)
            val jsonString = String(jsonBytes, Charsets.UTF_8)

            val root = JSONObject(jsonString)
            if (root.has("nodes")) {
                val nodes = root.getJSONArray("nodes")
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    if (node.has("extras")) {
                        val extras = node.getJSONObject("extras")
                        if (extras.has("prop")) {
                            val text = extras.getString("prop")
                            val name = if (node.has("name")) node.getString("name") else "Node $i"
                            val translation = floatArrayOf(0f, 0f, 0f)
                            if (node.has("translation")) {
                                val transArr = node.getJSONArray("translation")
                                if (transArr.length() >= 3) {
                                    translation[0] = transArr.getDouble(0).toFloat()
                                    translation[1] = transArr.getDouble(1).toFloat()
                                    translation[2] = transArr.getDouble(2).toFloat()
                                }
                            }
                            labels.add(LabelInfo(i, name, text, translation))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            buffer.position(originalPos)
        }
        return labels
    }
}
