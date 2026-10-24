package dev.forkly.installation

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-side minimal AXML writer (AOSP binary-XML layout, UTF-8 string pool).
 * Shares the format spec with [AxmlParser], not its code — mirrored-bug risk
 * is accepted and documented; malformed-input tests guard the parser's real
 * value (never throw, never invent facts).
 */
private object AxmlWriter {
    fun manifest(
        packageName: String,
        versionCode: Long,
        versionName: String,
        minSdk: Int,
    ): ByteArray {
        val strings = listOf(
            "manifest", "package", packageName,
            "versionCode", "versionName", versionName,
            "uses-sdk", "minSdkVersion", "android",
        )
        val pool = stringPool(strings)
        val out = ByteArrayOutputStream()
        val total = ByteArrayOutputStream()
        total.write(pool)
        // Empty resource-map chunk (exercises the parser's generic skip).
        total.write(chunk(0x0180, ByteArray(0)))
        total.write(ns(true, uri = -1))
        total.write(
            elementStart(
                name = 0, // "manifest"
                attrs = listOf(
                    Attr(1, 8, 0x03, strings.indexOf(packageName)), // package: string
                    Attr(3, 8, 0x12, versionCode.toInt()), // versionCode: int32
                    Attr(4, 8, 0x03, strings.indexOf(versionName)), // versionName: string
                ),
            ),
        )
        total.write(
            elementStart(
                name = 6, // "uses-sdk"
                attrs = listOf(Attr(7, 8, 0x12, minSdk)), // minSdkVersion: int32
            ),
        )
        total.write(elementEnd(6))
        total.write(elementEnd(0))
        total.write(ns(false, uri = -1))
        val body = total.toByteArray()
        out.write(u16(0x0003))
        out.write(u16(8))
        out.write(u32(8 + body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun stringPool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        for (s in strings) {
            offsets += data.size()
            val bytes = s.toByteArray(Charsets.UTF_8)
            data.write(u16(s.length))
            if (bytes.size < 0x80) {
                data.write(bytes.size)
            } else {
                data.write(0x80 or (bytes.size shr 8))
                data.write(bytes.size and 0xFF)
            }
            data.write(bytes)
            data.write(0)
        }
        while (data.size() % 4 != 0) data.write(0)
        val out = ByteArrayOutputStream()
        out.write(u16(0x0001))
        out.write(u16(28)) // headerSize
        val stringsStart = 28 + strings.size * 4
        out.write(u32(8 + 20 + strings.size * 4 + data.size()))
        out.write(u32(strings.size))
        out.write(u32(0)) // styleCount
        out.write(u32(AxmlParser.UTF8_FLAG))
        out.write(u32(stringsStart))
        out.write(u32(0)) // stylesStart
        offsets.forEach { out.write(u32(it)) }
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    private fun chunk(type: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u16(type))
        out.write(u16(8))
        out.write(u32(8 + body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun ns(start: Boolean, uri: Int): ByteArray {
        val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        body.putInt(100) // lineNumber
        body.putInt(-1) // comment
        body.putInt(-1) // prefix
        body.putInt(uri)
        return chunk(if (start) 0x0100 else 0x0101, body.array())
    }

    /** attrs: (nameIdx, size, dataType, data). */
    private fun elementStart(name: Int, attrs: List<Attr>): ByteArray {
        val body = ByteArrayOutputStream()
        val head = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(200) // lineNumber
        head.putInt(-1) // comment
        head.putInt(-1) // ns
        head.putInt(name)
        head.putShort(20) // attributeStart (from attrExt)
        head.putShort(20) // attributeSize
        head.putShort(attrs.size.toShort())
        head.putShort(-1) // idIndex
        head.putShort(-1) // classIndex
        head.putShort(-1) // styleIndex
        body.write(head.array())
        for (attr in attrs) {
            val buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(-1) // ns
            buf.putInt(attr.nameIdx)
            buf.putInt(-1) // rawValue
            buf.putShort(attr.size.toShort()) // size
            buf.put(0) // res0
            buf.put(attr.dataType.toByte()) // dataType
            buf.putInt(attr.data) // data
            body.write(buf.array())
        }
        return chunk(0x0102, body.toByteArray())
    }

    private fun elementEnd(name: Int): ByteArray {
        val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        body.putInt(300)
        body.putInt(-1)
        body.putInt(-1)
        body.putInt(name)
        return chunk(0x0103, body.array())
    }

    private fun u16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun u32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )
}

/** Test-side attribute: (nameIdx, size, dataType, data). */
private data class Attr(val nameIdx: Int, val size: Int, val dataType: Int, val data: Int)

private fun storedEntry(name: String, bytes: ByteArray): Triple<ZipEntry, ByteArray, Boolean> {
    val checker = CRC32()
    checker.update(bytes)
    return Triple(
        ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = checker.value
        },
        bytes,
        true,
    )
}

private fun writeApk(entries: List<Triple<ZipEntry, ByteArray, Boolean>>): File {
    val file = File.createTempFile("forkly-test", ".apk")
    ZipOutputStream(file.outputStream()).use { zip ->
        // Note: the okCrc flag is informational only — ZipOutputStream validates
        // STORED CRCs itself, so post-write corruption (see corruptCrcReported)
        // is the only way to build a CRC mismatch.
        for ((entry, bytes, _) in entries) {
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return file
}

class AxmlParserTest {
    @Test fun roundTripManifestAttributes() {
        val bytes = AxmlWriter.manifest("com.example.app", 42, "1.2.3", 26)
        val info = AxmlParser.parse(bytes)
        assertEquals("com.example.app", info?.packageName)
        assertEquals(42L, info?.versionCode)
        assertEquals("1.2.3", info?.versionName)
        assertEquals(26, info?.minSdk)
    }

    @Test fun garbageIsNullNotThrow() {
        assertNull(AxmlParser.parse("not xml at all".toByteArray()))
        assertNull(AxmlParser.parse(ByteArray(0)))
        assertNull(AxmlParser.parse(ByteArray(64)))
    }

    @Test fun truncatedIsNullNotThrow() {
        val full = AxmlWriter.manifest("com.example.app", 1, "1.0", 24)
        assertNull(AxmlParser.parse(full.copyOf(full.size / 2)))
    }

    @Test fun wrongMagicIsNull() {
        val full = AxmlWriter.manifest("com.example.app", 1, "1.0", 24).copyOf()
        full[0] = 0x04
        assertNull(AxmlParser.parse(full))
    }
}

class ApkParserTest {
    private fun validApk(): File {
        val manifest = AxmlWriter.manifest("com.example.app", 7, "2.0", 26)
        val dex = ByteArray(64) { it.toByte() }
        val so = ByteArray(32) { 0x7F }
        return writeApk(
            listOf(
                storedEntry("AndroidManifest.xml", manifest),
                storedEntry("classes.dex", dex),
                storedEntry("lib/arm64-v8a/libapp.so", so),
                storedEntry("res/drawable/icon.png", ByteArray(16)),
            ),
        )
    }

    @Test fun validApkInspectsClean() {
        val inspection = ApkParser.inspect(validApk())
        assertTrue(inspection.isZip)
        assertTrue(inspection.structurallyValid)
        assertTrue(inspection.hasDex)
        assertEquals("com.example.app", inspection.manifest?.packageName)
        assertEquals(26, inspection.manifest?.minSdk)
        assertEquals(setOf(dev.forkly.model.AbiType.ARM64_V8A), inspection.abis)
        assertEquals(
            dev.forkly.model.AbiConfidence.HIGH,
            ApkParser.confidenceFor(inspection.abis, dev.forkly.model.AbiConfidence.MEDIUM),
        )
    }

    @Test fun missingManifestIsError() {
        val file = writeApk(listOf(storedEntry("classes.dex", ByteArray(16))))
        val inspection = ApkParser.inspect(file)
        assertTrue(inspection.isZip)
        assertFalse(inspection.structurallyValid)
        assertFalse(inspection.hasManifest)
    }

    @Test fun traversalEntriesFlagged() {
        val manifest = AxmlWriter.manifest("com.evil.x", 1, "1.0", 21)
        val file = writeApk(
            listOf(
                storedEntry("AndroidManifest.xml", manifest),
                storedEntry("../evil.dex", ByteArray(8)),
            ),
        )
        val inspection = ApkParser.inspect(file)
        assertEquals(listOf("../evil.dex"), inspection.traversalEntries)
        assertFalse(inspection.structurallyValid)
    }

    @Test fun corruptCrcReported() {
        val manifest = AxmlWriter.manifest("com.example.app", 1, "1.0", 21)
        val dex = ByteArray(64) { it.toByte() }
        // ZipOutputStream self-validates STORED CRCs on closeEntry, so a CRC
        // mismatch can only be built by corrupting the file afterwards: flip one
        // content byte inside the classes.dex local data.
        val raw = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                for ((entry, bytes) in
                    listOf(storedEntry("AndroidManifest.xml", manifest), storedEntry("classes.dex", dex))
                        .map { it.first to it.second }) {
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val dataAt = localDataOffset(raw, "classes.dex")
        raw[dataAt] = (raw[dataAt].toInt() xor 0xFF).toByte()
        val file = File.createTempFile("forkly-test", ".apk")
        file.writeBytes(raw)
        val inspection = ApkParser.inspect(file)
        assertTrue(inspection.crcErrors.any { it.startsWith("classes.dex") })
        assertFalse(inspection.structurallyValid)
    }

    /** Offset of an entry's raw data via its local file header. */
    private fun localDataOffset(zip: ByteArray, name: String): Int {
        var p = 0
        val want = name.toByteArray(Charsets.UTF_8)
        while (p + 30 < zip.size) {
            val sig = ByteBuffer.wrap(zip, p, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (sig != 0x04034b50) {
                p++
                continue
            }
            val buf = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
            val nameLen = buf.getShort(p + 26).toInt() and 0xFFFF
            val extraLen = buf.getShort(p + 28).toInt() and 0xFFFF
            val compSize = buf.getInt(p + 18).toLong() and 0xFFFFFFFFL
            if (p + 30 + nameLen > zip.size) break
            val actual = zip.copyOfRange(p + 30, p + 30 + nameLen)
            if (actual.contentEquals(want)) return p + 30 + nameLen + extraLen
            p += 30 + nameLen + extraLen + compSize.toInt()
        }
        throw AssertionError("entry $name not found")
    }

    @Test fun notAZipIsNotZip() {
        val file = File.createTempFile("forkly-test", ".apk")
        file.writeBytes("definitely not a zip".toByteArray())
        val inspection = ApkParser.inspect(file)
        assertFalse(inspection.isZip)
        assertFalse(inspection.structurallyValid)
    }

    @Test fun noNativeLibsMeansUnknownSupport() {
        val compat = ApkParser.deviceSupport(null, emptySet())
        assertEquals(dev.forkly.model.DeviceSupport.UNKNOWN, compat.deviceSupported)
    }
}
