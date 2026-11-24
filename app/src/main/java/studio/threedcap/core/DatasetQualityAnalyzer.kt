package studio.threedcap.core

import kotlin.math.abs
import kotlin.math.roundToInt

enum class DatasetReadiness { INSUFFICIENT, REVIEW, READY }

data class DatasetQualityReport(
    val frameCount: Int,
    val coveredCells: Int,
    val totalCells: Int,
    val coveragePercent: Int,
    val duplicateCellCount: Int,
    val missingCells: List<CaptureTarget>,
    val warnings: List<String>,
    val readiness: DatasetReadiness
)

object DatasetQualityAnalyzer {
    private const val YAW_STEP = 30
    private val pitchBands = listOf(-25, 15, 55)
    private val targets = pitchBands.flatMap { pitch ->
        (0 until 360 step YAW_STEP).map { yaw -> CaptureTarget(yaw, pitch) }
    }

    /**
     * Evaluates planner-cell coverage and metadata consistency only.
     * It does not claim geometric overlap, sharpness, visibility, or reconstructability.
     */
    fun analyze(frames: List<CapturedFrameRecord>): DatasetQualityReport {
        val warnings = mutableListOf<String>()
        val assigned = frames.map { frame ->
            val yaw = normalizeYaw(frame.yawDegrees)
            val snappedYaw = ((yaw.toFloat() / YAW_STEP).roundToInt() * YAW_STEP) % 360
            val pitch = pitchBands.minBy { abs(it - frame.pitchDegrees) }
            CaptureTarget(snappedYaw, pitch)
        }
        val uniqueCells = assigned.toSet()
        val missing = targets.filterNot { it in uniqueCells }
        val duplicates = (assigned.size - uniqueCells.size).coerceAtLeast(0)
        if (duplicates > 0) warnings += "$duplicates frame(s) repeat an already-covered planner cell"

        val duplicateImages = frames
            .groupingBy { it.sha256.lowercase() }
            .eachCount()
            .values
            .sumOf { (it - 1).coerceAtLeast(0) }
        if (duplicateImages > 0) {
            warnings += "$duplicateImages frame(s) contain identical image content (SHA-256)"
        }

        val metadataMissing = frames.count { it.cameraMetadata == null }
        if (metadataMissing > 0) warnings += "$metadataMissing frame(s) have no readable EXIF metadata"

        val dimensions = frames.mapNotNull { frame ->
            val metadata = frame.cameraMetadata ?: return@mapNotNull null
            val width = metadata.imageWidth
            val height = metadata.imageHeight
            if (width != null && height != null) width to height else null
        }.toSet()
        if (dimensions.size > 1) warnings += "Mixed image dimensions detected across frames"

        val coverage = if (targets.isEmpty()) 0 else (uniqueCells.size * 100f / targets.size).roundToInt()
        val readiness = when {
            frames.isEmpty() || coverage < 35 -> DatasetReadiness.INSUFFICIENT
            coverage < 80 || warnings.isNotEmpty() -> DatasetReadiness.REVIEW
            else -> DatasetReadiness.READY
        }
        return DatasetQualityReport(
            frameCount = frames.size,
            coveredCells = uniqueCells.size,
            totalCells = targets.size,
            coveragePercent = coverage,
            duplicateCellCount = duplicates,
            missingCells = missing,
            warnings = warnings,
            readiness = readiness
        )
    }

    private fun normalizeYaw(value: Float): Int =
        (((value % 360f) + 360f) % 360f).roundToInt() % 360
}
