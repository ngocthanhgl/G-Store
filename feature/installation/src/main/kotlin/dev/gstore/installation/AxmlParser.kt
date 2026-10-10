package dev.gstore.installation

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Android binary-XML (AXML) reader — just enough to extract
 * `<manifest>` / `<uses-sdk>` attributes. Per AOSP `ResourceTypes.h`.
 *
 * Never throws: any inconsistency yields null (unknown manifest facts).
 * Bounds-checked on every read so truncated/hostile blobs only produce null.
 */
object AxmlParser {
    private const val CHUNK_XML = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RESOURCE_MAP = 0x0180
    private const val CHUNK_NS_START = 0x0100
    private const val CHUNK_NS_END = 0x0101
    private const val CHUNK_ELEMENT_START = 0x0102
    private const val CHUNK_ELEMENT_END = 0x0103
    const val UTF8_FLAG = 1 shl 8
    private const val TYPE_STRING = 0x03

    fun parse(bytes: ByteArray): ApkParser.ManifestInfo? {
        return try {
            parseOrThrow(bytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOrThrow(bytes: ByteArray): ApkParser.ManifestInfo? {
        if (bytes.size < 8) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = u16(buf, 0) ?: return null
        if (type != CHUNK_XML) return null
        val totalSize = u32(buf, 4) ?: return null
        val end = totalSize.coerceAtMost(bytes.size.toLong()).toInt()
        var pos = 8
        var pool: List<String> = emptyList()
        var packageName: String? = null
        var versionCode: Long? = null
        var versionName: String? = null
        var minSdk: Int? = null
        var elementStack = ArrayDeque<String>()
        while (pos + 8 <= end) {
            val chunkType = u16(buf, pos) ?: break
            val chunkSize = (u32(buf, pos + 4) ?: break).toInt()
            if (chunkSize < 8 || pos + chunkSize > end) break
            when (chunkType) {
                CHUNK_STRING_POOL -> pool = readPool(buf, pos, chunkSize) ?: emptyList()
                CHUNK_ELEMENT_START -> {
                    val nameIdx = u32(buf, pos + 20)
                    val attrStart = u16(buf, pos + 24)
                    val attrSize = u16(buf, pos + 26)
                    val attrCount = u16(buf, pos + 28)
                    val name = nameIdx?.let { pool.getOrNull(it.toInt()) }
                    if (name != null) {
                        elementStack.addLast(name)
                        if (attrStart != null && attrSize != null && attrCount != null &&
                            attrSize >= 20
                        ) {
                            // attributeStart counts from ResXMLTree_attrExt, which
                            // starts after the 16-byte node header (lineNumber+comment).
                            val attrsBase = pos + 16 + attrStart
                            for (i in 0 until attrCount) {
                                val a = attrsBase + i * attrSize
                                if (a + 20 > pos + chunkSize) break
                                val attrName = u32(buf, a + 4)?.toInt()
                                    ?.let { pool.getOrNull(it) } ?: continue
                                // Res_value layout: size u16 @+12, res0 u8 @+14,
                                // dataType u8 @+15, data u32 @+16.
                                val dataType = u8(buf, a + 15) ?: continue
                                val data = u32(buf, a + 16) ?: continue
                                val stringVal = if (dataType == TYPE_STRING) {
                                    pool.getOrNull(data.toInt())
                                } else {
                                    null
                                }
                                when {
                                    name == "manifest" && attrName == "package" ->
                                        packageName = stringVal
                                    name == "manifest" && attrName == "versionCode" ->
                                        versionCode = stringVal?.toLongOrNull()
                                            ?: if (dataType in 0x10..0x1F) data.toLong() else null
                                    name == "manifest" && attrName == "versionName" ->
                                        versionName = stringVal
                                    name == "uses-sdk" && attrName == "minSdkVersion" ->
                                        minSdk = stringVal?.toIntOrNull()
                                            ?: if (dataType in 0x10..0x1F) data.toInt() else null
                                }
                            }
                        }
                    }
                }
                CHUNK_ELEMENT_END -> if (elementStack.isNotEmpty()) elementStack.removeLast()
            }
            if (chunkType != CHUNK_STRING_POOL && chunkType != CHUNK_RESOURCE_MAP &&
                chunkType != CHUNK_NS_START && chunkType != CHUNK_NS_END &&
                chunkType != CHUNK_ELEMENT_START && chunkType != CHUNK_ELEMENT_END
            ) {
                // Unknown chunk (e.g. CDATA) — skip by size.
            }
            pos += chunkSize
        }
        if (packageName == null && versionCode == null && versionName == null && minSdk == null) {
            return null
        }
        return ApkParser.ManifestInfo(packageName, versionCode, versionName, minSdk)
    }

    private fun readPool(buf: ByteBuffer, pos: Int, chunkSize: Int): List<String>? {
        val count = u32(buf, pos + 8)?.toInt() ?: return null
        val flags = u32(buf, pos + 16) ?: return null
        val stringsStart = u32(buf, pos + 20)?.toInt() ?: return null
        if (count < 0 || count > 100_000) return null
        if (pos + 28 + count * 4 > pos + chunkSize) return null
        val utf8 = (flags.toInt() and UTF8_FLAG) != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = u32(buf, pos + 28 + i * 4)?.toInt() ?: return null
            val abs = pos + stringsStart + off
            out += readString(buf, abs, utf8) ?: return null
        }
        return out
    }

    private fun readString(buf: ByteBuffer, pos: Int, utf8: Boolean): String? {
        return try {
            if (utf8) {
                var p = pos
                val u16len = u16(buf, p) ?: return null; p += 2
                if (u16len and 0x8000 != 0) p += 2 // two-u16 length form
                var byteLen = u8(buf, p) ?: return null; p += 1
                if (byteLen and 0x80 != 0) {
                    val lo = u8(buf, p) ?: return null; p += 1
                    byteLen = ((byteLen and 0x7F) shl 8) or lo
                }
                if (p + byteLen > buf.limit()) return null
                val bytes = ByteArray(byteLen) { buf.get(p + it) }
                String(bytes, Charsets.UTF_8)
            } else {
                var p = pos
                var len = u16(buf, p) ?: return null; p += 2
                if (len and 0x8000 != 0) {
                    val hi = len and 0x7FFF
                    val lo = u16(buf, p) ?: return null; p += 2
                    len = (hi shl 16) or lo
                }
                if (len > 100_000 || p + len * 2 > buf.limit()) return null
                buildString {
                    repeat(len) { append(buf.getChar(p + it * 2)) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun u16(buf: ByteBuffer, pos: Int): Int? {
        if (pos < 0 || pos + 2 > buf.limit()) return null
        return buf.getShort(pos).toInt() and 0xFFFF
    }

    private fun u8(buf: ByteBuffer, pos: Int): Int? {
        if (pos < 0 || pos + 1 > buf.limit()) return null
        return buf.get(pos).toInt() and 0xFF
    }

    private fun u32(buf: ByteBuffer, pos: Int): Long? {
        if (pos < 0 || pos + 4 > buf.limit()) return null
        return buf.getInt(pos).toLong() and 0xFFFFFFFFL
    }
}
