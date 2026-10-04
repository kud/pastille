package app.pastille.ime

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat

data class ImageItem(
    val uri: Uri,
    val displayName: String,
    val dateAddedSeconds: Long = 0,
)

data class ImageSource(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val latestDateAdded: Long,
    val isScreenshots: Boolean,
)

data class SourceRow(
    val bucketId: Long,
    val name: String?,
    val dateAdded: Long,
    val path: String?,
)

fun groupSources(rows: List<SourceRow>): List<ImageSource> {
    return rows.groupBy { it.bucketId }.map { (bucketId, group) ->
        ImageSource(
            bucketId = bucketId,
            name = group.firstOrNull { !it.name.isNullOrBlank() }?.name ?: "Unnamed",
            count = group.size,
            latestDateAdded = group.maxOf { it.dateAdded },
            isScreenshots = group.any { it.path?.contains("Screenshots", ignoreCase = true) == true },
        )
    }.sortedByDescending { it.latestDateAdded }
}

fun orderForChips(sources: List<ImageSource>, maxOthers: Int = 3): List<ImageSource> {
    val ordered = mutableListOf<ImageSource>()
    fun take(source: ImageSource?) {
        if (source != null && ordered.none { it.bucketId == source.bucketId }) {
            ordered.add(source)
        }
    }
    take(sources.filter { it.isScreenshots }.maxByOrNull { it.latestDateAdded })
    take(sources.firstOrNull { it.name.equals("Camera", ignoreCase = true) })
    take(sources.firstOrNull { it.name.equals("Download", ignoreCase = true) || it.name.equals("Downloads", ignoreCase = true) })
    sources.sortedByDescending { it.latestDateAdded }
        .filter { candidate -> ordered.none { it.bucketId == candidate.bucketId } }
        .take(maxOthers)
        .forEach { ordered.add(it) }
    return ordered
}

fun isDefaultSource(source: ImageSource): Boolean =
    source.isScreenshots ||
        source.name.equals("Camera", ignoreCase = true) ||
        source.name.equals("Download", ignoreCase = true) ||
        source.name.equals("Downloads", ignoreCase = true)

// Until the user picks, only Screenshots, Camera and Download show; once they pick, only what they
// ticked, so a folder that appears later stays hidden until chosen.
fun visibleSources(sources: List<ImageSource>, enabledIds: Set<Long>?): List<ImageSource> {
    val chosen = if (enabledIds == null) sources.filter(::isDefaultSource) else sources.filter { it.bucketId in enabledIds }
    val preferred = orderForChips(chosen, maxOthers = 0)
    return preferred + chosen.filter { source -> preferred.none { it.bucketId == source.bucketId } }
}

fun resolveSource(sources: List<ImageSource>, savedBucketId: Long?): ImageSource? {
    if (savedBucketId != null) {
        sources.find { it.bucketId == savedBucketId }?.let { return it }
    }
    sources.filter { it.isScreenshots }.maxByOrNull { it.latestDateAdded }?.let { return it }
    return sources.maxByOrNull { it.latestDateAdded }
}

object ImageSourceReader {
    const val DEFAULT_LIMIT = 90

    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun hasOnlyPartialAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) != PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun listSources(context: Context): List<ImageSource> {
        if (!hasPermission(context)) return emptyList()
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            pathColumn,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val rows = mutableListOf<SourceRow>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
                while (cursor.moveToNext()) {
                    rows.add(
                        SourceRow(
                            bucketId = cursor.getLong(bucketIndex),
                            name = cursor.getString(nameIndex),
                            dateAdded = cursor.getLong(dateIndex),
                            path = cursor.getString(pathIndex),
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            return groupSources(rows)
        } catch (_: IllegalArgumentException) {
            return groupSources(rows)
        }
        return groupSources(rows)
    }

    @Suppress("DEPRECATION")
    fun readRecent(context: Context, bucketId: Long?, limit: Int = DEFAULT_LIMIT): List<ImageItem> {
        if (!hasPermission(context)) return emptyList()
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val projection = if (bucketId == null) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                pathColumn,
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
            )
        }
        val selection = if (bucketId == null) {
            "$pathColumn LIKE ?"
        } else {
            "${MediaStore.Images.Media.BUCKET_ID} = ?"
        }
        val args = if (bucketId == null) {
            arrayOf("%Screenshots%")
        } else {
            arrayOf(bucketId.toString())
        }
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val found = mutableListOf<ImageItem>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sortOrder,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext() && found.size < limit) {
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    )
                    found.add(ImageItem(uri, cursor.getString(nameIndex) ?: "", cursor.getLong(dateIndex)))
                }
            }
        } catch (_: SecurityException) {
            return found
        } catch (_: IllegalArgumentException) {
            return found
        }
        return found
    }

    fun loadThumbnail(resolver: ContentResolver, uri: Uri, sizePx: Int = 256): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } catch (_: Exception) {
            null
        }
    }
}
