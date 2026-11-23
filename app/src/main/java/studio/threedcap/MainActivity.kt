package studio.threedcap
import android.Manifest
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import studio.threedcap.core.CapturePlanner
import studio.threedcap.core.FrameQualityAnalyzer
import studio.threedcap.core.OrientationCalibration
import studio.threedcap.core.StabilityGate
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
 private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) setContent { Studio() } }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  cameraExecutor = Executors.newSingleThreadExecutor()
  sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
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
    try { provider.unbindAll(); provider.bindToLifecycle(this@MainActivity,CameraSelector.DEFAULT_BACK_CAMERA,preview,imageCapture,analysis) } catch (e: Exception) { e.printStackTrace() }
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
       Text("LOCAL CAPTURE LIBRARY",color=Color(0xFF9BF7D4)); Text(count.toString()+" frames captured in this session",color=Color.White)
       Text("Stored in app-private /captures. No upload.",color=Color.LightGray); Spacer(Modifier.weight(1f))
      }
     }
    }
   }
  }
 }
 private fun takePhoto(yaw: Float,pitch: Float) {
  val capture=imageCapture ?: return
  val folder=File(filesDir,"captures").apply { mkdirs() }
  val stamp=SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
  val file=File(folder,"3dcap_"+stamp+".jpg")
  capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),cameraExecutor,object:ImageCapture.OnImageSavedCallback {
   override fun onImageSaved(output: ImageCapture.OutputFileResults) {
    planner.markCaptured(yaw,pitch)
    runOnUiThread { capturedCount.intValue=planner.progress().captured }
   }
   override fun onError(exception: ImageCaptureException) { exception.printStackTrace() }
  })
 }
}
