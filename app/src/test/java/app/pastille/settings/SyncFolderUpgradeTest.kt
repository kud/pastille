package app.pastille.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Sync gained folders. An install from before keeps its single-file sync exactly as stored: same
// file, same remembered state, no folder, until the user chooses one.
@RunWith(RobolectricTestRunner::class)
class SyncFolderUpgradeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("pastille_settings", Context.MODE_PRIVATE)

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test fun `a single-file sync from before folders reads as it was`() {
        prefs.edit()
            .putString("backup_uri", "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D42")
            .putString("backup_name", "pastille-backup.zip")
            .putLong("backup_last_at", 1_700_000_000_000)
            .putString("backup_fingerprint", "f")
            .putString("sync_remote_hash", "h")
            .commit()
        val settings = PastilleSettings.forContext(context)
        assertEquals("content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D42", settings.backupUri)
        assertEquals("pastille-backup.zip", settings.backupName)
        assertEquals(1_700_000_000_000, settings.backupLastAt)
        assertEquals("f", settings.backupFingerprint)
        assertEquals("h", settings.syncRemoteHash)
        assertNull(settings.syncFolderUri)
        assertNull(settings.syncFolderName)
        assertEquals(1, settings.syncFolderMatches)
        assertNull(settings.syncFolderPicked)
    }

    @Test fun `turning sync off forgets the folder too`() {
        val settings = PastilleSettings.forContext(context)
        settings.backupUri = "content://p/tree/1/document/2"
        settings.syncFolderUri = "content://p/tree/1"
        settings.syncFolderName = "Pastille"
        settings.syncFolderMatches = 2
        settings.syncFolderPicked = 5
        settings.clearBackup()
        assertNull(settings.backupUri)
        assertNull(settings.syncFolderUri)
        assertNull(settings.syncFolderName)
        assertEquals(1, settings.syncFolderMatches)
        assertNull(settings.syncFolderPicked)
    }
}
