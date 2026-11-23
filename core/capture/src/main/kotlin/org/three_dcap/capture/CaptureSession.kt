package org.three_dcap.capture

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class CaptureShot(
    val sequence: Int,
    val yawDegrees: Double,
    val pitchDegrees: Double,
    val rollDegrees: Double,
    val relativeImagePath: String,
    val sha256: String,
    val byteSize: Long,
    val capturedAt: String
)

@Serializable
data class CaptureSession(
    val schemaVersion: String = "1.0",
    val sessionId: String,
    val objectLabel: String,
    val createdAt: String,
    val shots: List<CaptureShot> = emptyList()
)

class CaptureContractException(message: String) : IllegalArgumentException(message)

object CaptureSessionContract {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,95}$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun validate(session: CaptureSession): CaptureSession {
        if (session.schemaVersion != "1.0") throw CaptureContractException("schemaVersion must be 1.0")
        if (!idPattern.matches(session.sessionId)) throw CaptureContractException("sessionId must be 3-96 safe characters")
        if (session.objectLabel.isBlank() || session.objectLabel.length > 160) throw CaptureContractException("objectLabel must contain 1-160 characters")
        if (session.createdAt.isBlank()) throw CaptureContractException("createdAt is required")
        val sequences = HashSet<Int>()
        session.shots.forEachIndexed { index, shot ->
            if (shot.sequence < 0 || !sequences.add(shot.sequence)) throw CaptureContractException("shot sequence values must be unique non-negative integers")
            if (!shot.yawDegrees.isFinite() || shot.yawDegrees !in -180.0..180.0) throw CaptureContractException("shot[$index].yawDegrees must be finite and within -180..180")
            if (!shot.pitchDegrees.isFinite() || shot.pitchDegrees !in -90.0..90.0) throw CaptureContractException("shot[$index].pitchDegrees must be finite and within -90..90")
            if (!shot.rollDegrees.isFinite() || shot.rollDegrees !in -180.0..180.0) throw CaptureContractException("shot[$index].rollDegrees must be finite and within -180..180")
            val path = shot.relativeImagePath.replace('\\', '/')
            if (path.isBlank() || path.startsWith("/") || path.split('/').any { it == ".." || it.isBlank() }) throw CaptureContractException("shot[$index].relativeImagePath must be a safe relative path")
            if (!sha256Pattern.matches(shot.sha256)) throw CaptureContractException("shot[$index].sha256 must be a lowercase SHA-256 digest")
            if (shot.byteSize <= 0) throw CaptureContractException("shot[$index].byteSize must be positive")
            if (shot.capturedAt.isBlank()) throw CaptureContractException("shot[$index].capturedAt is required")
        }
        return session
    }
}

object CaptureSessionCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false; encodeDefaults = true }

    fun encode(session: CaptureSession): String = json.encodeToString(CaptureSessionContract.validate(session))

    fun decode(source: String): CaptureSession {
        val session = try {
            json.decodeFromString<CaptureSession>(source)
        } catch (error: SerializationException) {
            throw CaptureContractException("invalid capture manifest JSON: " + error.message)
        }
        return CaptureSessionContract.validate(session)
    }
}
