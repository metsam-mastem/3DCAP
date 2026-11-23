package org.three_dcap.capture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CaptureCoreTest {
    @Test
    fun plannerIsDeterministicAndCoverageIncreases() {
        val planner = SphericalCoveragePlanner(128)
        val first = assertNotNull(planner.nextShot(emptyList()))
        val second = assertNotNull(planner.nextShot(listOf(first)))
        assertTrue(first != second)
        assertTrue(planner.snapshot(listOf(first, second)).estimatedCoveragePercent > planner.snapshot(emptyList()).estimatedCoveragePercent)
        assertEquals(second, planner.nextShot(listOf(first)))
    }

    @Test
    fun manifestRoundTripsAndRejectsPathTraversal() {
        val session = CaptureSession(
            sessionId = "capture-001",
            objectLabel = "sample object",
            createdAt = "2026-11-23T10:00:00Z",
            shots = listOf(CaptureShot(0, 0.0, 15.0, 0.0, "images/shot-0001.jpg", "a".repeat(64), 1234, "2026-11-23T10:00:01Z"))
        )
        assertEquals(session, CaptureSessionCodec.decode(CaptureSessionCodec.encode(session)))
        assertFailsWith<CaptureContractException> {
            CaptureSessionCodec.encode(session.copy(shots = session.shots.map { it.copy(relativeImagePath = "../outside.jpg") }))
        }
    }

    @Test
    fun intakeHashesPngWithoutLoadingWholeFile() {
        val file = Files.createTempFile("3dcap-", ".png")
        try {
            val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0)
            Files.write(file, bytes)
            val asset = AssetIntake.inspect(file)
            assertEquals("png", asset.format)
            assertEquals(bytes.size.toLong(), asset.byteSize)
            assertEquals(64, asset.sha256.length)
        } finally { Files.deleteIfExists(file) }
    }

    @Test
    fun intakeRejectsExtensionSignatureMismatch() {
        val file = Files.createTempFile("3dcap-", ".jpg")
        try {
            Files.write(file, byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            assertFailsWith<AssetIntakeException> { AssetIntake.inspect(file) }
        } finally { Files.deleteIfExists(file) }
    }
}
