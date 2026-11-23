package org.three_dcap.capture

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale

data class CapturedAsset(val fileName: String, val mediaType: String, val format: String, val byteSize: Long, val sha256: String)
class AssetIntakeException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Read-only streaming intake for locally captured JPEG, PNG, and WebP images. */
object AssetIntake {
    private val signatures = mapOf("jpg" to "jpeg", "jpeg" to "jpeg", "png" to "png", "webp" to "webp")

    fun inspect(path: Path, maxBytes: Long = 100L * 1024 * 1024): CapturedAsset {
        require(maxBytes > 0) { "maxBytes must be positive" }
        if (Files.isSymbolicLink(path)) throw AssetIntakeException("symbolic-link inputs are not accepted")
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw AssetIntakeException("input must be a regular file")
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val expected = signatures[extension] ?: throw AssetIntakeException("unsupported image extension")
        val beforeSize = Files.size(path)
        if (beforeSize <= 0) throw AssetIntakeException("empty files are not accepted")
        if (beforeSize > maxBytes) throw AssetIntakeException("file exceeds configured size limit")

        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = ByteArray(16)
        var prefixCount = 0
        var total = 0L
        try {
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (prefixCount < prefix.size) {
                        val copy = minOf(read, prefix.size - prefixCount)
                        System.arraycopy(buffer, 0, prefix, prefixCount, copy)
                        prefixCount += copy
                    }
                    total += read
                    if (total > maxBytes) throw AssetIntakeException("file exceeds configured size limit")
                    digest.update(buffer, 0, read)
                }
            }
        } catch (error: AssetIntakeException) {
            throw error
        } catch (error: Exception) {
            throw AssetIntakeException("could not read image", error)
        }
        if (total != beforeSize || Files.size(path) != beforeSize) throw AssetIntakeException("file changed while it was being inspected")
        val detected = detectFormat(prefix, prefixCount) ?: throw AssetIntakeException("file signature is not a supported image")
        if (detected != expected) throw AssetIntakeException("extension/signature mismatch")
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return CapturedAsset(path.fileName.toString(), "image", detected, total, hex)
    }

    private fun detectFormat(bytes: ByteArray, size: Int): String? {
        if (size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) return "jpeg"
        if (size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return "png"
        if (size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return "webp"
        return null
    }
}
