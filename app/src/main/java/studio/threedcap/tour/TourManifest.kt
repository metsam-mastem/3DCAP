package studio.threedcap.tour

data class TourHotspot(
 val id: String,
 val yaw: Float,
 val pitch: Float,
 val label: String,
 val targetSceneId: String? = null,
 val content: String? = null
)
data class TourScene(
 val id: String,
 val title: String,
 val panoramaPath: String,
 val hotspots: List<TourHotspot> = emptyList()
)
data class TourRoute(val id: String, val title: String, val sceneIds: List<String>)
data class TourManifest(val schemaVersion: Int = 1, val title: String, val scenes: List<TourScene>, val routes: List<TourRoute> = emptyList())

object TourValidator {
 fun validate(tour: TourManifest): List<String> {
  val errors=mutableListOf<String>()
  if (tour.schemaVersion != 1) errors += "Unsupported schema version"
  if (tour.title.isBlank()) errors += "Tour title is required"
  val sceneIds=tour.scenes.map { it.id }
  if (sceneIds.any { it.isBlank() }) errors += "Scene IDs cannot be blank"
  if (sceneIds.size != sceneIds.toSet().size) errors += "Scene IDs must be unique"
  val known=sceneIds.toSet()
  tour.scenes.forEach { scene ->
   if (scene.panoramaPath.isBlank()) errors += "Scene " + scene.id + " has no panorama asset"
   val hotspotIds=scene.hotspots.map { it.id }
   if (hotspotIds.size != hotspotIds.toSet().size) errors += "Hotspot IDs must be unique in scene " + scene.id
   scene.hotspots.forEach { spot ->
    if (spot.yaw !in -180f..180f || spot.pitch !in -90f..90f) errors += "Hotspot " + spot.id + " is outside spherical bounds"
    if (spot.targetSceneId != null && spot.targetSceneId !in known) errors += "Hotspot " + spot.id + " targets a missing scene"
    if (spot.label.isBlank()) errors += "Hotspot " + spot.id + " needs a label"
   }
  }
  tour.routes.forEach { route -> if (route.sceneIds.any { it !in known }) errors += "Route " + route.id + " references a missing scene" }
  return errors
 }
}
