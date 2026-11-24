package studio.threedcap.core

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class Camera2SensorCalibrationContractTest {
    private fun valid() = Camera2SensorCalibration(
        cameraId = "0",
        fx = 2800.0,
        fy = 2790.0,
        cx = 2000.0,
        cy = 1500.0,
        skew = 0.0,
        distortionCoefficients = listOf(-0.12, 0.03, 0.001, -0.002, 0.0005),
        preCorrectionActiveArray = Camera2PixelRect(0, 0, 4000, 3000),
        activeArray = Camera2PixelRect(0, 0, 3960, 2960),
        sensorOrientationDegrees = 90
    )

    @Test fun acceptsRawSensorFactoryCalibration() {
        assertTrue(Camera2SensorCalibrationContract.validate(valid()).isEmpty())
    }

    @Test fun permitsMissingOptionalDistortionAndOrientation() {
        val candidate = valid().copy(
            distortionCoefficients = null,
            sensorOrientationDegrees = null
        )
        assertTrue(Camera2SensorCalibrationContract.validate(candidate).isEmpty())
    }

    @Test fun rejectsInvalidIntrinsicsAndNonFinitePrincipalPoint() {
        val candidate = valid().copy(fx = 0.0, cx = Double.NaN)
        val errors = Camera2SensorCalibrationContract.validate(candidate)
        assertTrue(errors.any { it.contains("fx") })
        assertTrue(errors.any { it.contains("principal point") })
    }

    @Test fun rejectsMalformedDistortionAndArrayBounds() {
        val candidate = valid().copy(
            distortionCoefficients = listOf(Double.NaN),
            activeArray = Camera2PixelRect(0, 0, 4100, 3000)
        )
        val errors = Camera2SensorCalibrationContract.validate(candidate)
        assertTrue(errors.any { it.contains("distortion") })
        assertTrue(errors.any { it.contains("enclosed") })
    }

    @Test fun rejectsInvalidCameraIdAndSensorOrientation() {
        val candidate = valid().copy(cameraId = " ", sensorOrientationDegrees = 45)
        val errors = Camera2SensorCalibrationContract.validate(candidate)
        assertFalse(errors.isEmpty())
        assertTrue(errors.any { it.contains("camera ID") })
        assertTrue(errors.any { it.contains("orientation") })
    }
}
