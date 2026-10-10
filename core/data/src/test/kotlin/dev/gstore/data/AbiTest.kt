package dev.gstore.data

import dev.gstore.model.AbiConfidence
import dev.gstore.model.AbiType
import org.junit.Assert.assertEquals
import org.junit.Test

class AbiTest {

    @Test fun `arm64 names win over arm32 substrings`() {
        assertEquals(
            AbiVerdict(AbiType.ARM64_V8A, AbiConfidence.MEDIUM),
            detectAbi("app-arm64-v8a.apk", null),
        )
        assertEquals(
            AbiVerdict(AbiType.ARM64_V8A, AbiConfidence.MEDIUM),
            detectAbi("app-aarch64.apk", null),
        )
    }

    @Test fun `32-bit arm detected`() {
        assertEquals(
            AbiVerdict(AbiType.ARMEABI_V7A, AbiConfidence.MEDIUM),
            detectAbi("app-armeabi-v7a.apk", null),
        )
    }

    @Test fun `x86_64 not confused with x86`() {
        assertEquals(
            AbiVerdict(AbiType.X86_64, AbiConfidence.MEDIUM),
            detectAbi("app-x86_64.apk", null),
        )
        assertEquals(
            AbiVerdict(AbiType.X86, AbiConfidence.MEDIUM),
            detectAbi("app-x86.apk", null),
        )
    }

    @Test fun `conflicting abi names report unknown`() {
        assertEquals(
            AbiVerdict(AbiType.UNKNOWN, AbiConfidence.LOW),
            detectAbi("app-arm64-x86.apk", null),
        )
    }

    @Test fun `universal marker is a packaging label`() {
        assertEquals(
            AbiVerdict(AbiType.UNIVERSAL, AbiConfidence.MEDIUM),
            detectAbi("app-universal.apk", null),
        )
        assertEquals(
            AbiVerdict(AbiType.UNIVERSAL, AbiConfidence.MEDIUM),
            detectAbi("app-all.apk", null),
        )
    }

    @Test fun `noarch never trusted`() {
        assertEquals(
            AbiVerdict(AbiType.UNKNOWN, AbiConfidence.UNKNOWN),
            detectAbi("app-noarch.apk", null),
        )
    }

    @Test fun `plain name means unknown not universal`() {
        assertEquals(
            AbiVerdict(AbiType.UNKNOWN, AbiConfidence.UNKNOWN),
            detectAbi("app-release.apk", null),
        )
    }

    @Test fun `asset label participates in detection`() {
        assertEquals(
            AbiVerdict(AbiType.ARM64_V8A, AbiConfidence.MEDIUM),
            detectAbi("download.apk", "arm64-v8a build"),
        )
    }

    @Test fun `bundles never claim an abi`() {
        val artifact = artifactOf(1L, "app-arm64.apks", null, AssetKind.BUNDLE)
        assertEquals(AbiType.UNKNOWN, artifact.abi)
        assertEquals(AbiConfidence.UNKNOWN, artifact.confidence)
        assertEquals(true, artifact.isSplitBundle)
    }

    @Test fun `apk artifact carries verdict`() {
        val artifact = artifactOf(2L, "app-x86_64.apk", null, AssetKind.APK)
        assertEquals(AbiType.X86_64, artifact.abi)
        assertEquals(AbiConfidence.MEDIUM, artifact.confidence)
        assertEquals(null, artifact.minSdk)
    }
}
