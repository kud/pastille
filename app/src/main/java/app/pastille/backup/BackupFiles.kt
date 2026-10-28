package app.pastille.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Moving backup files in and out through Android's own components: the Storage Access Framework
 * for files the user picks (Drive included, through its document provider) and the FileProvider
 * for sharing. Pastille itself never touches the network.
 *
 * A backup is always built and checked in the cache first ([build]); only a verified archive is
 * copied over the user's file, and the copy is read back and compared ([writeTo]).
 */
object BackupFiles {

    private fun workDir(context: Context): File = File(context.cacheDir, "backup").apply { mkdirs() }

    /** Copies a picked or shared file into the cache, where ZipFile can read it at random. */
    suspend fun copyIn(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val target = File(workDir(context), "incoming-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: throw FileNotFoundException("Could not open $uri")
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        target
    }

    /** Builds the whole collection into [target] and checks it reads back; throws if it doesn't. */
    suspend fun build(
        context: Context,
        target: File = File(workDir(context), "out-${UUID.randomUUID()}.zip"),
        withSync: Boolean = false,
    ): Pair<File, BackupArchive.Written> = withContext(Dispatchers.IO) {
        val repository = SnippetRepository.forContext(context)
        val images = ImageStore.forContext(context).imagesDir()
        try {
            target.parentFile?.mkdirs()
            val written = target.outputStream().use { repository.exportBackup(it, images, withSync) }
            if (BackupArchive.verify(target) == null) throw IOException("Backup didn't verify")
            target to written
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    /**
     * Replaces the contents of the document at [uri] with [source], then reads it back and compares.
     * "wt" truncates; some providers only take "rwt". Plain "w" is never used: on several Android
     * versions it doesn't truncate, which would leave the end of a longer old zip behind.
     */
    suspend fun writeTo(context: Context, uri: Uri, source: File) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "rwt")
        } ?: throw FileNotFoundException("Could not open $uri")
        stream.use { out -> source.inputStream().use { it.copyTo(out) } }
        val expected = source.inputStream().use(::sha256)
        val actual = resolver.openInputStream(uri)?.use(::sha256)
            ?: throw FileNotFoundException("Could not read back $uri")
        if (!expected.contentEquals(actual)) throw IOException("Backup file doesn't match what was written")
    }

    /** Builds the backup in cache/shared/, the folder the FileProvider serves, for the share sheet. */
    suspend fun buildForSharing(context: Context, now: Long = System.currentTimeMillis()): Pair<File, BackupArchive.Written> {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))
        val target = File(File(context.cacheDir, "shared"), "pastille-backup-$day.zip")
        return build(context, target)
    }

    /** Imports a cached copy and removes it. */
    suspend fun importFile(context: Context, file: File): BackupArchive.Counts = withContext(Dispatchers.IO) {
        try {
            SnippetRepository.forContext(context)
                .importBackup(file, ImageStore.forContext(context).imagesDir())
        } finally {
            file.delete()
        }
    }

    /** What a cached file holds, for the confirmation; throws when it isn't a Pastille backup. */
    suspend fun peek(file: File): BackupArchive.Counts = withContext(Dispatchers.IO) {
        BackupArchive.summary(SnippetBackup.decode(BackupArchive.readJson(file)))
    }

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** The provider's size and last-modified time for [uri]; either may be null when it doesn't say. */
    fun stat(context: Context, uri: Uri): Pair<Long?, Long?> = try {
        val columns = arrayOf(OpenableColumns.SIZE, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val size = if (cursor.isNull(0)) null else cursor.getLong(0)
            val modified = if (cursor.getColumnCount() < 2 || cursor.isNull(1)) null else cursor.getLong(1)
            size to modified
        } ?: (null to null)
    } catch (_: Exception) {
        null to null
    }

    /** Whether the provider still lists [uri], whatever it can serve right now. */
    fun exists(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }

    fun sha256Hex(file: File): String = file.inputStream().use(::sha256).joinToString("") { "%02x".format(it) }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }
}
