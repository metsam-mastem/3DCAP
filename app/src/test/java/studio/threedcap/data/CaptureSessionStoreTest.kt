package studio.threedcap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.threedcap.core.CaptureManifestContract
import studio.threedcap.core.Camera2PixelRect
import studio.threedcap.core.Camera2SensorCalibration
import java.io.File
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

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
            val csv = store.exportManifestCsv()
            assertTrue(csv.startsWith("schema_version,session_id,created_at"))
            assertTrue(csv.contains("capture-"))
            assertTrue(csv.contains("3dcap_20261123_120000_000.jpg"))
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

    @Test fun manifestRoundTripsOptionalCameraExifMetadata() {
        val directory = Files.createTempDirectory("3dcap-exif-test").toFile()
        try {
            val image = File(directory, "3dcap_20261123_120000_011.jpg").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            }
            val store = CaptureSessionStore(directory)
            val metadata = studio.threedcap.core.CameraCaptureMetadata(
                imageWidth = 4000,
                imageHeight = 3000,
                orientation = 6,
                cameraMake = "Example Camera Co.",
                cameraModel = "Model X",
                focalLengthMm = 4.25f,
                focalLength35Mm = 26,
                iso = 100,
                camera2Calibration = Camera2SensorCalibration(
                    cameraId = "0",
                    fx = 2800.0,
                    fy = 2790.0,
                    cx = 2000.0,
                    cy = 1500.0,
                    skew = 0.0,
                    distortionCoefficients = listOf(-0.12, 0.03, 0.001, -0.002, 0.0005),
                    preCorrectionActiveArray = Camera2PixelRect(0, 0, 4000, 3000),
                    activeArray = Camera2PixelRect(0, 0, 3960, 2960),
                    sensorOrientationDegrees = 90
                )
            )
            store.record(image, 45f, 10f, metadata)

            val reloaded = CaptureSessionStore(directory).load()!!.frames.single()
            assertEquals(4000, reloaded.cameraMetadata?.imageWidth)
            assertEquals(3000, reloaded.cameraMetadata?.imageHeight)
            assertEquals(6, reloaded.cameraMetadata?.orientation)
            assertEquals("Example Camera Co.", reloaded.cameraMetadata?.cameraMake)
            assertEquals("Model X", reloaded.cameraMetadata?.cameraModel)
            assertEquals(4.25f, reloaded.cameraMetadata?.focalLengthMm)
            assertEquals(26, reloaded.cameraMetadata?.focalLength35Mm)
            assertEquals(100, reloaded.cameraMetadata?.iso)
            val camera2 = reloaded.cameraMetadata?.camera2Calibration
            assertEquals("0", camera2?.cameraId)
            assertEquals(2800.0, camera2?.fx)
            assertEquals(2000.0, camera2?.cx)
            assertEquals(listOf(-0.12, 0.03, 0.001, -0.002, 0.0005), camera2?.distortionCoefficients)
            assertEquals(Camera2PixelRect(0, 0, 4000, 3000), camera2?.preCorrectionActiveArray)
            assertEquals(Camera2PixelRect(0, 0, 3960, 2960), camera2?.activeArray)
            assertEquals(90, camera2?.sensorOrientationDegrees)
            val csv = store.exportManifestCsv()
            assertTrue(csv.contains("camera_make"))
            assertTrue(csv.contains("Example Camera Co."))
            assertTrue(csv.contains("camera2_camera_id"))
            assertTrue(csv.contains("camera2_pre_correction_active_array"))
            assertTrue(csv.contains("2800.0"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun exifReaderIgnoresFilesWithoutReadableExif() {
        val directory = Files.createTempDirectory("3dcap-invalid-exif-test").toFile()
        try {
            val invalidImage = File(directory, "not-an-image.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            assertEquals(null, CameraExifReader.read(invalidImage))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun startupRecoversInterruptedFrameDeletion() {
        val directory = Files.createTempDirectory("3dcap-recovery-test").toFile()
        try {
            val image = File(directory, "3dcap_20261123_120000_009.jpg").apply {
                writeBytes(byteArrayOf(9, 8, 7, 6))
            }
            val store = CaptureSessionStore(directory)
            store.record(image, 30f, 15f)

            val tombstone = File(directory, image.name + ".deleting")
            assertTrue(image.renameTo(tombstone))
            val recoveredStore = CaptureSessionStore(directory)

            assertTrue(image.isFile)
            assertFalse(tombstone.exists())
            assertEquals(image.name, recoveredStore.load()?.frames?.single()?.fileName)

            val orphanedTombstone = File(directory, "3dcap_20261123_120000_010.jpg.deleting")
                .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            CaptureSessionStore(directory)
            assertFalse(orphanedTombstone.exists())
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
    @Test fun datasetZipContainsImagesManifestAndColmapGuidance() {
        val directory = Files.createTempDirectory("3dcap-zip-test").toFile()
        try {
            val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 4)
            val image = File(directory, "3dcap_20261123_120000_003.jpg").apply { writeBytes(bytes) }
            val store = CaptureSessionStore(directory)
            store.record(image, 90f, 15f)

            val output = ByteArrayOutputStream()
            store.writeDatasetZip(output)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }

            assertTrue(entries.containsKey("manifest.json"))
            assertTrue(entries.containsKey("passport.json"))
            assertTrue(entries.containsKey("passport-report.txt"))
            val passportJson = String(entries.getValue("passport.json"), Charsets.UTF_8)
            assertTrue(passportJson.contains("\\\"schema_version\\\": 1"))
            assertTrue(passportJson.contains("image_file_integrity"))
            val passportReport = String(entries.getValue("passport-report.txt"), Charsets.UTF_8)
            assertTrue(passportReport.contains("3DCAP CAPTURE PASSPORT"))
            assertTrue(passportReport.contains("NOT_MEASURED"))
            assertTrue(entries.containsKey("COLMAP_IMPORT.txt"))
            assertTrue(entries.containsKey("images/" + image.name))
            assertTrue(entries.getValue("images/" + image.name).contentEquals(bytes))
            val guide = String(entries.getValue("COLMAP_IMPORT.txt"), Charsets.UTF_8)
            assertTrue(guide.contains("NOT calibrated camera extrinsics"))
            assertTrue(guide.contains("colmap feature_extractor"))
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test fun datasetExportRejectsSameSizeImageTampering() {
        val directory = Files.createTempDirectory("3dcap-tamper-test").toFile()
        try {
            val image = File(directory, "3dcap_20261123_120000_004.jpg").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
            }
            val store = CaptureSessionStore(directory)
            store.record(image, 0f, 15f)
            image.writeBytes(byteArrayOf(6, 5, 4, 3, 2, 1))

            try {
                store.writeDatasetZip(ByteArrayOutputStream())
                throw AssertionError("Expected same-size image tampering to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("changed during dataset export"))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
