package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class DatasetQualityAnalyzerTest {
    private fun frame(sequence: Int, yaw: Float, pitch: Float = 15f) = CapturedFrameRecord(
        sequence = sequence,
        fileName = "3dcap_20261123_120000_${sequence.toString().padStart(3, '0')}.jpg",
        relativePath = "3dcap_20261123_120000_${sequence.toString().padStart(3, '0')}.jpg",
        yawDegrees = yaw,
        pitchDegrees = pitch,
        byteSize = 1000,
        sha256 = sequence.toString(16).repeat(64).take(64),
        capturedAt = "2026-11-23T12:00:00Z"
    )

    @Test fun reportsCoverageFromPlannerCells() {
        val frames = listOf(frame(0, 0f), frame(1, 30f), frame(2, 60f))
        val report = DatasetQualityAnalyzer.analyze(frames)
        assertEquals(3, report.coveredCells)
        assertEquals(36, report.totalCells)
        assertEquals(8, report.coveragePercent)
        assertEquals(33, report.missingCells.size)
    }

    @Test fun identifiesRepeatedPlannerCells() {
        val report = DatasetQualityAnalyzer.analyze(listOf(frame(0, 0f), frame(1, 1f)))
        assertEquals(1, report.duplicateCellCount)
        assertEquals(1, report.coveredCells)
    }

    @Test fun flagsRepeatedImageContentAcrossDifferentPlannerCells() {
        val first = frame(0, 0f)
        val second = frame(1, 30f).copy(sha256 = first.sha256)
        val report = DatasetQualityAnalyzer.analyze(listOf(first, second))
        assertTrue(report.warnings.any { it.contains("identical image content") })
    }

    @Test fun flagsMixedImageDimensionsAndMissingExifAsReviewSignals() {
        val a = frame(0, 0f).copy(cameraMetadata = CameraCaptureMetadata(imageWidth = 4000, imageHeight = 3000))
        val b = frame(1, 30f).copy(cameraMetadata = CameraCaptureMetadata(imageWidth = 3000, imageHeight = 2000))
        val withoutExif = frame(2, 60f)
        val report = DatasetQualityAnalyzer.analyze(listOf(a, b, withoutExif))
        assertTrue(report.warnings.any { it.contains("dimensions") })
        assertTrue(report.warnings.any { it.contains("EXIF") })
    }

    @Test fun emptyDatasetIsInsufficient() {
        val report = DatasetQualityAnalyzer.analyze(emptyList())
        assertEquals(DatasetReadiness.INSUFFICIENT, report.readiness)
        assertEquals(0, report.coveragePercent)
    }
}
