package studio.threedcap.data

import org.json.JSONArray
import org.json.JSONObject
import studio.threedcap.core.CaptureManifest
import studio.threedcap.core.CaptureManifestContract
import studio.threedcap.core.CapturedFrameRecord
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * App-private, crash-conscious capture manifest store.
 * Images remain in the captures directory; the manifest records relative paths and hashes.
 */
class CaptureSessionStore(private val directory: File) {
    private val manifestFile = File(directory, "session.json")
    private val backupFile = File(directory, "session.json.bak")
    private val temporaryFile = File(directory, "session.json.tmp")

    init {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create the private capture directory")
        }
        require(directory.isDirectory) { "Capture path is not a directory" }
        recoverDeletionTransactions()
    }

    @Synchronized
    fun load(): CaptureManifest? {
        val primary = readValid(manifestFile)
        if (primary != null) return primary
        val backup = readValid(backupFile) ?: return null
        if (manifestFile.exists()) manifestFile.delete()
        backupFile.renameTo(manifestFile)
        return backup
    }

    /**
     * Reconcile staged deletions left behind by a process/device crash.
     * If the durable manifest still references a frame, restore its staged image.
     * If the manifest no longer references it, finish the deletion.
     * When neither manifest copy is valid, preserve tombstones rather than guessing.
     */
    private fun recoverDeletionTransactions() {
        val durableManifest = readValid(manifestFile) ?: readValid(backupFile) ?: return
        val referenced = durableManifest.frames.map { it.fileName }.toSet()
        val tombstones = directory.listFiles { file -> file.isFile && file.name.endsWith(".deleting") }
            ?: return
        tombstones.forEach { tombstone ->
            val originalName = tombstone.name.removeSuffix(".deleting")
            if (!originalName.matches(Regex("^3dcap_[A-Za-z0-9_-]+\\.jpg$"))) return@forEach
            val original = File(directory, originalName)
            when {
                originalName in referenced && !original.exists() -> {
                    if (!tombstone.renameTo(original)) {
                        throw IllegalStateException("Could not recover staged frame deletion: $originalName")
                    }
                }
                else -> {
                    if (!tombstone.delete()) {
                        throw IllegalStateException("Could not clean staged frame deletion: $originalName")
                    }
                }
            }
        }
    }

    @Synchronized
    fun record(file: File, yawDegrees: Float, pitchDegrees: Float): CaptureManifest {
        val root = directory.canonicalFile
        val image = file.canonicalFile
        require(image.parentFile == root) { "Captured image must be directly inside the private capture directory" }
        require(image.isFile && !FilesCompat.isSymbolicLink(file)) { "Captured image must be a regular non-symlink file" }
        require(image.name.matches(Regex("^3dcap_[A-Za-z0-9_-]+\\.jpg$"))) { "Unexpected captured image filename" }

        val sizeBefore = image.length()
        require(sizeBefore > 0) { "Captured image is empty" }
        val digest = sha256(image)
        require(image.length() == sizeBefore) { "Captured image changed while hashing" }

        val current = load() ?: CaptureManifest(
            sessionId = "capture-" + UUID.randomUUID().toString(),
            createdAt = Instant.now().toString()
        )
        val frame = CapturedFrameRecord(
            sequence = (current.frames.maxOfOrNull { it.sequence } ?: -1) + 1,
            fileName = image.name,
            relativePath = image.name,
            yawDegrees = normalizeYaw(yawDegrees),
            pitchDegrees = pitchDegrees,
            byteSize = sizeBefore,
            sha256 = digest,
            capturedAt = Instant.now().toString()
        )
        val updated = CaptureManifestContract.validate(current.copy(frames = current.frames + frame))
        writeAtomically(updated)
        return updated
    }

    @Synchronized
    fun removeFrame(fileName: String): CaptureManifest {
        require(fileName.matches(Regex("^3dcap_[A-Za-z0-9_-]+\\.jpg$"))) { "Invalid frame filename" }
        val current = load() ?: throw IllegalStateException("No capture session exists")
        val target = current.frames.firstOrNull { it.fileName == fileName }
            ?: throw IllegalArgumentException("Frame does not exist in this session")
        val updated = CaptureManifestContract.validate(current.copy(frames = current.frames.filterNot { it.fileName == fileName }))
        val image = File(directory, target.fileName)
        require(image.canonicalFile.parentFile == directory.canonicalFile && !FilesCompat.isSymbolicLink(image)) {
            "Frame path is unsafe"
        }
        val tombstone = File(directory, target.fileName + ".deleting")
        if (tombstone.exists()) throw IllegalStateException("A previous frame deletion needs cleanup")
        val moved = image.exists()
        if (moved && !image.renameTo(tombstone)) {
            throw IllegalStateException("Could not stage frame deletion")
        }
        try {
            writeAtomically(updated)
        } catch (error: Exception) {
            if (moved && tombstone.exists()) tombstone.renameTo(image)
            throw error
        }
        if (moved && tombstone.exists() && !tombstone.delete()) {
            throw IllegalStateException("Frame was removed from the manifest; staged image cleanup is pending")
        }
        return updated
    }

    @Synchronized
    fun exportManifestJson(): String {
        val current = load() ?: throw IllegalStateException("No capture session exists")
        return encode(CaptureManifestContract.validate(current))
    }

    @Synchronized
    fun exportManifestCsv(): String {
        val current = CaptureManifestContract.validate(load() ?: throw IllegalStateException("No capture session exists"))
        val header = "schema_version,session_id,created_at,sequence,file_name,relative_path,yaw_degrees,pitch_degrees,byte_size,sha256,captured_at"
        val rows = current.frames.sortedBy { it.sequence }.map { frame ->
            listOf(
                current.schemaVersion.toString(),
                csv(current.sessionId),
                csv(current.createdAt),
                frame.sequence.toString(),
                csv(frame.fileName),
                csv(frame.relativePath),
                frame.yawDegrees.toString(),
                frame.pitchDegrees.toString(),
                frame.byteSize.toString(),
                frame.sha256,
                csv(frame.capturedAt)
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    @Synchronized
    fun writeDatasetZip(output: OutputStream) {
        val current = CaptureManifestContract.validate(load() ?: throw IllegalStateException("No capture session exists"))
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(encode(current).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("COLMAP_IMPORT.txt"))
            zip.write(
                """
                3DCAP -> COLMAP input package

                The images/ directory contains the captured source photographs.
                manifest.json is the 3DCAP session manifest. Its yaw/pitch values
                are capture-planner targets, NOT calibrated camera extrinsics or
                camera poses. This package intentionally does not fabricate
                cameras.txt, images.txt, or points3D.txt.

                Example COLMAP CLI workflow (run from this extracted directory):

                  colmap feature_extractor --database_path database.db --image_path images
                  colmap exhaustive_matcher --database_path database.db
                  mkdir -p sparse
                  colmap mapper --database_path database.db --image_path images --output_path sparse

                Use a COLMAP build whose CLI supports these commands/options.
                Inspect camera grouping and reconstruction quality before use.
                A sparse model is produced only if image overlap, texture,
                calibration assumptions, and matching are sufficient.

                For metric or pose-accurate reconstruction, capture calibrated
                camera intrinsics and a reliable per-image camera pose source.
                The current 3DCAP yaw/pitch guidance is not a substitute.
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            current.frames.sortedBy { it.sequence }.forEach { frame ->
                val image = File(directory, frame.fileName)
                require(image.canonicalFile.parentFile == directory.canonicalFile && image.isFile) {
                    "Missing or unsafe image file: ${frame.fileName}"
                }
                require(image.length() == frame.byteSize) {
                    "Image size check failed: ${frame.fileName}"
                }
                zip.putNextEntry(ZipEntry("images/${frame.fileName}"))
                val copiedDigest = MessageDigest.getInstance("SHA-256")
                var copiedBytes = 0L
                FileInputStream(image).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            zip.write(buffer, 0, read)
                            copiedDigest.update(buffer, 0, read)
                            copiedBytes += read
                        }
                    }
                }
                zip.closeEntry()
                val copiedSha256 = copiedDigest.digest().joinToString("") { "%02x".format(it) }
                require(copiedBytes == frame.byteSize && copiedSha256 == frame.sha256) {
                    "Image changed during dataset export: ${frame.fileName}"
                }
            }
            zip.finish()
        }
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"" + escaped + "\""
    }

    private fun writeAtomically(manifest: CaptureManifest) {
        val json = encode(manifest).toByteArray(Charsets.UTF_8)
        FileOutputStream(temporaryFile).use { output ->
            output.write(json)
            output.fd.sync()
        }
        if (backupFile.exists() && !backupFile.delete()) {
            temporaryFile.delete()
            throw IllegalStateException("Could not rotate the previous capture backup")
        }
        val hadPrimary = manifestFile.exists()
        if (hadPrimary && !manifestFile.renameTo(backupFile)) {
            temporaryFile.delete()
            throw IllegalStateException("Could not preserve the previous capture manifest")
        }
        if (!temporaryFile.renameTo(manifestFile)) {
            if (hadPrimary && backupFile.exists()) backupFile.renameTo(manifestFile)
            temporaryFile.delete()
            throw IllegalStateException("Could not install the new capture manifest")
        }
    }

    private fun readValid(file: File): CaptureManifest? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val framesJson = json.getJSONArray("frames")
            val frames = (0 until framesJson.length()).map { index ->
                val item = framesJson.getJSONObject(index)
                CapturedFrameRecord(
                    sequence = item.getInt("sequence"),
                    fileName = item.getString("file_name"),
                    relativePath = item.getString("relative_path"),
                    yawDegrees = item.getDouble("yaw_degrees").toFloat(),
                    pitchDegrees = item.getDouble("pitch_degrees").toFloat(),
                    byteSize = item.getLong("byte_size"),
                    sha256 = item.getString("sha256"),
                    capturedAt = item.getString("captured_at")
                )
            }
            CaptureManifestContract.validate(
                CaptureManifest(
                    schemaVersion = json.getInt("schema_version"),
                    sessionId = json.getString("session_id"),
                    createdAt = json.getString("created_at"),
                    frames = frames
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(manifest: CaptureManifest): String {
        val frames = JSONArray()
        manifest.frames.forEach { frame ->
            frames.put(JSONObject()
                .put("sequence", frame.sequence)
                .put("file_name", frame.fileName)
                .put("relative_path", frame.relativePath)
                .put("yaw_degrees", frame.yawDegrees.toDouble())
                .put("pitch_degrees", frame.pitchDegrees.toDouble())
                .put("byte_size", frame.byteSize)
                .put("sha256", frame.sha256)
                .put("captured_at", frame.capturedAt))
        }
        return JSONObject()
            .put("schema_version", manifest.schemaVersion)
            .put("session_id", manifest.sessionId)
            .put("created_at", manifest.createdAt)
            .put("frames", frames)
            .toString(2)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeYaw(value: Float): Float {
        require(value.isFinite() && value in 0f..360f) { "Yaw must be between 0 and 360 degrees" }
        return value
    }

    private object FilesCompat {
        fun isSymbolicLink(file: File): Boolean = try {
            java.nio.file.Files.isSymbolicLink(file.toPath())
        } catch (_: Exception) {
            false
        }
    }
}
