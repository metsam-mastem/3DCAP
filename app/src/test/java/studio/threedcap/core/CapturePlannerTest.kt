package studio.threedcap.core
import org.junit.Assert.*
import org.junit.Test
class CapturePlannerTest {
 @Test fun startsEmpty() { val p=CapturePlanner().progress(); assertEquals(0,p.captured); assertEquals(36,p.total); assertEquals(0,p.percent); assertNotNull(p.nextTarget) }
 @Test fun wrapsYaw() { assertEquals(0,CapturePlanner.normalizeYaw(360f)); assertEquals(359,CapturePlanner.normalizeYaw(-1f)); assertEquals(2f,CapturePlanner.angularDistance(359f,1f),0.001f) }
 @Test fun duplicateAnglesDoNotIncreaseCoverage() { val p=CapturePlanner(); p.markCaptured(359f,18f); p.markCaptured(-1f,14f); assertEquals(1,p.progress().captured) }
 @Test fun reachesFullCoverage() { val p=CapturePlanner(90,listOf(0)); listOf(0f,90f,180f,270f).forEach { p.markCaptured(it,0f) }; assertEquals(100,p.progress().percent); assertNull(p.progress().nextTarget) }
}
