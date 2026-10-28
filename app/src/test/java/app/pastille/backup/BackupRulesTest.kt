package app.pastille.backup

import app.pastille.settings.PastilleSettings
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The README promises that nothing reaches Google's cloud backup while a phone-to-phone
 * transfer still moves everything. These rules are the whole of that promise.
 */
class BackupRulesTest {

    private val res = File("src/main/res/xml")

    private fun rules(name: String, parent: String?, tag: String): List<Element> {
        val file = File(res, name)
        assertTrue("$file is missing", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val scope = parent?.let { doc.getElementsByTagName(it).item(0) as Element } ?: doc.documentElement
        val nodes = scope.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.rule() = getAttribute("domain") to getAttribute("path")

    private val prefsFile: String = PastilleSettings::class.java.getDeclaredField("PREFS_NAME")
        .apply { isAccessible = true }
        .get(null) as String + ".xml"

    private val transferred = setOf(
        "database" to ".",
        "file" to "images/",
        "file" to "crashes/",
        // Two-phone sync keeps its merge base here (filesDir/sync/base.json).
        "file" to "sync/",
        "sharedpref" to prefsFile,
    )

    @Test
    fun `cloud backup excludes every domain on Android 12 and later`() {
        val cloud = rules("data_extraction_rules.xml", "cloud-backup", "exclude").map { it.rule() }.toSet()
        assertEquals(
            setOf("root", "file", "database", "sharedpref", "external").map { it to "." }.toSet(),
            cloud,
        )
        assertTrue(rules("data_extraction_rules.xml", "cloud-backup", "include").isEmpty())
    }

    @Test
    fun `device transfer carries the database, images, crash log, sync state and settings`() {
        val transfer = rules("data_extraction_rules.xml", "device-transfer", "include").map { it.rule() }.toSet()
        assertEquals(transferred, transfer)
    }

    @Test
    fun `before Android 12 every include needs a device-to-device transfer`() {
        val includes = rules("backup_rules.xml", null, "include")
        assertEquals(transferred, includes.map { it.rule() }.toSet())
        includes.forEach { assertEquals("deviceToDeviceTransfer", it.getAttribute("requireFlags")) }
        assertTrue(rules("backup_rules.xml", null, "exclude").isEmpty())
    }

    @Test
    fun `the manifest points at both rule files`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }
}
