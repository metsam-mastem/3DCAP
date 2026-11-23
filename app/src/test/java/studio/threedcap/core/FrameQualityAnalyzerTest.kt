package studio.threedcap.core
import org.junit.Assert.*
import org.junit.Test
class FrameQualityAnalyzerTest {
 @Test fun flagsDarkFrames() { val q=FrameQualityAnalyzer.analyzeLuma(ByteArray(32*32){20},32,32,32,1); assertEquals("LOW LIGHT",q.label) }
 @Test fun flagsOverexposure() { val q=FrameQualityAnalyzer.analyzeLuma(ByteArray(32*32){-1},32,32,32,1); assertEquals("OVEREXPOSED",q.label) }
 @Test fun flagsLowDetail() { val q=FrameQualityAnalyzer.analyzeLuma(ByteArray(32*32){110},32,32,32,1); assertEquals("CHECK FOCUS",q.label) }
 @Test fun readsPaddedRowsAndPixelStride() { val bytes=ByteArray(40*32); for(y in 0 until 32) for(x in 0 until 32) bytes[y*40+x]=(if((x+y)%2==0) 20 else 230).toByte(); val q=FrameQualityAnalyzer.analyzeLuma(bytes,32,32,40,1); assertTrue(q.sharpness>14f) }
}
