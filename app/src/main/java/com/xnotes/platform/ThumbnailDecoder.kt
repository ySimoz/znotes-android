package com.xnotes.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Provides memory-capped, asynchronous image downsampling for thumbnails.
 * Offloading image decodes to background workers prevents UI frame drops on 144Hz displays.
 */
object ThumbnailDecoder {

    /** Default maximum bounding box dimension for custom asset picker thumbnails. */
    const val DEFAULT_THUMB_SIZE_PX = 256

    /**
     * Calculates the largest power-of-two [inSampleSize] that keeps both decoded dimensions
     * larger than or equal to the requested [reqWidth] and [reqHeight].
     */
    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1

        var inSampleSize = 1
        while (width / (inSampleSize * 2) >= reqWidth && height / (inSampleSize * 2) >= reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /** Overload computing sample size directly from populated [BitmapFactory.Options]. */
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int =
        calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)

    /**
     * Inspects magic header bytes to verify the file represents a valid, recognized image format.
     * Prevents invoking native decoders on non-image or corrupt binary payloads.
     */
    fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false

        // PNG signature: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true

        // JPEG SOI marker: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true

        // GIF signature: GIF8
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()) return true

        // BMP file header: BM
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return true

        // WebP container: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()) return true

        return false
    }

    /**
     * Decodes a downsampled [Bitmap] from raw image [bytes] targeting [reqWidth] x [reqHeight].
     * Never decodes full-resolution images directly into memory for small thumbnail strips.
     */
    fun decodeSampledBitmap(
        bytes: ByteArray,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? {
        if (bytes.isEmpty() || reqWidth <= 0 || reqHeight <= 0) return null
        if (!isValidImageBytes(bytes)) return null

        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    /**
     * Decodes a downsampled [Bitmap] from a local [file] targeting [reqWidth] x [reqHeight].
     * Inspects dimensions first so unscaled native resolution is never allocated in heap.
     */
    fun decodeSampledBitmap(
        file: File,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? {
        if (!file.exists() || !file.isFile || file.length() < 4L || reqWidth <= 0 || reqHeight <= 0) return null
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.inputStream().use { stream -> BitmapFactory.decodeStream(stream, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            file.inputStream().use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        }.getOrNull()
    }

    /**
     * Decodes a downsampled [Bitmap] from a content [uri] targeting [reqWidth] x [reqHeight].
     */
    fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? {
        if (reqWidth <= 0 || reqHeight <= 0) return null
        val resolver = context.contentResolver
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        }.getOrNull()
    }

    /**
     * Asynchronously decodes downsampled thumbnail bitmap bytes on [Dispatchers.IO].
     * Guarantees that decoding work never blocks the main rendering thread.
     */
    suspend fun decodeThumbnailAsync(
        bytes: ByteArray,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decodeSampledBitmap(bytes, reqWidth, reqHeight)
    }

    /**
     * Asynchronously decodes downsampled thumbnail bitmap from [file] on [Dispatchers.IO].
     */
    suspend fun decodeThumbnail(
        file: File,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decodeSampledBitmap(file, reqWidth, reqHeight)
    }

    /**
     * Asynchronously decodes downsampled thumbnail bitmap from [uri] on [Dispatchers.IO].
     */
    suspend fun decodeThumbnail(
        context: Context,
        uri: Uri,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decodeSampledBitmap(context, uri, reqWidth, reqHeight)
    }

    /** Overload matching [decodeThumbnailAsync] naming for file sources. */
    suspend fun decodeThumbnailAsync(
        file: File,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? = decodeThumbnail(file, reqWidth, reqHeight)

    /** Overload matching [decodeThumbnailAsync] naming for uri sources. */
    suspend fun decodeThumbnailAsync(
        context: Context,
        uri: Uri,
        reqWidth: Int = DEFAULT_THUMB_SIZE_PX,
        reqHeight: Int = DEFAULT_THUMB_SIZE_PX,
    ): Bitmap? = decodeThumbnail(context, uri, reqWidth, reqHeight)
}
