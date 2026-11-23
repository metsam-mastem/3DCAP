package studio.threedcap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.threedcap.core.CaptureManifestContract
import java.io.File
import java.nio.file.Files

class CaptureSessionStoreTest {
    @Test fun recordsExportsAndRemovesFrame() {
        val directory = Files.createTempDirectory("3dcap-store-test").toFile()
        try {
            val image = File(directory, "3dcap_20261123_120000_000.jpg")
            image.writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1, 2, 3))
            val store = CaptureSessionStore(directory)
            val recorded = store.record(image, 45f, 10f)
            assertEquals(1, recorded.frames.size)
            assertEquals(64, recorded.frames.single().sha256.length)
            val exported = store.exportManifestJson()
            assertTrue(exported.contains("3dcap_20261123_120000_000.jpg"))
            assertEquals(1, store.load()?.frames?.size)

            val removed = store.removeFrame(image.name)
            assertTrue(removed.frames.isEmpty())
            assertFalse(image.exists())
            assertEquals(0, store.load()?.frames?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsUnregisteredAndUnsafeFrameNames() {
        val directory = Files.createTempDirectory("3dcap-store-test").toFile()
        try {
            val store = CaptureSessionStore(directory)
            try {
                store.removeFrame("../outside.jpg")
                throw AssertionError("Expected invalid filename to be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
            assertNotNull(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun manifestRemainsValidAfterRemoval() {
        val directory = Files.createTempDirectory("3dcap-store-test").toFile()
        try {
            val imageA = File(directory, "3dcap_20261123_120000_001.jpg").apply { writeBytes(byteArrayOf(1,2,3)) }
            val imageB = File(directory, "3dcap_20261123_120000_002.jpg").apply { writeBytes(byteArrayOf(4,5,6)) }
            val store = CaptureSessionStore(directory)
            store.record(imageA, 10f, 0f)
            store.record(imageB, 20f, 0f)
            val updated = store.removeFrame(imageA.name)
            assertEquals(1, updated.frames.size)
            assertEquals(1, CaptureManifestContract.validate(updated).frames.size)
            assertTrue(imageB.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
