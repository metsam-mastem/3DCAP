package studio.threedcap.core

import java.time.Instant

enum class PassportReadiness { INSUFFICIENT, REVIEW, READY }
enum class PassportCheckOutcome { PASS, WARN, FAIL, NOT_MEASURED }
enum class PassportCheckSeverity { INFO, WARNING, BLOCKER }

data class CapturePassportSession(
    val sessionId: String,
    val createdAt: String,
    val manifestSchemaVersion: Int,
    val frameCount: Int
)

data class CapturePassportCoverage(
    val coveredCells: Int,
    val totalCells: Int,
    val percent: Int,
    val duplicateCellCount: Int,
    val missingCells: List<CaptureTarget>
)

data class CapturePassportCheck(
    val checkId: String,
    val category: String,
    val outcome: PassportCheckOutcome,
    val severity: PassportCheckSeverity,
    val summary: String,
    val evidence: Map<String, Any?> = emptyMap(),
    val limitations: List<String> = emptyList(),
    val remediation: String? = null,
    val analyzerVersion: String
)

data class CapturePassport(
    val schemaVersion: Int = 1,
    val passportId: String,
    val generatedAt: String,
    val session: CapturePassportSession,
    val analyzerId: String,
    val analyzerVersion: String,
    val readiness: PassportReadiness,
    val coverage: CapturePassportCoverage,
    val checks: List<CapturePassportCheck>,
    val limitations: List<String>
) {
    fun check(checkId: String): CapturePassportCheck? = checks.firstOrNull { it.checkId == checkId }
}

data class ImageIntegrityEvidence(
    val verified: Boolean?,
    val verifiedFrameCount: Int = 0,
    val failedFrameCount: Int = 0,
    val reasonCode: String? = null
) {
    init {
        require(verifiedFrameCount >= 0) { "Verified frame count cannot be negative" }
        require(failedFrameCount >= 0) { "Failed frame count cannot be negative" }
        require(reasonCode == null || reasonCode.length <= 64) { "Integrity reason code is too long" }
    }
}

object CapturePassportContract {
    private val idPattern = Regex("^passport-[A-Za-z0-9._-]{3,110}$")
    private val checkIdPattern = Regex("^[a-z][a-z0-9_]{2,63}$")

    fun validate(passport: CapturePassport, manifest: CaptureManifest? = null): CapturePassport {
        require(passport.schemaVersion == 1) { "Unsupported Passport schema version" }
        require(idPattern.matches(passport.passportId)) { "Invalid Passport ID" }
        require(isInstant(passport.generatedAt)) { "generatedAt must be an ISO-8601 instant" }
        require(passport.analyzerId == "3dcap-capture-quality") { "Unsupported analyzer ID" }
        require(passport.analyzerVersion.isNotBlank() && passport.analyzerVersion.length <= 32) {
            "Invalid analyzer version"
        }
        require(passport.session.sessionId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,95}$"))) {
            "Invalid session ID"
        }
        require(isInstant(passport.session.createdAt)) { "Session createdAt must be an ISO-8601 instant" }
        require(passport.session.manifestSchemaVersion == 1) { "Unsupported manifest schema version" }
        require(passport.session.frameCount >= 0) { "Frame count cannot be negative" }

        val coverage = passport.coverage
        require(coverage.totalCells > 0) { "Total planner cells must be positive" }
        require(coverage.coveredCells in 0..coverage.totalCells) { "Covered cell count is out of range" }
        require(coverage.percent in 0..100) { "Coverage percentage is out of range" }
        require(coverage.duplicateCellCount >= 0) { "Duplicate cell count cannot be negative" }
        require(coverage.missingCells.distinct().size == coverage.missingCells.size) {
            "Missing planner cells must be unique"
        }
        coverage.missingCells.forEach {
            require(it.yawDegrees in 0 until 360 && it.yawDegrees % 30 == 0) { "Invalid missing-cell yaw" }
            require(it.pitchBand in listOf(-25, 15, 55)) { "Invalid missing-cell pitch band" }
        }

        val ids = mutableSetOf<String>()
        passport.checks.forEach { check ->
            require(checkIdPattern.matches(check.checkId) && ids.add(check.checkId)) {
                "Invalid or duplicate quality check ID"
            }
            require(check.category.isNotBlank() && check.category.length <= 40) { "Invalid check category" }
            require(check.summary.isNotBlank() && check.summary.length <= 500) { "Invalid check summary" }
            require(check.analyzerVersion == passport.analyzerVersion) { "Check analyzer version mismatch" }
            require(check.limitations.size <= 20 && check.limitations.all { it.length <= 500 }) {
                "Invalid check limitations"
            }
            require(check.evidence.size <= 40) { "Too many evidence fields" }
        }

        manifest?.let {
            CaptureManifestContract.validate(it)
            require(passport.session.sessionId == it.sessionId) { "Passport session ID does not match manifest" }
            require(passport.session.createdAt == it.createdAt) { "Passport session timestamp does not match manifest" }
            require(passport.session.manifestSchemaVersion == it.schemaVersion) {
                "Passport manifest schema does not match manifest"
            }
            require(passport.session.frameCount == it.frames.size) { "Passport frame count does not match manifest" }
        }
        return passport
    }

    private fun isInstant(value: String): Boolean = try {
        Instant.parse(value)
        true
    } catch (_: Exception) {
        false
    }
}
