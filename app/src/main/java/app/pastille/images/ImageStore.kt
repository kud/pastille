package app.pastille.images

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

class ImageImportException(val reason: String) : IOException(reason)

data class StoredImage(
    val fileName: String,
    val width: Int,
    val height: Int,
    val alreadyExisted: Boolean,
)

class ImageStore(private val appContext: Context) {

    fun imagesDir(): File = File(appContext.filesDir, "images")

    fun fileFor(name: String): File = File(imagesDir(), name)

    fun delete(name: String) {
        runCatching { fileFor(name).delete() }
    }

    suspend fun import(uri: Uri): StoredImage = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val dir = imagesDir().apply { mkdirs() }
        querySize(resolver, uri)?.let { size ->
            if (size > MAX_SOURCE_BYTES) throw ImageImportException("Image too large")
        }
        val declaredMime = try {
            resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase()
        } catch (_: Exception) {
            null
        }
        val sourceTmp = File(dir, ".tmp-${UUID.randomUUID()}")
        val outTmp = File(dir, ".tmp-${UUID.randomUUID()}")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { raw ->
                DigestInputStream(raw, digest).use { stream ->
                    sourceTmp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_SOURCE_BYTES) throw ImageImportException("Image too large")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } ?: throw ImageImportException("Unreadable image")
            val bounds = boundsOf(sourceTmp) ?: throw ImageImportException("Unreadable image")
            val mime = declaredMime?.takeIf { it.isNotBlank() } ?: bounds.outMimeType?.lowercase()
            val ext = extensionFor(mime ?: "image/jpeg")
            val fileName = hashName(digest.digest(), ext)
            val target = File(dir, fileName)
            if (target.exists()) {
                val existing = boundsOf(target) ?: throw ImageImportException("Unreadable image")
                return@withContext StoredImage(fileName, existing.outWidth, existing.outHeight, true)
            }
            val width = bounds.outWidth
            val height = bounds.outHeight
            when {
                mime == "image/gif" -> {
                    if (sourceTmp.length() > MAX_GIF_BYTES) throw ImageImportException("Image too large")
                    sourceTmp.copyTo(outTmp, overwrite = true)
                }
                mime == "image/png" && maxOf(width, height) <= MAX_EDGE -> {
                    sourceTmp.copyTo(outTmp, overwrite = true)
                }
                else -> transcode(sourceTmp, outTmp, mime == "image/png")
            }
            if (!outTmp.renameTo(target) && !target.exists()) {
                outTmp.copyTo(target)
            }
            val stored = boundsOf(target) ?: throw ImageImportException("Unreadable image")
            StoredImage(fileName, stored.outWidth, stored.outHeight, false)
        } finally {
            sourceTmp.delete()
            outTmp.delete()
        }
    }

    suspend fun sweepOrphans(referenced: Set<String>, now: Long) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = imagesDir()
            val present = dir.listFiles()?.filter { it.isFile }?.map { it.name to it.lastModified() }
                ?: emptyList()
            orphans(present, referenced, now).forEach { File(dir, it).delete() }
        }
        runCatching {
            val shared = File(appContext.cacheDir, "shared")
            val files = shared.listFiles()?.filter { it.isFile }?.map { it.name to it.lastModified() }
                ?: emptyList()
            staleShared(files, now).forEach { File(shared, it).delete() }
        }
    }

    fun loadThumbnail(name: String, targetPx: Int): Bitmap? {
        val key = "$name@$targetPx"
        thumbnails.get(key)?.let { return it }
        val file = fileFor(name)
        if (!file.exists()) return null
        val bounds = boundsOf(file) ?: return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx &&
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }
        val bitmap = try {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (_: Exception) {
            null
        } ?: return null
        thumbnails.put(key, bitmap)
        return bitmap
    }

    private fun transcode(source: File, dest: File, keepPng: Boolean) {
        val bounds = boundsOf(source) ?: throw ImageImportException("Unreadable image")
        val (targetWidth, targetHeight) = targetSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
            },
        ) ?: throw ImageImportException("Unreadable image")
        var current = decoded
        try {
            if (current.width != targetWidth || current.height != targetHeight) {
                val scaled = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
                if (scaled !== current) {
                    current.recycle()
                    current = scaled
                }
            }
            if (!keepPng) {
                val oriented = applyOrientation(current, source)
                if (oriented !== current) {
                    current.recycle()
                    current = oriented
                }
            }
            dest.outputStream().use { out ->
                if (keepPng) {
                    current.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    current.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        } finally {
            current.recycle()
        }
    }

    private fun applyOrientation(bitmap: Bitmap, source: File): Bitmap {
        val orientation = try {
            FileInputStream(source).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (_: Exception) {
            return bitmap
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun boundsOf(file: File): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            return null
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                cursor.getLong(0).takeIf { it >= 0 }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MAX_EDGE = 2048
        const val MAX_SOURCE_BYTES = 40L * 1024 * 1024
        const val MAX_GIF_BYTES = 15L * 1024 * 1024

        private val thumbnails = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        fun forContext(context: Context): ImageStore =
            ImageStore(context.applicationContext)
    }
}
