package app.pastille.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The backup file: a plain ZIP, so every phone, file manager and Drive recognises it by its
 * standard type and `.zip` extension (custom extensions are unreliable on Android).
 *
 * ```
 * pastille.json        the SnippetBackup JSON, version 4
 * images/<name>        one entry per image snippet or sticker, named as in filesDir/images/
 * ```
 *
 * Old exports, a bare JSON file (versions 1–3), still import: [isZip] tells them apart by the
 * first bytes, never by the file name.
 */
object BackupArchive {
    const val MIME = "application/zip"
    const val JSON_ENTRY = "pastille.json"
    const val IMAGES_DIR = "images/"
    const val SUGGESTED_NAME = "pastille-backup.zip"

    // An image entry is larger than anything ImageStore keeps (it caps sources well below this).
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
    private const val MAX_JSON_BYTES = 32L * 1024 * 1024

    // Content-addressed names from ImageStore: hex, a dot, an image extension. Anything else in an
    // archive (a path, "..", a stray file) is ignored, so a crafted zip can't write outside images/.
    private val imageName = Regex("^[0-9a-f]{8,64}\\.(png|jpg|jpeg|webp|gif)$")

    fun isImageName(name: String): Boolean = imageName.matches(name)

    fun isZip(header: ByteArray): Boolean =
        header.size >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()

    fun isZip(file: File): Boolean = file.inputStream().use { input ->
        val header = ByteArray(4)
        val read = input.read(header)
        read == 4 && isZip(header)
    }

    /**
     * Writes the archive. Records whose image file is missing or isn't a valid name are left out
     * before encoding, so the JSON never names an entry the archive doesn't hold.
     */
    fun write(
        out: OutputStream,
        records: List<app.pastille.model.SnippetRecord>,
        categories: List<app.pastille.model.CategoryRecord>,
        imageFile: (String) -> File,
    ): Written {
        val kept = records.filter { record ->
            val name = record.imageFile ?: return@filter true
            isImageName(name) && imageFile(name).isFile
        }
        val json = SnippetBackup.encode(kept, categories)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray())
            zip.closeEntry()
            kept.mapNotNull { it.imageFile }.distinct().forEach { name ->
                zip.putNextEntry(ZipEntry(IMAGES_DIR + name))
                imageFile(name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return Written(
            snippets = kept.count { !it.isImage },
            images = kept.count { it.isImage && !it.sticker },
            stickers = kept.count { it.sticker },
            leftOut = records.size - kept.size,
        )
    }

    data class Written(val snippets: Int, val images: Int, val stickers: Int, val leftOut: Int)

    /** The JSON of a backup file, zip or old bare JSON. Throws if it isn't one. */
    fun readJson(file: File): String =
        if (isZip(file)) {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(JSON_ENTRY) ?: throw IOException("No $JSON_ENTRY")
                if (entry.size > MAX_JSON_BYTES) throw IOException("Backup too large")
                zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            }
        } else {
            if (file.length() > MAX_JSON_BYTES) throw IOException("Backup too large")
            file.readText()
        }

    /**
     * Copies the archive's image [name] into [imagesDir], unless a file with that name is already
     * there (names are content-addressed, so it's the same image). Written to a temporary name and
     * renamed, so a failure never leaves half a file under a real name. False when the archive has
     * no such entry, the name isn't valid, or the copy failed.
     */
    fun extractImage(file: File, name: String, imagesDir: File): Boolean {
        if (!isImageName(name)) return false
        val target = File(imagesDir, name)
        if (target.isFile && target.length() > 0) return true
        return ZipFile(file).use { zip ->
            val entry = zip.getEntry(IMAGES_DIR + name) ?: return@use false
            imagesDir.mkdirs()
            val tmp = File(imagesDir, ".tmp-${UUID.randomUUID()}")
            try {
                zip.getInputStream(entry).use { input -> copyCapped(input, tmp) }
                tmp.renameTo(target) || target.isFile
            } catch (_: IOException) {
                false
            } finally {
                tmp.delete()
            }
        }
    }

    private fun copyCapped(input: InputStream, dest: File) {
        dest.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_IMAGE_BYTES) throw IOException("Image too large")
                output.write(buffer, 0, read)
            }
        }
    }

    /**
     * Checks a freshly written archive before it replaces a good one: the JSON decodes and every
     * image it names is in the archive. Returns the decoded contents, or null.
     */
    fun verify(file: File): BackupContents? = try {
        val contents = SnippetBackup.decode(readJson(file))
        val ok = ZipFile(file).use { zip ->
            contents.snippets.mapNotNull { it.record.imageFile }.all { zip.getEntry(IMAGES_DIR + it) != null }
        }
        if (ok) contents else null
    } catch (_: Exception) {
        null
    }

    /** "12 snippets, 3 stickers and 2 images" for the import confirmation. */
    fun summary(contents: BackupContents): Counts {
        val records = contents.snippets.map { it.record }
        return Counts(
            snippets = records.count { !it.isImage },
            images = records.count { it.isImage && !it.sticker },
            stickers = records.count { it.sticker },
        )
    }

    data class Counts(val snippets: Int, val images: Int, val stickers: Int)
}
