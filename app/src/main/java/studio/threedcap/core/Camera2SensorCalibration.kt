package studio.threedcap.core

/**
 * Camera2 factory calibration expressed in the Android sensor's
 * pre-correction active-array coordinate system. These values are not
 * transformed to CameraX/JPEG output coordinates.
 */
data class Camera2PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class Camera2SensorCalibration(
    val cameraId: String,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val skew: Double,
    /** Brown-Conrady [k1, k2, k3, k4, k5]; null when the device does not expose it. */
    val distortionCoefficients: List<Double>?,
    val preCorrectionActiveArray: Camera2PixelRect,
    val activeArray: Camera2PixelRect,
    val sensorOrientationDegrees: Int?
)

object Camera2SensorCalibrationContract {
    fun validate(value: Camera2SensorCalibration): List<String> {
        val errors = mutableListOf<String>()
        if (value.cameraId.isBlank()) errors += "Camera2 camera ID is required"
        if (!value.fx.isFinite() || value.fx <= 0.0) errors += "Camera2 fx must be finite and positive"
        if (!value.fy.isFinite() || value.fy <= 0.0) errors += "Camera2 fy must be finite and positive"
        if (!value.cx.isFinite() || !value.cy.isFinite()) {
            errors += "Camera2 principal point must be finite"
        }
        if (!value.skew.isFinite()) errors += "Camera2 skew must be finite"

        fun validRect(rect: Camera2PixelRect): Boolean =
            rect.left >= 0 && rect.top >= 0 && rect.right > rect.left && rect.bottom > rect.top

        if (!validRect(value.preCorrectionActiveArray)) {
            errors += "Camera2 pre-correction active array is invalid"
        }
        if (!validRect(value.activeArray)) {
            errors += "Camera2 active array is invalid"
        } else if (validRect(value.preCorrectionActiveArray) &&
            (value.activeArray.left < value.preCorrectionActiveArray.left ||
                value.activeArray.top < value.preCorrectionActiveArray.top ||
                value.activeArray.right > value.preCorrectionActiveArray.right ||
                value.activeArray.bottom > value.preCorrectionActiveArray.bottom)
        ) {
            errors += "Camera2 active array must be enclosed by the pre-correction active array"
        }

        value.distortionCoefficients?.let { coefficients ->
            if (coefficients.size != 5 || coefficients.any { !it.isFinite() }) {
                errors += "Camera2 distortion must contain five finite Brown-Conrady coefficients"
            }
        }
        if (value.sensorOrientationDegrees != null &&
            value.sensorOrientationDegrees !in setOf(0, 90, 180, 270)
        ) {
            errors += "Camera2 sensor orientation must be 0, 90, 180, or 270 degrees"
        }
        return errors
    }
}
