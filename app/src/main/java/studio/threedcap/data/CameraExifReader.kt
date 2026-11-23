package studio.threedcap.data

import androidx.exifinterface.media.ExifInterface
import studio.threedcap.core.CameraCaptureMetadata
import java.io.File

/**
 * Reads camera facts recorded by the camera pipeline.
 * Missing EXIF attributes remain null; this reader never estimates calibration values.
 */
object CameraExifReader {
    fun read(file: File): CameraCaptureMetadata? {
        if (!file.isFile) return null
        return try {
            val exif = ExifInterface(file)
            val metadata = CameraCaptureMetadata(
                imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 },
                imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 },
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0).takeIf { it in 1..8 },
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.takeIf { it.isNotBlank() },
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() },
                focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    .toFloat().takeIf { it.isFinite() && it > 0f },
                focalLength35Mm = exif.getAttributeInt(ExifInterface.TAG焦_LENGTH_IN_35MM_FILM, 0)
                    .takeIf { it > 0 },
                aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    .toFloat().takeIf { it.isFinite() && it > 0f },
                exposureTimeSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    .takeIf { it.isFinite() && it > 0.0 },
                iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                    .takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it > 0 }
            )
            metadata.takeIf {
                it.imageWidth != null || it.imageHeight != null || it.orientation != null ||
                    it.cameraMake != null || it.cameraModel != null || it.focalLengthMm != null ||
                    it.focalLength35Mm != null || it.aperture != null ||
                    it.exposureTimeSeconds != null || it.iso != null
            }
        } catch (_: Exception) {
            null
        }
    }
}
