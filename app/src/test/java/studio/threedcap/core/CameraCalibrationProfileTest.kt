package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class CameraCalibrationProfileTest {
    private fun valid() = CameraCalibrationProfile(
        cameraKey = "rear-main",
        imageWidth = 4000,
        imageHeight = 3000,
        cameraModel = "OPENCV",
        fx = 2800.0,
        fy = 2790.0,
        cx = 2000.0,
        cy = 1500.0,
        distortion = listOf(-0.12, 0.03, 0.001, -0.002),
        reprojectionErrorPx = 0.42,
        sampleViewCount = 18,
        pattern = CalibrationPattern.CHARUCO,
        calibratedAt = "2026-11-23T12:00:00Z"
    )

    @Test fun acceptsPlausibleCalibrationProfile() {
        assertTrue(CameraCalibrationProfileContract.validate(valid()).isEmpty())
    }

    @Test fun rejectsInvalidIntrinsicsAndImageBounds() {
        val errors = CameraCalibrationProfileContract.validate(valid().copy(fx = 0.0, cx = 4100.0))
        assertTrue(errors.any { it.contains("fx") })
        assertTrue(errors.any { it.contains("principal point") })
    }

    @Test fun rejectsNonFiniteDistortionAndPoorReprojectionError() {
        val errors = CameraCalibrationProfileContract.validate(
            valid().copy(distortion = listOf(Double.NaN), reprojectionErrorPx = 9.0)
        )
        assertTrue(errors.any { it.contains("distortion", ignoreCase = true) })
        assertTrue(errors.any { it.contains("reprojection", ignoreCase = true) })
    }

    @Test fun rejectsInsufficientCalibrationViews() {
        val errors = CameraCalibrationProfileContract.validate(valid().copy(sampleViewCount = 2))
        assertTrue(errors.any { it.contains("views") })
    }
}
