package studio.threedcap.core

import org.json.JSONArray
import org.json.JSONObject

object CapturePassportJson {
    fun encode(passport: CapturePassport): String {
        CapturePassportContract.validate(passport)
        val missing = JSONArray().apply {
            passport.coverage.missingCells.forEach { target ->
                put(JSONObject().put("yaw_degrees", target.yawDegrees).put("pitch_degrees", target.pitchBand))
            }
        }
        val checks = JSONArray().apply {
            passport.checks.forEach { check ->
                val evidence = JSONObject()
                check.evidence.forEach { (key, value) -> evidence.put(key, value ?: JSONObject.NULL) }
                put(JSONObject()
                    .put("check_id", check.checkId)
                    .put("category", check.category)
                    .put("outcome", check.outcome.name)
                    .put("severity", check.severity.name)
                    .put("summary", check.summary)
                    .put("evidence", evidence)
                    .put("limitations", JSONArray(check.limitations))
                    .put("remediation", check.remediation ?: JSONObject.NULL)
                    .put("analyzer_version", check.analyzerVersion))
            }
        }
        return JSONObject()
            .put("schema_version", passport.schemaVersion)
            .put("capture_plan_id", passport.capturePlanId)
            .put("passport_id", passport.passportId)
            .put("generated_at", passport.generatedAt)
            .put("session", JSONObject()
                .put("session_id", passport.session.sessionId)
                .put("created_at", passport.session.createdAt)
                .put("manifest_schema_version", passport.session.manifestSchemaVersion)
                .put("frame_count", passport.session.frameCount))
            .put("analyzer", JSONObject()
                .put("id", passport.analyzerId)
                .put("version", passport.analyzerVersion))
            .put("readiness", passport.readiness.name)
            .put("coverage", JSONObject()
                .put("covered_cells", passport.coverage.coveredCells)
                .put("total_cells", passport.coverage.totalCells)
                .put("percent", passport.coverage.percent)
                .put("duplicate_cell_count", passport.coverage.duplicateCellCount)
                .put("missing_cells", missing))
            .put("checks", checks)
            .put("limitations", JSONArray(passport.limitations))
            .toString(2)
    }

    fun decode(json: String): CapturePassport {
        require(json.length <= 2_000_000) { "Passport JSON exceeds the size limit" }
        val root = JSONObject(json)
        val session = root.getJSONObject("session")
        val analyzer = root.getJSONObject("analyzer")
        val coverage = root.getJSONObject("coverage")
        val missingJson = coverage.getJSONArray("missing_cells")
        require(missingJson.length() <= 108) { "Too many missing planner cells" }
        val missing = (0 until missingJson.length()).map { index ->
            val item = missingJson.getJSONObject(index)
            CaptureTarget(item.getInt("yaw_degrees"), item.getInt("pitch_degrees"))
        }
        val checksJson = root.getJSONArray("checks")
        require(checksJson.length() <= 100) { "Too many quality checks" }
        val checks = (0 until checksJson.length()).map { index ->
            val item = checksJson.getJSONObject(index)
            val evidenceObject = item.optJSONObject("evidence") ?: JSONObject()
            val evidence = linkedMapOf<String, Any?>()
            val keys = evidenceObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                evidence[key] = fromJsonValue(evidenceObject.get(key))
            }
            CapturePassportCheck(
                checkId = item.getString("check_id"),
                category = item.getString("category"),
                outcome = PassportCheckOutcome.valueOf(item.getString("outcome")),
                severity = PassportCheckSeverity.valueOf(item.getString("severity")),
                summary = item.getString("summary"),
                evidence = evidence,
                limitations = item.getJSONArray("limitations").toStringList(),
                remediation = item.optString("remediation").takeIf { it.isNotBlank() && it != "null" },
                analyzerVersion = item.getString("analyzer_version")
            )
        }
        val passport = CapturePassport(
            schemaVersion = root.getInt("schema_version"),
            capturePlanId = root.getString("capture_plan_id"),
            passportId = root.getString("passport_id"),
            generatedAt = root.getString("generated_at"),
            session = CapturePassportSession(
                sessionId = session.getString("session_id"),
                createdAt = session.getString("created_at"),
                manifestSchemaVersion = session.getInt("manifest_schema_version"),
                frameCount = session.getInt("frame_count")
            ),
            analyzerId = analyzer.getString("id"),
            analyzerVersion = analyzer.getString("version"),
            readiness = PassportReadiness.valueOf(root.getString("readiness")),
            coverage = CapturePassportCoverage(
                coveredCells = coverage.getInt("covered_cells"),
                totalCells = coverage.getInt("total_cells"),
                percent = coverage.getInt("percent"),
                duplicateCellCount = coverage.getInt("duplicate_cell_count"),
                missingCells = missing
            ),
            checks = checks,
            limitations = root.getJSONArray("limitations").toStringList()
        )
        return CapturePassportContract.validate(passport)
    }

    private fun JSONArray.toStringList(): List<String> {
        require(length() <= 100) { "Array exceeds the item limit" }
        return (0 until length()).map { getString(it).also { value ->
            require(value.length <= 1000) { "String exceeds the length limit" }
        } }
    }

    private fun fromJsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> {
            val map = linkedMapOf<String, Any?>()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = fromJsonValue(value.get(key))
            }
            map
        }
        is JSONArray -> (0 until value.length()).map { fromJsonValue(value.get(it)) }
        else -> value
    }
}
