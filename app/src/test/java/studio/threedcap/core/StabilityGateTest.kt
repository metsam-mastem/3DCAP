package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class StabilityGateTest {
 @Test fun requiresContinuousQuietPeriod() {
  val gate=StabilityGate(thresholdRadiansPerSecond=0.08f,stableDurationNanos=500_000_000L)
  assertFalse(gate.update(0.01f,0.01f,0.01f,1_000_000_000L))
  assertFalse(gate.update(0.01f,0.01f,0.01f,1_499_000_000L))
  assertTrue(gate.update(0.01f,0.01f,0.01f,1_500_000_000L))
 }
 @Test fun motionResetsQuietPeriod() {
  val gate=StabilityGate()
  gate.update(0f,0f,0f,1_000_000_000L)
  assertFalse(gate.update(0.3f,0f,0f,1_400_000_000L))
  assertFalse(gate.update(0f,0f,0f,1_450_000_000L))
  assertTrue(gate.update(0f,0f,0f,2_000_000_000L))
 }
 @Test fun rejectsInvalidSamplesAsUnstable() {
  val gate=StabilityGate()
  assertFalse(gate.update(Float.NaN,0f,0f,1_000_000_000L))
  assertFalse(gate.update(0f,0f,0f,1_500_000_000L))
 }
 @Test fun resetClearsQuietPeriod() {
  val gate=StabilityGate()
  gate.update(0f,0f,0f,1_000_000_000L)
  gate.reset()
  assertFalse(gate.isStable(2_000_000_000L))
 }
}
