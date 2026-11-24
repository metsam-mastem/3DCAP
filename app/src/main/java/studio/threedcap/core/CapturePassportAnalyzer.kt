package studio.threedcap.core

private const val PASSPORT_ANALYZER_VERSION = "1.0.0"

object CapturePassportAnalyzer {
    private val geometricLimitations = listOf(
        "Planner-cell occupancy does not prove that the corresponding physical surface is visible."
    )
    private val deferredLimitations = listOf(
        "This property is not measured by the current offline MVP and must not be interpreted as passing."
    )

    fun analyze(
        manifest: CaptureManifest,
        generatedAt: String,
        imageIntegrity: ImageIntegrityEvidence? = null
    ): CapturePassport {
        CaptureManifestContract.validate(manifest)
        val report = DatasetQualityAnalyzer.analyze(manifest.frames)
        val checks = mutableListOf<CapturePassportCheck>()

        val coverageOutcome = when {
            report.frameCount == 0 || report.coveragePercent < 35 -> PassportCheckOutcome.FAIL
            report.coveragePercent < 80 -> PassportCheckOutcome.WARN
            else -> PassportCheckOutcome.PASS
        }
        checks += check(
            id = "planner_coverage",
            category = "coverage",
            outcome = coverageOutcome,
            severity = if (coverageOutcome == PassportCheckOutcome.FAIL) PassportCheckSeverity.BLOCKER
                else if (coverageOutcome == PassportCheckOutcome.WARN) PassportCheckSeverity.WARNING else PassportCheckSeverity.INFO,
            summary = "Planner target coverage is ${report.coveragePercent}% (${report.coveredCells}/${report.totalCells} cells).",
            evidence = mapOf(
                "covered_cells" to report.coveredCells,
                "total_cells" to report.totalCells,
                "coverage_percent" to report.coveragePercent,
                "missing_cell_count" to report.missingCells.size
            ),
            limitations = geometricLimitations,
            remediation = if (coverageOutcome != PassportCheckOutcome.PASS && report.missingCells.isNotEmpty()) {
                "Capture the listed missing planner targets."
            } else null
        )

        checks += check(
            id = "planner_cell_duplicates",
            category = "coverage",
            outcome = if (report.duplicateCellCount == 0) PassportCheckOutcome.PASS else PassportCheckOutcome.WARN,
            severity = if (report.duplicateCellCount == 0) PassportCheckSeverity.INFO else PassportCheckSeverity.WARNING,
            summary = if (report.duplicateCellCount == 0) "No repeated planner cells were found."
                else "${report.duplicateCellCount} frame(s) repeat an already-covered planner cell.",
            evidence = mapOf("duplicate_frame_count" to report.duplicateCellCount),
            limitations = listOf("Repeated planner cells do not prove that the images are visually identical.")
        )

        val duplicateContentCount = manifest.frames.groupingBy { it.sha256.lowercase() }
            .eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
        checks += check(
            id = "image_content_duplicates",
            category = "integrity",
            outcome = if (duplicateContentCount == 0) PassportCheckOutcome.PASS else PassportCheckOutcome.WARN,
            severity = if (duplicateContentCount == 0) PassportCheckSeverity.INFO else PassportCheckSeverity.WARNING,
            summary = if (duplicateContentCount == 0) "No identical SHA-256 image records were found."
                else "$duplicateContentCount frame(s) share identical SHA-256 content.",
            evidence = mapOf("duplicate_frame_count" to duplicateContentCount),
            limitations = listOf("SHA-256 equality detects byte-identical files, not visually similar images with different encodings.")
        )

        val framesWithExif = manifest.frames.count { frame ->
            frame.cameraMetadata?.hasExifFields() == true
        }
        val missingExif = manifest.frames.size - framesWithExif
        checks += check(
            id = "exif_availability",
            category = "metadata",
            outcome = when {
                manifest.frames.isEmpty() -> PassportCheckOutcome.NOT_MEASURED
                missingExif > 0 -> PassportCheckOutcome.WARN
                else -> PassportCheckOutcome.PASS
            },
            severity = if (missingExif > 0) PassportCheckSeverity.WARNING else PassportCheckSeverity.INFO,
            summary = when {
                manifest.frames.isEmpty() -> "EXIF availability cannot be evaluated without frames."
                missingExif > 0 -> "$missingExif frame(s) have no readable EXIF metadata."
                else -> "Readable camera metadata is present for all frames."
            },
            evidence = mapOf("frames_with_exif" to framesWithExif, "frames_without_exif" to missingExif),
            limitations = listOf("EXIF availability does not establish camera calibration or pose accuracy.")
        )

        val dimensionFrames = manifest.frames.mapNotNull { frame ->
            val metadata = frame.cameraMetadata ?: return@mapNotNull null
            val width = metadata.imageWidth
            val height = metadata.imageHeight
            if (width != null && height != null) width to height else null
        }
        val dimensions = dimensionFrames.toSet()
        val dimensionOutcome = when {
            dimensionFrames.isEmpty() -> PassportCheckOutcome.NOT_MEASURED
            dimensions.size > 1 || dimensionFrames.size < manifest.frames.size -> PassportCheckOutcome.WARN
            else -> PassportCheckOutcome.PASS
        }
        checks += check(
            id = "image_dimensions_consistency",
            category = "metadata",
            outcome = dimensionOutcome,
            severity = if (dimensionOutcome == PassportCheckOutcome.WARN) PassportCheckSeverity.WARNING else PassportCheckSeverity.INFO,
            summary = when (dimensionOutcome) {
                PassportCheckOutcome.NOT_MEASURED -> "No complete image-dimension metadata is available."
                PassportCheckOutcome.WARN -> "Mixed image dimensions detected across frames."
                else -> "Known image dimensions are consistent."
            },
            evidence = mapOf(
                "known_dimension_pair_count" to dimensions.size,
                "known_frame_count" to dimensionFrames.size
            ),
            limitations = listOf("EXIF dimensions may differ from the post-rotation or cropped pixel dimensions consumed downstream.")
        )

        val manifestValid = try {
            CaptureManifestContract.validate(manifest)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
        checks += check(
            id = "manifest_integrity",
            category = "integrity",
            outcome = if (manifestValid) PassportCheckOutcome.PASS else PassportCheckOutcome.FAIL,
            severity = if (manifestValid) PassportCheckSeverity.INFO else PassportCheckSeverity.BLOCKER,
            summary = if (manifestValid) "Capture Manifest satisfies schema and structural validation."
                else "Capture Manifest failed structural validation.",
            evidence = mapOf("manifest_schema_version" to manifest.schemaVersion, "frame_count" to manifest.frames.size),
            limitations = listOf("Structural validation does not prove that source image bytes still match recorded hashes.")
        )

        val integrityVerified = manifest.frames.isNotEmpty() && imageIntegrity?.let {
            it.verified == true &&
                it.verifiedFrameCount == manifest.frames.size &&
                it.failedFrameCount == 0
        } == true
        val integrityOutcome = when {
            manifest.frames.isEmpty() || imageIntegrity == null || imageIntegrity.verified == null ->
                PassportCheckOutcome.NOT_MEASURED
            integrityVerified -> PassportCheckOutcome.PASS
            else -> PassportCheckOutcome.FAIL
        }
        checks += check(
            id = "image_file_integrity",
            category = "integrity",
            outcome = integrityOutcome,
            severity = if (integrityOutcome == PassportCheckOutcome.FAIL) PassportCheckSeverity.BLOCKER else PassportCheckSeverity.INFO,
            summary = when (integrityOutcome) {
                PassportCheckOutcome.PASS -> "All registered source images passed export-time size and SHA-256 verification."
                PassportCheckOutcome.FAIL -> "Source image integrity evidence is incomplete or one or more images failed verification."
                else -> "Source image bytes have not yet been verified during export."
            },
            evidence = mapOf(
                "verified_frame_count" to (imageIntegrity?.verifiedFrameCount ?: 0),
                "failed_frame_count" to (imageIntegrity?.failedFrameCount ?: 0),
                "reason_code" to (imageIntegrity?.reasonCode ?: when (integrityOutcome) {
                    PassportCheckOutcome.FAIL -> "INTEGRITY_EVIDENCE_MISMATCH"
                    PassportCheckOutcome.NOT_MEASURED -> "NOT_RUN"
                    else -> "NONE"
                })
            ),
            limitations = listOf("Hash verification establishes byte identity against the manifest, not authenticity of the capture event.")
        )

        listOf(
            "blur_focus" to "Motion blur and focus quality are not measured.",
            "feature_overlap" to "Feature overlap between adjacent images is not measured.",
            "occlusion_parallax" to "Occlusion, unseen surfaces and parallax sufficiency are not measured.",
            "camera_pose_calibration" to "Saved-image camera calibration and per-image pose accuracy are not measured.",
            "reconstruction_result" to "No photogrammetry reconstruction result is available in this Passport analysis."
        ).forEach { (id, summary) ->
            checks += check(
                id = id,
                category = when (id) {
                    "camera_pose_calibration" -> "geometry"
                    "reconstruction_result" -> "reconstruction"
                    else -> "image_quality"
                },
                outcome = PassportCheckOutcome.NOT_MEASURED,
                severity = PassportCheckSeverity.INFO,
                summary = summary,
                limitations = deferredLimitations
            )
        }

        val hasIntegrityFailure = checks.any {
            it.severity == PassportCheckSeverity.BLOCKER && it.outcome == PassportCheckOutcome.FAIL
        }
        val hasWarnings = checks.any { it.outcome == PassportCheckOutcome.WARN }
        val requiredMeasurementMissing = checks.any {
            it.checkId == "image_dimensions_consistency" && it.outcome == PassportCheckOutcome.NOT_MEASURED
        }
        val readiness = when {
            report.frameCount == 0 || report.coveragePercent < 35 || hasIntegrityFailure -> PassportReadiness.INSUFFICIENT
            report.coveragePercent < 80 || hasWarnings || !integrityVerified || requiredMeasurementMissing -> PassportReadiness.REVIEW
            else -> PassportReadiness.READY
        }

        val passport = CapturePassport(
            passportId = "passport-${manifest.sessionId}",
            generatedAt = generatedAt,
            session = CapturePassportSession(
                sessionId = manifest.sessionId,
                createdAt = manifest.createdAt,
                manifestSchemaVersion = manifest.schemaVersion,
                frameCount = manifest.frames.size
            ),
            analyzerId = "3dcap-capture-quality",
            analyzerVersion = PASSPORT_ANALYZER_VERSION,
            readiness = readiness,
            coverage = CapturePassportCoverage(
                coveredCells = report.coveredCells,
                totalCells = report.totalCells,
                percent = report.coveragePercent,
                duplicateCellCount = report.duplicateCellCount,
                missingCells = report.missingCells
            ),
            checks = checks,
            limitations = listOf(
                "Planner-cell coverage does not prove physical surface visibility.",
                "Blur, feature overlap, occlusion, parallax, calibration accuracy and reconstruction success are not measured."
            )
        )
        return CapturePassportContract.validate(passport, manifest)
    }

    private fun CameraCaptureMetadata.hasExifFields(): Boolean =
        imageWidth != null || imageHeight != null || orientation != null ||
            !cameraMake.isNullOrBlank() || !cameraModel.isNullOrBlank() ||
            focalLengthMm != null || focalLength35Mm != null || aperture != null ||
            exposureTimeSeconds != null || iso != null

    private fun check(
        id: String,
        category: String,
        outcome: PassportCheckOutcome,
        severity: PassportCheckSeverity,
        summary: String,
        evidence: Map<String, Any?> = emptyMap(),
        limitations: List<String> = emptyList(),
        remediation: String? = null
    ) = CapturePassportCheck(
        checkId = id,
        category = category,
        outcome = outcome,
        severity = severity,
        summary = summary,
        evidence = evidence,
        limitations = limitations,
        remediation = remediation,
        analyzerVersion = PASSPORT_ANALYZER_VERSION
    )
}
