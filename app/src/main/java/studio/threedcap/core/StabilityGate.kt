package studio.threedcap.core

import kotlin.math.sqrt

/**
 * Requires a continuous low-angular-velocity interval before enabling capture.
 * Timestamps must be monotonic nanoseconds from the same clock.
 */
class StabilityGate(
 private val thresholdRadiansPerSecond: Float = 0.08f,
 private val stableDurationNanos: Long = 500_000_000L
) {
 private var quietSinceNanos: Long? = null

 init {
  require(thresholdRadiansPerSecond > 0f && thresholdRadiansPerSecond.isFinite())
  require(stableDurationNanos >= 0L)
 }

 @Synchronized fun update(x: Float, y: Float, z: Float, timestampNanos: Long): Boolean {
  if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
   quietSinceNanos = null
   return false
  }
  val magnitude = sqrt(x*x + y*y + z*z)
  if (magnitude > thresholdRadiansPerSecond) {
   quietSinceNanos = null
   return false
  }
  if (quietSinceNanos == null) quietSinceNanos = timestampNanos
  return isStable(timestampNanos)
 }

 @Synchronized fun isStable(nowNanos: Long): Boolean {
  val start = quietSinceNanos ?: return false
  return nowNanos >= start && nowNanos - start >= stableDurationNanos
 }

 @Synchronized fun reset() { quietSinceNanos = null }
}
