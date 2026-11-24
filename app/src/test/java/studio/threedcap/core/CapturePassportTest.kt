package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class CapturePassportTest {
    private fun manifest(frames: List<CapturedFrameRecord>) = CaptureManifest(
        sessionId = "capture-passport-test",
        createdAt = "2026-11-24T00:00:00Z",
        frames = frames
    )

    private fun frame(sequence: Int, yaw: Float, pitch: Float = 15f, hash: String? = null,
                      metadata: CameraCaptureMetadata? = CameraCaptureMetadata(imageWidth = 4000, imageHeight = 3000)) =
        CapturedFrameRecord(
            sequence = sequence,
            fileName = "3dcap_20261124_120000_${sequence.toString().padStart(3, '0')}.jpg",
            relativePath = "3dcap_20261124_120000_${sequence.toString().padStart(3, '0')}.jpg",
            yawDegrees = yaw,
            pitchDegrees = pitch,
            byteSize = 1024,
            sha256 = hash ?: sequence.toString(16).padStart(64, 'a').takeLast(64),
            capturedAt = "2026-11-24T00:00:00Z",
            cameraMetadata = metadata
        )

    private fun fullCoverageFrames(): List<CapturedFrameRecord> {
        val pitches = listOf(-25f, 15f, 55f)
        return pitches.flatMap { pitch ->
            (0 until 360 step 30).map { yaw -> frame((pitch.toInt() + 25) * 12 + yaw / 30, yaw.toFloat(), pitch) }
        }
    }

    @Test fun emptySessionIsInsufficientAndCoverageIsZero() {
        val passport = CapturePassportAnalyzer.analyze(manifest(emptyList()), "2026-11-24T01:00:00Z")
        assertEquals(PassportReadiness.INSUFFICIENT, passport.readiness)
        assertEquals(0, passport.coverage.percent)
        assertEquals(36, passport.coverage.totalCells)
    }

    @Test fun reportsPlannerCoverageAndMissingTargetsFromThirtySixCells() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(listOf(frame(0, 0f), frame(1, 30f), frame(2, 60f))),
            "2026-11-24T01:00:00Z"
        )
        assertEquals(3, passport.coverage.coveredCells)
        assertEquals(33, passport.coverage.missingCells.size)
        assertEquals(8, passport.coverage.percent)
    }

    @Test fun reportsPlannerCellDuplicatesSeparatelyFromDuplicateImageContent() {
        val a = frame(0, 0f, hash = "a".repeat(64))
        val b = frame(1, 1f, hash = "b".repeat(64))
        val c = frame(2, 30f, hash = "a".repeat(64))
        val passport = CapturePassportAnalyzer.analyze(manifest(listOf(a, b, c)), "2026-11-24T01:00:00Z")
        assertEquals("WARN", passport.check("planner_cell_duplicates")?.outcome?.name)
        assertEquals("WARN", passport.check("image_content_duplicates")?.outcome?.name)
        assertEquals(1, passport.coverage.duplicateCellCount)
    }

    @Test fun reportsMissingExifAndMixedDimensionsAsSeparateFindings() {
        val frames = listOf(
            frame(0, 0f, metadata = CameraCaptureMetadata(imageWidth = 4000, imageHeight = 3000)),
            frame(1, 30f, metadata = CameraCaptureMetadata(imageWidth = 3000, imageHeight = 2000)),
            frame(2, 60f, metadata = null)
        )
        val passport = CapturePassportAnalyzer.analyze(manifest(frames), "2026-11-24T01:00:00Z")
        assertEquals("WARN", passport.check("exif_availability")?.outcome?.name)
        assertEquals("WARN", passport.check("image_dimensions_consistency")?.outcome?.name)
    }

    @Test fun geometricAndReconstructionPropertiesAreNeverReportedAsPassing() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(fullCoverageFrames()),
            "2026-11-24T01:00:00Z",
            ImageIntegrityEvidence(verified = true, verifiedFrameCount = 36)
        )
        listOf("blur_focus", "feature_overlap", "occlusion_parallax", "camera_pose_calibration", "reconstruction_result")
            .forEach { id -> assertEquals("NOT_MEASURED", passport.check(id)?.outcome?.name) }
    }

    @Test fun imageIntegrityFailureBlocksReadyState() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(fullCoverageFrames()),
            "2026-11-24T01:00:00Z",
            ImageIntegrityEvidence(verified = false, verifiedFrameCount = 35, failedFrameCount = 1, reasonCode = "HASH_MISMATCH")
        )
        assertEquals(PassportReadiness.INSUFFICIENT, passport.readiness)
        assertEquals("FAIL", passport.check("image_file_integrity")?.outcome?.name)
    }

    @Test fun integrityCannotPassWhenVerifiedFrameCountDoesNotMatchManifest() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(fullCoverageFrames()),
            "2026-11-24T01:00:00Z",
            ImageIntegrityEvidence(verified = true, verifiedFrameCount = 35)
        )
        assertEquals("FAIL", passport.check("image_file_integrity")?.outcome?.name)
        assertEquals(PassportReadiness.INSUFFICIENT, passport.readiness)
    }

    @Test fun fullCoverageWithVerifiedFilesCanBecomeReady() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(fullCoverageFrames()),
            "2026-11-24T01:00:00Z",
            ImageIntegrityEvidence(verified = true, verifiedFrameCount = 36)
        )
        assertEquals(PassportReadiness.READY, passport.readiness)
    }

    @Test fun jsonRoundTripPreservesPassportContract() {
        val original = CapturePassportAnalyzer.analyze(
            manifest(listOf(frame(0, 0f), frame(1, 30f))),
            "2026-11-24T01:00:00Z"
        )
        val encoded = CapturePassportJson.encode(original)
        val decoded = CapturePassportJson.decode(encoded)
        assertEquals(original, decoded)
        assertTrue(encoded.contains("\"schema_version\": 1"))
    }

    @Test fun readyPassportCannotSkipRequiredIntegrityEvidence() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(fullCoverageFrames()),
            "2026-11-24T01:00:00Z"
        ).copy(readiness = PassportReadiness.READY)
        try {
            CapturePassportContract.validate(passport)
            fail("Expected READY without verified image integrity to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun validatorRejectsSessionFrameCountMismatch() {
        val passport = CapturePassportAnalyzer.analyze(
            manifest(listOf(frame(0, 0f))),
            "2026-11-24T01:00:00Z"
        ).copy(session = CapturePassportSession("capture-other", "2026-11-24T00:00:00Z", 1, 1))
        try {
            CapturePassportContract.validate(passport, manifest(listOf(frame(0, 0f))))
            fail("Expected session mismatch to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun textReportExplainsUnmeasuredProperties() {
        val passport = CapturePassportAnalyzer.analyze(manifest(emptyList()), "2026-11-24T01:00:00Z")
        val report = PassportTextReport.render(passport)
        assertTrue(report.contains("NOT_MEASURED"))
        assertTrue(report.contains("reconstruction"))
    }
}
