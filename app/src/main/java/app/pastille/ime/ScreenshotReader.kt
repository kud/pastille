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

data class ScreenshotItem(
    val uri: Uri,
    val displayName: String,
)

object ScreenshotReader {
    const val DEFAULT_LIMIT = 6

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
    fun readRecent(context: Context, limit: Int = DEFAULT_LIMIT): List<ScreenshotItem> {
        if (!hasPermission(context)) return emptyList()
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            pathColumn,
        )
        val selection = "$pathColumn LIKE ?"
        val args = arrayOf("%Screenshots%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val found = mutableListOf<ScreenshotItem>()
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
                while (cursor.moveToNext() && found.size < limit) {
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    )
                    found.add(ScreenshotItem(uri, cursor.getString(nameIndex) ?: ""))
                }
            }
        } catch (_: SecurityException) {
            return found
        } catch (_: IllegalArgumentException) {
            return found
        }
        return found
    }

    fun loadThumbnail(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            resolver.loadThumbnail(uri, Size(256, 256), null)
        } catch (_: Exception) {
            null
        }
    }
}
