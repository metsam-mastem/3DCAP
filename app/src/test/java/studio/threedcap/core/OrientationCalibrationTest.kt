package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class OrientationCalibrationTest {
 @Test fun mapsReferenceHeadingToZeroYawAndDefaultCapturePitch() {
  val calibration=OrientationCalibration()
  calibration.calibrate(123f,-8f)
  val pose=calibration.toRelative(123f,-8f)
  assertEquals(0f,pose.yaw,0.001f)
  assertEquals(15f,pose.pitch,0.001f)
 }
 @Test fun handlesAzimuthWrapAcrossNorth() {
  val calibration=OrientationCalibration()
  calibration.calibrate(179f,0f)
  val pose=calibration.toRelative(-179f,0f)
  assertEquals(2f,pose.yaw,0.001f)
 }
 @Test fun handlesReverseAzimuthWrap() {
  val calibration=OrientationCalibration()
  calibration.calibrate(-179f,0f)
  val pose=calibration.toRelative(179f,0f)
  assertEquals(358f,pose.yaw,0.001f)
 }
 @Test fun keepsPitchRelativeToCalibrationPose() {
  val calibration=OrientationCalibration()
  calibration.calibrate(20f,10f)
  assertEquals(25f,calibration.toRelative(20f,20f).pitch,0.001f)
 }
 @Test fun rejectsPoseConversionBeforeCalibration() {
  val calibration=OrientationCalibration()
  assertFalse(calibration.isCalibrated)
  try {
   calibration.toRelative(0f,0f)
   fail("Expected calibration guard")
  } catch (_: IllegalStateException) {}
 }
}
