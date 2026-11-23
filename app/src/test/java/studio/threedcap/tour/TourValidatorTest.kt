package studio.threedcap.tour
import org.junit.Assert.*
import org.junit.Test
class TourValidatorTest {
 @Test fun acceptsLinkedOfflineTour() {
  val t=TourManifest(title="Workshop",scenes=listOf(
   TourScene("entry","Entry","panoramas/entry.jpg",listOf(TourHotspot("to-room",20f,5f,"Go to room","room"))),
   TourScene("room","Room","panoramas/room.jpg")
  ),routes=listOf(TourRoute("main","Main route",listOf("entry","room"))))
  assertTrue(TourValidator.validate(t).isEmpty())
 }
 @Test fun rejectsBrokenSceneLinksAndCoordinates() {
  val t=TourManifest(title="Broken",scenes=listOf(TourScene("a","A","a.jpg",listOf(TourHotspot("x",220f,0f,"Door","missing")))))
  val errors=TourValidator.validate(t)
  assertTrue(errors.any { it.contains("spherical bounds") })
  assertTrue(errors.any { it.contains("missing scene") })
 }
}
