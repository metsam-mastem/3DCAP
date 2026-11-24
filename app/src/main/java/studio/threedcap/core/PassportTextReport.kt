package studio.threedcap.core

object PassportTextReport {
    fun render(passport: CapturePassport): String = buildString {
        appendLine("3DCAP CAPTURE PASSPORT")
        appendLine("======================")
        appendLine("Passport: ${passport.passportId}")
        appendLine("Session: ${passport.session.sessionId}")
        appendLine("Generated: ${passport.generatedAt}")
        appendLine("Analyzer: ${passport.analyzerId} ${passport.analyzerVersion}")
        appendLine("Readiness: ${passport.readiness}")
        appendLine()
        appendLine("COVERAGE")
        appendLine("--------")
        appendLine("${passport.coverage.percent}% — ${passport.coverage.coveredCells}/${passport.coverage.totalCells} planner cells")
        appendLine("Repeated planner cells: ${passport.coverage.duplicateCellCount}")
        appendLine("Missing planner targets:")
        if (passport.coverage.missingCells.isEmpty()) {
            appendLine("- none")
        } else {
            passport.coverage.missingCells.forEach {
                appendLine("- yaw ${it.yawDegrees}°, pitch band ${it.pitchBand}°")
            }
        }
        appendLine()
        appendLine("QUALITY EVIDENCE")
        appendLine("----------------")
        passport.checks.forEach { check ->
            appendLine("[${check.outcome}] ${check.checkId} — ${check.summary}")
            if (check.evidence.isNotEmpty()) appendLine("  Evidence: ${check.evidence}")
            check.limitations.forEach { appendLine("  Limitation: $it") }
            check.remediation?.let { appendLine("  Action: $it") }
        }
        appendLine()
        appendLine("LIMITATIONS")
        appendLine("-----------")
        passport.limitations.forEach { appendLine("- $it") }
        appendLine()
        appendLine("READY means only that the listed checks permit handoff to the next pipeline stage.")
        appendLine("It does not guarantee surface completeness, calibrated camera poses, or successful reconstruction.")
    }
}
