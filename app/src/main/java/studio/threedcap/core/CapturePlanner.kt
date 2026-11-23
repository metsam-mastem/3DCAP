package studio.threedcap.core
import kotlin.math.abs
import kotlin.math.roundToInt

data class CaptureTarget(val yawDegrees: Int, val pitchBand: Int)
data class CaptureProgress(val captured: Int, val total: Int, val percent: Int, val nextTarget: CaptureTarget?)

class CapturePlanner(private val yawStep: Int = 30, private val pitchBands: List<Int> = listOf(-25, 15, 55)) {
 init { require(yawStep in 10..90 && 360 % yawStep == 0); require(pitchBands.isNotEmpty()) }
 private val completed = linkedSetOf<CaptureTarget>()
 private val targets = pitchBands.flatMap { pitch -> (0 until 360 step yawStep).map { yaw -> CaptureTarget(yaw, pitch) } }

 @Synchronized fun markCaptured(yawDegrees: Float, pitchDegrees: Float): CaptureTarget {
  val yaw = normalizeYaw(yawDegrees)
  val snappedYaw = ((yaw.toFloat() / yawStep).roundToInt() * yawStep) % 360
  val pitch = pitchBands.minBy { abs(it - pitchDegrees) }
  return CaptureTarget(snappedYaw, pitch).also { completed.add(it) }
 }
 @Synchronized fun progress(): CaptureProgress {
  val count = completed.size.coerceAtMost(targets.size)
  return CaptureProgress(count, targets.size, (count * 100f / targets.size).roundToInt(), targets.firstOrNull { it !in completed })
 }
 @Synchronized fun reset() { completed.clear() }

 companion object {
  fun normalizeYaw(yaw: Float): Int = (((yaw % 360f) + 360f) % 360f).roundToInt() % 360
  fun angularDistance(a: Float, b: Float): Float {
   val delta = abs(normalizeYaw(a).toFloat() - normalizeYaw(b).toFloat())
   return minOf(delta, 360f - delta)
  }
 }
}
