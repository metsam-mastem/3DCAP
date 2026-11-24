package studio.threedcap.core

import java.time.Instant

data class CameraCaptureMetadata(
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val orientation: Int? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val focalLengthMm: Float? = null,
    val focalLength35Mm: Int? = null,
    val aperture: Float? = null,
    val exposureTimeSeconds: Double? = null,
    val iso: Int? = null,
    val camera2Calibration: Camera2SensorCalibration? = null
)

data class CapturedFrameRecord(
    val sequence: Int,
    val fileName: String,
    val relativePath: String,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val byteSize: Long,
    val sha256: String,
    val capturedAt: String,
    val cameraMetadata: CameraCaptureMetadata? = null
)

data class CaptureManifest(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val createdAt: String,
    val frames: List<CapturedFrameRecord> = emptyList()
)

object CaptureManifestContract {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,95}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val filePattern = Regex("^3dcap_[A-Za-z0-9_-]+\\.jpg$")

    fun validate(manifest: CaptureManifest): CaptureManifest {
        require(manifest.schemaVersion == 1) { "Unsupported capture manifest schema" }
        require(idPattern.matches(manifest.sessionId)) { "Invalid session ID" }
        require(isTimestamp(manifest.createdAt)) { "createdAt must be an ISO-8601 instant" }
        val sequences = mutableSetOf<Int>()
        val names = mutableSetOf<String>()
        manifest.frames.forEach { frame ->
            require(frame.sequence >= 0 && sequences.add(frame.sequence)) { "Frame sequence must be unique and non-negative" }
            require(filePattern.matches(frame.fileName)) { "Invalid captured image filename" }
            require(frame.relativePath == frame.fileName) { "Frame path must be a filename relative to the capture directory" }
            require(names.add(frame.fileName)) { "Duplicate captured image filename" }
            require(frame.yawDegrees.isFinite() && frame.yawDegrees in 0f..360f) { "Invalid yaw" }
            require(frame.pitchDegrees.isFinite() && frame.pitchDegrees in -90f..90f) { "Invalid pitch" }
            require(frame.byteSize > 0) { "Frame size must be positive" }
            require(sha256Pattern.matches(frame.sha256)) { "Invalid SHA-256 digest" }
            require(isTimestamp(frame.capturedAt)) { "capturedAt must be an ISO-8601 instant" }
            frame.cameraMetadata?.let { metadata ->
                require(metadata.imageWidth == null || metadata.imageWidth > 0) { "Invalid EXIF image width" }
                require(metadata.imageHeight == null || metadata.imageHeight > 0) { "Invalid EXIF image height" }
                require(metadata.orientation == null || metadata.orientation in 1..8) { "Invalid EXIF orientation" }
                require(metadata.focalLengthMm == null || metadata.focalLengthMm.isFinite() && metadata.focalLengthMm > 0f) { "Invalid EXIF focal length" }
                require(metadata.focalLength35Mm == null || metadata.focalLength35Mm > 0) { "Invalid 35mm-equivalent focal length" }
                require(metadata.aperture == null || metadata.aperture.isFinite() && metadata.aperture > 0f) { "Invalid EXIF aperture" }
                require(metadata.exposureTimeSeconds == null || metadata.exposureTimeSeconds.isFinite() && metadata.exposureTimeSeconds > 0.0) { "Invalid EXIF exposure time" }
                require(metadata.iso == null || metadata.iso > 0) { "Invalid EXIF ISO" }
                metadata.camera2Calibration?.let { calibration ->
                    require(Camera2SensorCalibrationContract.validate(calibration).isEmpty()) {
                        "Invalid Camera2 sensor calibration metadata"
                    }
                }
            }
        }
        return manifest
    }

    private fun isTimestamp(value: String): Boolean = try {
        Instant.parse(value)
        true
    } catch (_: Exception) {
        false
    }
}
