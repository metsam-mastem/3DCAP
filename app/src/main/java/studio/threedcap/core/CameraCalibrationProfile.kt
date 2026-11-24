package studio.threedcap.core

import java.time.Instant

enum class CalibrationPattern { CHECKERBOARD, CHARUCO }

data class CameraCalibrationProfile(
    val schemaVersion: Int = 1,
    val cameraKey: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val cameraModel: String,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val distortion: List<Double>,
    val reprojectionErrorPx: Double,
    val sampleViewCount: Int,
    val pattern: CalibrationPattern,
    val calibratedAt: String
)

object CameraCalibrationProfileContract {
    fun validate(profile: CameraCalibrationProfile): List<String> {
        val errors = mutableListOf<String>()
        if (profile.schemaVersion != 1) errors += "Unsupported calibration schema version"
        if (profile.cameraKey.isBlank()) errors += "Camera key is required"
        if (profile.imageWidth <= 0 || profile.imageHeight <= 0) errors += "Image dimensions must be positive"
        if (profile.cameraModel.isBlank()) errors += "Camera model is required"
        if (!profile.fx.isFinite() || profile.fx <= 0.0) errors += "fx must be finite and positive"
        if (!profile.fy.isFinite() || profile.fy <= 0.0) errors += "fy must be finite and positive"
        if (!profile.cx.isFinite() || !profile.cy.isFinite() ||
            profile.cx < 0.0 || profile.cy < 0.0 ||
            profile.cx >= profile.imageWidth || profile.cy >= profile.imageHeight
        ) errors += "Invalid principal point: it must lie inside image bounds"
        if (profile.distortion.size > 8 || profile.distortion.any { !it.isFinite() }) {
            errors += "Distortion coefficients must be finite and contain at most eight values"
        }
        if (!profile.reprojectionErrorPx.isFinite() ||
            profile.reprojectionErrorPx < 0.0 || profile.reprojectionErrorPx > MAX_REPROJECTION_ERROR_PX
        ) errors += "Reprojection error exceeds the accepted calibration threshold"
        if (profile.sampleViewCount < MIN_CALIBRATION_VIEWS) {
            errors += "At least $MIN_CALIBRATION_VIEWS calibration views are required"
        }
        try {
            Instant.parse(profile.calibratedAt)
        } catch (_: Exception) {
            errors += "calibratedAt must be an ISO-8601 instant"
        }
        return errors
    }

    const val MIN_CALIBRATION_VIEWS = 8
    const val MAX_REPROJECTION_ERROR_PX = 2.0
}
