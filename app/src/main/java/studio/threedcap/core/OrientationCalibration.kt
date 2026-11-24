package studio.threedcap.core

data class RelativePose(val yaw: Float, val pitch: Float)

/**
 * Converts device azimuth/pitch into an object/session-relative capture pose.
 * The first calibrated view is defined as yaw=0 and pitch=15 degrees,
 * matching the default CapturePlanner's middle ring.
 */
class OrientationCalibration(private val referencePitch: Float = 15f) {
 private var referenceYaw: Float? = null
 private var referenceDevicePitch: Float? = null

 val isCalibrated: Boolean get() = referenceYaw != null && referenceDevicePitch != null

 @Synchronized fun calibrate(deviceYaw: Float, devicePitch: Float) {
  require(deviceYaw.isFinite() && devicePitch.isFinite()) { "Device pose must be finite" }
  referenceYaw = normalize(deviceYaw)
  referenceDevicePitch = devicePitch
 }

 @Synchronized fun toRelative(deviceYaw: Float, devicePitch: Float): RelativePose {
  val yaw0 = referenceYaw ?: throw IllegalStateException("Calibrate the object front view first")
  val pitch0 = referenceDevicePitch ?: throw IllegalStateException("Calibrate the object front view first")
  require(deviceYaw.isFinite() && devicePitch.isFinite()) { "Device pose must be finite" }
  val yaw = normalize(deviceYaw - yaw0)
  val pitch = devicePitch - pitch0 + referencePitch
  return RelativePose(yaw, pitch)
 }

 @Synchronized fun reset() {
  referenceYaw = null
  referenceDevicePitch = null
 }

 private fun normalize(value: Float): Float = ((value % 360f) + 360f) % 360f
}
