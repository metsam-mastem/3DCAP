package studio.threedcap.core
import org.junit.Assert.*
import org.junit.Test
class CapturePlannerTest {
 @Test fun startsEmpty() { val p=CapturePlanner().progress(); assertEquals(0,p.captured); assertEquals(36,p.total); assertEquals(0,p.percent); assertNotNull(p.nextTarget) }
 @Test fun wrapsYaw() { assertEquals(0,CapturePlanner.normalizeYaw(360f)); assertEquals(359,CapturePlanner.normalizeYaw(-1f)); assertEquals(2f,CapturePlanner.angularDistance(359f,1f),0.001f) }
 @Test fun duplicateAnglesDoNotIncreaseCoverage() { val p=CapturePlanner(); p.markCaptured(359f,18f); p.markCaptured(-1f,14f); assertEquals(1,p.progress().captured) }
 @Test fun recommendsNearestUncapturedTarget() { val p=CapturePlanner(90,listOf(0)); val r=p.recommend(89f,0f); assertEquals(90,r.target?.yawDegrees); assertTrue(r.aligned) }
 @Test fun reachesFullCoverage() { val p=CapturePlanner(90,listOf(0)); listOf(0f,90f,180f,270f).forEach { p.markCaptured(it,0f) }; assertEquals(100,p.progress().percent); assertNull(p.recommend(0f,0f).target) }
 @Test fun retakePrioritizesTheExactPreviouslyCapturedView() {
  val planner = CapturePlanner(90, listOf(-25, 15, 55))
  planner.markCaptured(90f, 15f)
  planner.markCaptured(180f, 15f)
  val target = planner.requestRetake(91f, 14f)
  assertEquals(CaptureTarget(90, 15), target)
  val recommendation = planner.recommend(180f, 15f)
  assertEquals(target, recommendation.target)
  assertFalse(recommendation.aligned)
  planner.markCaptured(90f, 15f)
  assertEquals(CaptureTarget(270, 15), planner.recommend(270f, 15f).target)
 }
}
