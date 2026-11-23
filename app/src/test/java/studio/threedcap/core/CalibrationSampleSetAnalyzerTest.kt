package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class CalibrationSampleSetAnalyzerTest {
    private fun sample(yaw: Float, pitch: Float) = CalibrationSample(yaw, pitch, 1920, 1080, true)

    @Test fun acceptsDiverseSharpCalibrationViews() {
        val samples = listOf(
            sample(0f, 0f), sample(45f, 0f), sample(90f, 0f), sample(135f, 0f),
            sample(180f, 0f), sample(225f, 0f), sample(270f, 0f), sample(315f, 0f)
        )
        val report = CalibrationSampleSetAnalyzer.analyze(samples)
        assertTrue(report.accepted)
        assertEquals(8, report.acceptedCount)
    }

    @Test fun rejectsRepeatedViewsAndBlurrySamples() {
        val samples = List(8) { sample(0f, 0f) } + listOf(sample(90f, 0f).copy(isSharp = false))
        val report = CalibrationSampleSetAnalyzer.analyze(samples)
        assertFalse(report.accepted)
        assertTrue(report.warnings.any { it.contains("distinct") })
        assertTrue(report.warnings.any { it.contains("blur") })
    }

    @Test fun rejectsMixedImageDimensions() {
        val samples = List(8) { index -> sample(index * 45f, 0f) } + CalibrationSample(180f, 20f, 1280, 720, true)
        val report = CalibrationSampleSetAnalyzer.analyze(samples)
        assertFalse(report.accepted)
        assertTrue(report.warnings.any { it.contains("dimensions") })
    }
}
