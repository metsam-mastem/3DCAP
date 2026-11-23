package studio.threedcap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureManifestTest {
    private fun valid() = CaptureManifest(
        sessionId = "capture-123",
        createdAt = "2026-11-23T11:00:00Z",
        frames = listOf(
            CapturedFrameRecord(
                sequence = 0,
                fileName = "3dcap_20261123_110001_000.jpg",
                relativePath = "3dcap_20261123_110001_000.jpg",
                yawDegrees = 30f,
                pitchDegrees = 15f,
                byteSize = 1024,
                sha256 = "a".repeat(64),
                capturedAt = "2026-11-23T11:00:01Z"
            )
        )
    )

    @Test fun acceptsValidManifest() {
        assertEquals(1, CaptureManifestContract.validate(valid()).frames.size)
    }

    @Test fun rejectsTraversalAndUnexpectedPath() {
        val invalid = valid().copy(frames = valid().frames.map { it.copy(relativePath = "../outside.jpg") })
        assertThrows(IllegalArgumentException::class.java) { CaptureManifestContract.validate(invalid) }
    }

    @Test fun rejectsDuplicateSequenceAndInvalidDigest() {
        val frame = valid().frames.single()
        val duplicate = valid().copy(frames = listOf(frame, frame.copy(fileName = "3dcap_20261123_110002_000.jpg", relativePath = "3dcap_20261123_110002_000.jpg")))
        assertThrows(IllegalArgumentException::class.java) { CaptureManifestContract.validate(duplicate) }
        val badDigest = valid().copy(frames = listOf(frame.copy(sha256 = "not-a-hash")))
        assertThrows(IllegalArgumentException::class.java) { CaptureManifestContract.validate(badDigest) }
    }
}
