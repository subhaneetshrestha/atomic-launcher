package io.github.subhaneetshrestha.atomic.background

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.subhaneetshrestha.atomic.util.Logs
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max

/** A small software copy of an image, in the form [ColorSampler] reads. */
data class Thumbnail(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Thumbnail && width == other.width && height == other.height && pixels.contentEquals(other.pixels))

    override fun hashCode(): Int = (width * 31 + height) * 31 + pixels.contentHashCode()
}

/**
 * Turning a downloaded file into something to draw. The decoded bitmap is never larger than it
 * needs to be to fill the window, which is what keeps a 4000-pixel photograph from costing sixty
 * megabytes on a phone that has to stay alive as the home screen.
 *
 * From Android 9 the pixels go straight to graphics memory (a hardware bitmap), so they cost the
 * app process nothing beyond a handle; the small copy used to judge legibility is decoded
 * separately, in software, because hardware bitmaps cannot be read back.
 */
object ImageDecoders {
    /** The image scaled to cover [width] × [height], or null when the file is not an image at all. */
    fun decode(
        file: File,
        width: Int,
        height: Int,
    ): Bitmap? {
        if (!file.isFile || width <= 0 || height <= 0) return null
        return if (Build.VERSION.SDK_INT >= 28) decodeModern(file, width, height) else decodeLegacy(file, width, height)
    }

    fun thumbnail(
        file: File,
        maxSide: Int = ColorSampler.MAX_THUMBNAIL,
    ): Thumbnail? {
        val bitmap =
            if (Build.VERSION.SDK_INT >= 28) {
                decodeModern(file, maxSide, maxSide, hardware = false)
            } else {
                decodeLegacy(file, maxSide, maxSide)
            } ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            Thumbnail(pixels, bitmap.width, bitmap.height)
        } catch (e: IllegalStateException) {
            Logs.w(TAG, "could not read the thumbnail back", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(
        file: File,
        width: Int,
        height: Int,
        hardware: Boolean = true,
    ): Bitmap? =
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val size = coverSize(info.size.width, info.size.height, width, height)
                decoder.setTargetSize(size.first, size.second)
                decoder.allocator =
                    if (hardware) ImageDecoder.ALLOCATOR_HARDWARE else ImageDecoder.ALLOCATOR_SOFTWARE
                // A file that is almost an image is still worth drawing; the alternative is nothing.
                decoder.isUnpremultipliedRequired = false
                decoder.setOnPartialImageListener { true }
            }
        } catch (e: IOException) {
            Logs.w(TAG, "could not decode ${file.name}", e)
            null
        } catch (e: RuntimeException) {
            // ImageDecoder throws DecodeException (a RuntimeException) for a file that is not an image.
            Logs.w(TAG, "not an image: ${file.name}", e)
            null
        }

    private fun decodeLegacy(
        file: File,
        width: Int,
        height: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        return rotateByExif(file, decoded)
    }

    /** The smallest size that still covers the window, so the centre crop never has to stretch. */
    private fun coverSize(
        imageWidth: Int,
        imageHeight: Int,
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        if (imageWidth <= 0 || imageHeight <= 0) return width to height
        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        if (scale >= 1f) return imageWidth to imageHeight
        return ceil(imageWidth * scale).toInt().coerceAtLeast(1) to
            ceil(imageHeight * scale).toInt().coerceAtLeast(1)
    }

    /** Powers of two only, which is all BitmapFactory honours. */
    private fun sampleSize(
        imageWidth: Int,
        imageHeight: Int,
        width: Int,
        height: Int,
    ): Int {
        var sample = 1
        while (imageWidth / (sample * 2) >= width && imageHeight / (sample * 2) >= height) sample *= 2
        return sample
    }

    /**
     * Below Android 9 the decoder ignores the orientation a camera wrote into the file, so a
     * photograph taken sideways would be shown sideways.
     *
     * This is the platform ExifInterface rather than the support library's: it reads one field of
     * a file the launcher downloaded itself, which is not worth another dependency.
     */
    @SuppressLint("ExifInterface")
    private fun rotateByExif(
        file: File,
        bitmap: Bitmap,
    ): Bitmap {
        val degrees =
            try {
                when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: IOException) {
                0f
            }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private const val TAG = "ImageDecoders"
}
