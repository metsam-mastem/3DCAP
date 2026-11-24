package studio.threedcap.data

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import studio.threedcap.core.Camera2PixelRect
import studio.threedcap.core.Camera2SensorCalibration
import studio.threedcap.core.Camera2SensorCalibrationContract

/**
 * Reads optional factory Camera2 calibration from the CameraX-selected camera.
 * The returned coordinates remain in the sensor pre-correction active array;
 * this reader deliberately does not map them to the saved JPEG.
 */
object Camera2CalibrationReader {
    @OptIn(ExperimentalCamera2Interop::class)
    fun read(cameraInfo: CameraInfo): Camera2SensorCalibration? {
        return try {
            val camera2 = Camera2CameraInfo.from(cameraInfo)
            val intrinsic = camera2.getCameraCharacteristic(
                CameraCharacteristics.LENS_INTRINSIC_CALIBRATION
            ) ?: return null
            if (intrinsic.size < 5) return null

            val preCorrection = camera2.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE
            ) ?: return null
            val active = camera2.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
            ) ?: return null

            val distortion = if (Build.VERSION.SDK_INT >= 28) {
                camera2.getCameraCharacteristic(CameraCharacteristics.LENS_DISTORTION)
                    ?.map(Float::toDouble)
            } else {
                null
            }

            val sensorOrientation = camera2.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_ORIENTATION
            )

            val calibration = Camera2SensorCalibration(
                cameraId = camera2.cameraId,
                fx = intrinsic[0].toDouble(),
                fy = intrinsic[1].toDouble(),
                cx = intrinsic[2].toDouble(),
                cy = intrinsic[3].toDouble(),
                skew = intrinsic[4].toDouble(),
                distortionCoefficients = distortion,
                preCorrectionActiveArray = Camera2PixelRect(
                    preCorrection.left, preCorrection.top,
                    preCorrection.right, preCorrection.bottom
                ),
                activeArray = Camera2PixelRect(
                    active.left, active.top, active.right, active.bottom
                ),
                sensorOrientationDegrees = sensorOrientation
            )
            calibration.takeIf { Camera2SensorCalibrationContract.validate(it).isEmpty() }
        } catch (_: Exception) {
            // Camera2 interop and factory calibration are optional device capabilities.
            null
        }
    }
}
