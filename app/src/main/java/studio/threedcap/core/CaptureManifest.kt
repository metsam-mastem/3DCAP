package studio.threedcap.core

import java.time.Instant

data class CapturedFrameRecord(
    val sequence: Int,
    val fileName: String,
    val relativePath: String,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val byteSize: Long,
    val sha256: String,
    val capturedAt: String
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
