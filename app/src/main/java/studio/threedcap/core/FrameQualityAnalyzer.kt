package studio.threedcap.core

import kotlin.math.max
import kotlin.math.min

data class FrameQuality(val brightness: Float, val sharpness: Float, val label: String)

object FrameQualityAnalyzer {
 fun analyzeLuma(data: ByteArray, width: Int, height: Int, rowStride: Int, pixelStride: Int): FrameQuality {
  require(width > 2 && height > 2 && rowStride > 0 && pixelStride > 0)
  fun sample(x: Int, y: Int): Float {
   val index=y*rowStride+x*pixelStride
   require(index in data.indices) { "Luma buffer is smaller than its declared layout" }
   return (data[index].toInt() and 0xff).toFloat()
  }
  var sum=0.0; var count=0
  for (y in 0 until height step 4) for (x in 0 until width step 4) { sum+=sample(x,y); count++ }
  val brightness=(sum/count).toFloat()
  var lapSum=0.0; var lapSquared=0.0; var lapCount=0
  for (y in 1 until height-1 step 3) for (x in 1 until width-1 step 3) {
   val center=sample(x,y)
   val lap=sample(x-1,y)+sample(x+1,y)+sample(x,y-1)+sample(x,y+1)-4f*center
   lapSum+=lap; lapSquared+=lap*lap; lapCount++
  }
  val mean=lapSum/max(1,lapCount)
  val sharpness=max(0.0,lapSquared/max(1,lapCount)-mean*mean).toFloat()
  val label=when { brightness<45f -> "LOW LIGHT"; brightness>220f -> "OVEREXPOSED"; sharpness<14f -> "CHECK FOCUS"; else -> "EXPOSURE / DETAIL OK" }
  return FrameQuality(brightness,sharpness,label)
 }
}
