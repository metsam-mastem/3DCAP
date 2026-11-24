package studio.threedcap
import android.Manifest
import android.graphics.BitmapFactory
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import studio.threedcap.core.CapturePlanner
import studio.threedcap.core.CapturePassportAnalyzer
import studio.threedcap.core.CapturePassportJson
import studio.threedcap.core.PassportTextReport
import studio.threedcap.core.PassportReadiness
import studio.threedcap.core.FrameQualityAnalyzer
import studio.threedcap.core.OrientationCalibration
import studio.threedcap.core.StabilityGate
import studio.threedcap.data.CaptureSessionStore
import studio.threedcap.data.CameraExifReader
import studio.threedcap.data.Camera2CalibrationReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), SensorEventListener {
 private lateinit var cameraExecutor: ExecutorService
 private lateinit var sensors: SensorManager
 private lateinit var captureSessionStore: CaptureSessionStore
 private val planner = CapturePlanner()
 private val calibration = OrientationCalibration()
 private val calibrated = mutableStateOf(false)
 private val stabilityGate = StabilityGate()
 private val stable = mutableStateOf(false)
 private var hasGyroscope = false
 private val orientation = mutableStateOf(0f to 0f)
 private val capturedCount = mutableIntStateOf(0)
 private val qualityStatus = mutableStateOf("ANALYZING")
 private var imageCapture: ImageCapture? = null
 @Volatile private var activeCameraInfo: CameraInfo? = null
 private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) setContent { Studio() } }
 private val createDataset = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
  if (uri != null) {
   try {
    val output=contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not open export destination")
    captureSessionStore.writeDatasetZip(output)
    qualityStatus.value="DATASET + PASSPORT EXPORTED"
   } catch (error: Exception) {
    qualityStatus.value="DATASET EXPORT FAILED"
    error.printStackTrace()
    try {
     android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
    } catch (cleanupError: Exception) {
     cleanupError.printStackTrace()
    }
   }
  }
 }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  cameraExecutor = Executors.newSingleThreadExecutor()
  sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
  captureSessionStore = CaptureSessionStore(File(filesDir,"captures"))
  captureSessionStore.load()?.frames?.forEach { frame -> planner.markCaptured(frame.yawDegrees,frame.pitchDegrees) }
  capturedCount.intValue = planner.progress().captured
  if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) setContent { Studio() }
  else askCamera.launch(Manifest.permission.CAMERA)
 }
 override fun onResume() {
  super.onResume()
  sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME) }
  val gyro=sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
  hasGyroscope=gyro != null
  if (gyro != null) sensors.registerListener(this,gyro,SensorManager.SENSOR_DELAY_GAME)
  else stable.value=true
 }
 override fun onPause() { sensors.unregisterListener(this); stabilityGate.reset(); stable.value=!hasGyroscope; super.onPause() }
 override fun onDestroy() { if (::cameraExecutor.isInitialized) cameraExecutor.shutdown(); super.onDestroy() }
 override fun onSensorChanged(e: SensorEvent) {
  when (e.sensor.type) {
   Sensor.TYPE_ROTATION_VECTOR -> {
    val matrix=FloatArray(9); val angles=FloatArray(3)
    SensorManager.getRotationMatrixFromVector(matrix,e.values); SensorManager.getOrientation(matrix,angles)
    orientation.value = Math.toDegrees(angles[0].toDouble()).toFloat() to Math.toDegrees(angles[1].toDouble()).toFloat()
   }
   Sensor.TYPE_GYROSCOPE -> {
    stable.value=stabilityGate.update(e.values[0],e.values[1],e.values[2],e.timestamp)
   }
  }
 }
 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

 @Composable private fun Studio() {
  val context=LocalContext.current
  var tab by remember { mutableStateOf("CAPTURE") }
  var previewView by remember { mutableStateOf<PreviewView?>(null) }
  val absoluteAngles by orientation
  val isCalibrated by calibrated
  val isStable by stable
  val angles = if (isCalibrated) calibration.toRelative(absoluteAngles.first,absoluteAngles.second) else null
  val count by capturedCount
  val quality by qualityStatus
  val progress=planner.progress()
  val recommendation=angles?.let { planner.recommend(it.yaw,it.pitch) }
  LaunchedEffect(previewView) {
   val view=previewView ?: return@LaunchedEffect
   val future=ProcessCameraProvider.getInstance(context)
   future.addListener({
    val provider=future.get()
    val preview=Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
    imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    analysis.setAnalyzer(cameraExecutor) { frame ->
     try {
      val plane=frame.planes[0]
      val buffer=plane.buffer.duplicate()
      val bytes=ByteArray(buffer.remaining())
      buffer.get(bytes)
      val metrics=FrameQualityAnalyzer.analyzeLuma(bytes,frame.width,frame.height,plane.rowStride,plane.pixelStride)
      runOnUiThread { qualityStatus.value=metrics.label }
     } finally { frame.close() }
    }
    try {
     provider.unbindAll()
     val boundCamera = provider.bindToLifecycle(
      this@MainActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
     )
     activeCameraInfo = boundCamera.cameraInfo
    } catch (e: Exception) {
     activeCameraInfo = null
     e.printStackTrace()
    }
   },ContextCompat.getMainExecutor(context))
  }
  MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF9BF7D4),secondary=Color(0xFF8CC8FF),background=Color(0xFF080D13),surface=Color(0xFF101923))) {
   Scaffold(containerColor=Color(0xFF080D13),bottomBar={
    NavigationBar(containerColor=Color(0xFF101923)) {
     NavigationBarItem(tab=="CAPTURE",{tab="CAPTURE"},{Icon(Icons.Default.Camera,null)},label={Text("Capture")})
     NavigationBarItem(tab=="TOUR",{tab="TOUR"},{Icon(Icons.Default.Explore,null)},label={Text("Tour")})
     NavigationBarItem(tab=="LIBRARY",{tab="LIBRARY"},{Icon(Icons.Default.PhotoLibrary,null)},label={Text("Library")})
    }
   }) { pad ->
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
      Column { Text("3DCAP / FIELD STUDIO",color=Color(0xFF9BF7D4),style=MaterialTheme.typography.labelMedium); Text(tab,color=Color.White,style=MaterialTheme.typography.headlineSmall) }
      Surface(color=Color(0xFF18352F),shape=RoundedCornerShape(50)) { Text("● OFFLINE",Modifier.padding(horizontal=12.dp,vertical=7.dp),color=Color(0xFF9BF7D4)) }
     }
     when(tab) {
      "CAPTURE" -> {
       Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF17222D),RoundedCornerShape(24.dp))) {
        AndroidView(factory={PreviewView(it).also { pv -> previewView=pv }},modifier=Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(14.dp)) { Text("LIVE VIEW",color=Color.White); Text(if (angles != null) "Object yaw "+angles.yaw.roundToInt()+"° · pitch "+angles.pitch.roundToInt()+"°" else "Calibrate the object front view",color=Color(0xFF9BF7D4)) }
        Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp),color=Color(0xCC101923),shape=RoundedCornerShape(18.dp)) {
         Column(Modifier.padding(12.dp)) {
          Text("NEXT VIEW  "+(recommendation?.target?.yawDegrees?.toString() ?: "—")+"° / "+(recommendation?.target?.pitchBand?.toString() ?: "—")+"°",color=Color(0xFF9BF7D4))
          Text(recommendation?.instruction ?: "Set the object front view to calibrate",color=Color.White)
          Text("Coverage  "+progress.percent+"% · "+count+" frames",color=Color.White)
          LinearProgressIndicator(progress={progress.percent/100f},modifier=Modifier.fillMaxWidth().padding(top=6.dp))
         }
        }
       }
       Text((recommendation?.instruction ?: "Calibration required")+" · "+(if (isStable) "STABLE" else "HOLD STEADY")+" · FRAME: "+quality,color=Color.LightGray)
       if (!isCalibrated) {
        OutlinedButton(onClick={ calibration.calibrate(absoluteAngles.first,absoluteAngles.second); calibrated.value=true },modifier=Modifier.fillMaxWidth()) { Text("SET OBJECT FRONT / CALIBRATE") }
       } else {
        OutlinedButton(onClick={ calibration.reset(); calibrated.value=false; planner.reset(); capturedCount.intValue=0 },modifier=Modifier.fillMaxWidth()) { Text("RECALIBRATE OBJECT") }
       }
       Button(onClick={ angles?.let { takePhoto(it.yaw,it.pitch) } },enabled=isCalibrated && recommendation?.aligned==true && isStable,modifier=Modifier.fillMaxWidth().height(58.dp)) { Icon(Icons.Default.Camera,null); Spacer(Modifier.width(10.dp)); Text("CAPTURE FRAME") }
      }
      "TOUR" -> {
       Text("VIRTUAL TOUR WORKSPACE",color=Color(0xFF9BF7D4))
       Text("Local panorama scenes, scene links and spatial hotspots are defined in the tour data model. Viewer/editor is next.",color=Color.White)
       Surface(color=Color(0xFF101923),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().weight(1f)) { Box(contentAlignment=Alignment.Center) { Text("360° TOUR CANVAS\nOffline scene graph foundation",color=Color.LightGray) } }
      }
      else -> {
       val manifest = remember(count) { captureSessionStore.load() }
       val passport = remember(manifest) {
        manifest?.let { CapturePassportAnalyzer.analyze(it, java.time.Instant.now().toString()) }
       }
       var showPassportDetails by remember { mutableStateOf(false) }
       var pendingDelete by remember { mutableStateOf<String?>(null) }
       var pendingRetake by remember { mutableStateOf<String?>(null) }
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Column {
         Text("LOCAL CAPTURE LIBRARY",color=Color(0xFF9BF7D4))
         Text("${manifest?.frames?.size ?: 0} registered frames · app-private storage",color=Color.White)
        }
        Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
         OutlinedButton(
          enabled = manifest != null,
          onClick = {
           val json = captureSessionStore.exportManifestJson()
           val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "3DCAP capture manifest")
            putExtra(android.content.Intent.EXTRA_TEXT, json)
           }
           startActivity(android.content.Intent.createChooser(send, "Export capture manifest"))
          }
         ) { Text("EXPORT JSON") }
         OutlinedButton(
          enabled = manifest != null,
          onClick = {
           val csv = captureSessionStore.exportManifestCsv()
           val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "3DCAP capture metadata")
            putExtra(android.content.Intent.EXTRA_TEXT, csv)
           }
           startActivity(android.content.Intent.createChooser(send, "Export capture metadata"))
          }
         ) { Text("EXPORT CSV") }
         OutlinedButton(
          enabled = manifest != null,
          onClick = { createDataset.launch("3dcap-dataset.zip") }
         ) { Text("EXPORT DATASET ZIP") }
        }
       }
       Text("JSON/CSV exports contain metadata only. Dataset ZIP includes the manifest and captured images; nothing is uploaded automatically.",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
       passport?.let { report ->
        Surface(color=Color(0xFF101923),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
         Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
          Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
           Text("CAPTURE PASSPORT",color=Color(0xFF9BF7D4),style=MaterialTheme.typography.labelLarge)
           Text(report.readiness.name,color=when(report.readiness) { PassportReadiness.READY -> Color(0xFF9BF7D4); PassportReadiness.REVIEW -> Color(0xFFFFD27D); else -> Color(0xFFFF9C9C) })
          }
          Text("${report.coverage.percent}% planner coverage · ${report.coverage.coveredCells}/${report.coverage.totalCells} cells · ${report.session.frameCount} frames",color=Color.White)
          LinearProgressIndicator(progress={report.coverage.percent/100f},modifier=Modifier.fillMaxWidth())
          report.checks.filter { it.outcome.name == "WARN" || it.outcome.name == "FAIL" }.take(3).forEach { finding ->
           Text("• ${finding.summary}",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
          }
          Text("Image-byte integrity: NOT_MEASURED until dataset export. Geometric visibility and reconstruction are not assessed.",color=Color(0xFFFFD27D),style=MaterialTheme.typography.labelSmall)
          Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
           TextButton(onClick={showPassportDetails=true}) { Text("VIEW PASSPORT",color=Color(0xFF9BF7D4)) }
           Text("ID ${report.passportId.take(24)}",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
          }
         }
        }
        if (showPassportDetails) {
         AlertDialog(
          onDismissRequest={showPassportDetails=false},
          title={Text("Capture Passport · ${report.readiness.name}")},
          text={
           Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text(PassportTextReport.render(report),style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)
           }
          },
          confirmButton={TextButton(onClick={showPassportDetails=false}) { Text("CLOSE") }},
          dismissButton={
           TextButton(onClick={
            val json=CapturePassportJson.encode(report)
            val send=android.content.Intent(android.content.Intent.ACTION_SEND).apply {
             type="application/json"
             putExtra(android.content.Intent.EXTRA_SUBJECT,"3DCAP Capture Passport")
             putExtra(android.content.Intent.EXTRA_TEXT,json)
            }
            startActivity(android.content.Intent.createChooser(send,"Share Passport preview"))
           }) { Text("SHARE JSON") }
          }
         )
        }
       }
       if (manifest == null || manifest.frames.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
         Text("No captured frames yet. Use Capture to start a session.",color=Color.LightGray)
        }
       } else {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
         items(manifest.frames.sortedBy { it.sequence },key={it.fileName}) { frame ->
          Surface(color=Color(0xFF101923),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
           Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            val thumbnail = remember(frame.fileName) { loadFrameThumbnail(File(filesDir,"captures/"+frame.fileName)) }
            if (thumbnail != null) {
             Image(bitmap=thumbnail,contentDescription="Captured frame ${frame.sequence+1}",modifier=Modifier.fillMaxWidth().height(148.dp),contentScale=ContentScale.Crop)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
             Text("FRAME ${frame.sequence+1}",color=Color(0xFF9BF7D4),style=MaterialTheme.typography.titleMedium)
             Text("${frame.byteSize/1024} KB",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
            }
            Text(frame.fileName,color=Color.White,style=MaterialTheme.typography.bodySmall)
            Text("Yaw ${frame.yawDegrees}° · Pitch ${frame.pitchDegrees}°",color=Color.LightGray)
            frame.cameraMetadata?.let { metadata ->
             Text(
              listOfNotNull(
               metadata.cameraMake,
               metadata.cameraModel,
               metadata.imageWidth?.let { width -> metadata.imageHeight?.let { height -> "${width}×${height}" } },
               metadata.focalLengthMm?.let { "${it} mm" },
               metadata.iso?.let { "ISO ${it}" }
              ).joinToString(" · "),
              color=Color(0xFF8CC8FF),style=MaterialTheme.typography.labelSmall
             )
            }
            if (frame.cameraMetadata?.camera2Calibration != null) {
             Text(
              "Camera2 factory intrinsics available · raw sensor coordinates; JPEG mapping not applied",
              color=Color(0xFFFFD27D),style=MaterialTheme.typography.labelSmall
             )
            }
            Text(frame.capturedAt,color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
            Text("SHA-256 ${frame.sha256.take(20)}…",color=Color(0xFF8CC8FF),style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
             TextButton(onClick={pendingRetake=frame.fileName}) { Text("RETAKE SAME VIEW",color=Color(0xFF9BF7D4)) }
             TextButton(onClick={pendingDelete=frame.fileName}) { Text("DELETE FRAME",color=Color(0xFFFF9C9C)) }
            }
           }
          }
         }
        }
       }
       if (pendingRetake != null) {
        AlertDialog(
         onDismissRequest={pendingRetake=null},
         title={Text("Retake this exact view?")},
         text={Text("The selected image will be removed from the session. Capture guidance will prioritize its original yaw and pitch so you can replace it.")},
         confirmButton={
          TextButton(onClick={
           val name=pendingRetake
           pendingRetake=null
           val oldFrame=manifest?.frames?.firstOrNull { it.fileName == name }
           if(oldFrame!=null) {
            try {
             val updated=captureSessionStore.removeFrame(oldFrame.fileName)
             planner.reset()
             updated.frames.forEach { planner.markCaptured(it.yawDegrees,it.pitchDegrees) }
             planner.requestRetake(oldFrame.yawDegrees,oldFrame.pitchDegrees)
             capturedCount.intValue=updated.frames.size
             qualityStatus.value="RETARGETED · SAME VIEW"
             tab="CAPTURE"
            } catch(error:Exception) {
             qualityStatus.value="RETAKE PREPARATION FAILED"
             error.printStackTrace()
            }
           }
          }) { Text("REMOVE AND RETAKE") }
         },
         dismissButton={TextButton(onClick={pendingRetake=null}) { Text("CANCEL") }}
        )
       }
       if (pendingDelete != null) {
        AlertDialog(
         onDismissRequest={pendingDelete=null},
         title={Text("Delete captured frame?")},
         text={Text("The image and its manifest entry will be removed from this device. This cannot be undone.")},
         confirmButton={
          TextButton(onClick={
           val name=pendingDelete
           pendingDelete=null
           if(name!=null) {
            try {
             val updated=captureSessionStore.removeFrame(name)
             planner.reset()
             updated.frames.forEach { planner.markCaptured(it.yawDegrees,it.pitchDegrees) }
             capturedCount.intValue=updated.frames.size
             qualityStatus.value="FRAME REMOVED"
            } catch(error:Exception) {
             qualityStatus.value="DELETE FAILED"
             error.printStackTrace()
            }
           }
          }) { Text("DELETE") }
         },
         dismissButton={TextButton(onClick={pendingDelete=null}) { Text("CANCEL") }}
        )
       }
      }
     }
    }
   }
  }
 }
 private fun loadFrameThumbnail(file: File): androidx.compose.ui.graphics.ImageBitmap? {
  if (!file.isFile) return null
  return try {
   val options=BitmapFactory.Options().apply { inSampleSize=8 }
   BitmapFactory.decodeFile(file.absolutePath,options)?.asImageBitmap()
  } catch (_: Exception) { null }
 }
 private fun takePhoto(yaw: Float,pitch: Float) {
  val capture=imageCapture ?: return
  val folder=File(filesDir,"captures").apply { mkdirs() }
  val stamp=SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
  val file=File(folder,"3dcap_"+stamp+".jpg")
  capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),cameraExecutor,object:ImageCapture.OnImageSavedCallback {
   override fun onImageSaved(output: ImageCapture.OutputFileResults) {
    try {
     val target=planner.markCaptured(yaw,pitch)
     val exifMetadata = CameraExifReader.read(file)
     // Read from the current CameraInfo for every saved frame; logical camera IDs can change.
     val camera2Calibration = activeCameraInfo?.let { Camera2CalibrationReader.read(it) }
     val cameraMetadata = when {
      exifMetadata != null -> exifMetadata.copy(camera2Calibration = camera2Calibration)
      camera2Calibration != null -> studio.threedcap.core.CameraCaptureMetadata(camera2Calibration = camera2Calibration)
      else -> null
     }
     val manifest=captureSessionStore.record(
      file,target.yawDegrees.toFloat(),target.pitchBand.toFloat(),cameraMetadata
     )
     runOnUiThread { capturedCount.intValue=manifest.frames.size; qualityStatus.value="FRAME SAVED · SHA-256 VERIFIED" }
    } catch (error: Exception) {
     planner.reset()
     captureSessionStore.load()?.frames?.forEach { frame -> planner.markCaptured(frame.yawDegrees,frame.pitchDegrees) }
     runOnUiThread { capturedCount.intValue=planner.progress().captured; qualityStatus.value="MANIFEST SAVE FAILED" }
     error.printStackTrace()
    }
   }
   override fun onError(exception: ImageCaptureException) { exception.printStackTrace() }
  })
 }
}
