package studio.threedcap.core

import kotlin.math.roundToInt

data class CalibrationSample(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val isSharp: Boolean
)

data class CalibrationSampleSetReport(
    val totalCount: Int,
    val acceptedCount: Int,
    val distinctYawBins: Int,
    val accepted: Boolean,
    val warnings: List<String>
)

object CalibrationSampleSetAnalyzer {
    private const val MIN_SAMPLES = 8
    private const val YAW_BIN_DEGREES = 45
    private const val PITCH_BIN_DEGREES = 20f
    private const val MIN_PITCH_BINS = 3

    fun analyze(samples: List<CalibrationSample>): CalibrationSampleSetReport {
        val warnings = mutableListOf<String>()
        if (samples.size < MIN_SAMPLES) warnings += "At least $MIN_SAMPLES calibration samples are required"
        val validSamples = samples.filter {
            it.yawDegrees.isFinite() && it.pitchDegrees.isFinite() &&
                it.yawDegrees in 0f..360f && it.pitchDegrees in -90f..90f &&
                it.imageWidth > 0 && it.imageHeight > 0
        }
        if (validSamples.size != samples.size) warnings += "Invalid orientation or image dimensions in calibration samples"

        val dimensions = validSamples.map { it.imageWidth to it.imageHeight }.toSet()
        if (dimensions.size > 1) warnings += "Calibration samples have mixed image dimensions"

        val sharpSamples = validSamples.filter { it.isSharp }
        if (sharpSamples.size != validSamples.size) warnings += "One or more calibration samples fail the blur gate"

        val yawBins = sharpSamples.map { sample ->
            val normalized = ((sample.yawDegrees % 360f) + 360f) % 360f
            ((normalized / YAW_BIN_DEGREES).roundToInt() * YAW_BIN_DEGREES) % 360
        }.toSet()
        if (yawBins.size < MIN_SAMPLES) warnings += "At least $MIN_SAMPLES distinct yaw bins are required"
        val pitchBins = sharpSamples.map { ((it.pitchDegrees + 90f) / PITCH_BIN_DEGREES).toInt() }.toSet()
        if (pitchBins.size < MIN_PITCH_BINS) warnings += "Calibration samples must include at least $MIN_PITCH_BINS distinct tilt bands"

        return CalibrationSampleSetReport(
            totalCount = samples.size,
            acceptedCount = sharpSamples.size,
            distinctYawBins = yawBins.size,
            accepted = warnings.isEmpty(),
            warnings = warnings
        )
    }
}
