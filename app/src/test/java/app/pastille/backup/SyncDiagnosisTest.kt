package app.pastille.backup

import app.pastille.settings.BackupProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class SyncDiagnosisTest {

    @Test
    fun namesKnownProvidersAndNothingElse() {
        assertEquals("pCloud", SyncDiagnosis.providerLabel("com.pcloud.pcloud.documents"))
        assertEquals("Drive", SyncDiagnosis.providerLabel("com.google.android.apps.docs.storage"))
        assertEquals("this phone", SyncDiagnosis.providerLabel("com.android.externalstorage.documents"))
        assertEquals("the storage app", SyncDiagnosis.providerLabel("org.example.files"))
        assertEquals("the storage app", SyncDiagnosis.providerLabel(null))
    }

    @Test
    fun plainWriteOnlyOverAFileNoLongerThanTheNewOne() {
        assertTrue(SyncDiagnosis.plainWriteIsSafe(newLength = 100, currentLength = 100))
        assertTrue(SyncDiagnosis.plainWriteIsSafe(newLength = 100, currentLength = 0))
        assertFalse(SyncDiagnosis.plainWriteIsSafe(newLength = 99, currentLength = 100))
        assertFalse(SyncDiagnosis.plainWriteIsSafe(newLength = 100, currentLength = null))
    }

    @Test
    fun writesUnreadOnlyAnEmptyFileJustCreated() {
        assertTrue(SyncDiagnosis.mayWriteUnread(justCreated = true, size = 0))
        assertTrue(SyncDiagnosis.mayWriteUnread(justCreated = true, size = null))
        assertFalse(SyncDiagnosis.mayWriteUnread(justCreated = true, size = 12))
        assertFalse(SyncDiagnosis.mayWriteUnread(justCreated = false, size = 0))
    }

    @Test
    fun replacesABrokenFileOnlyAfterItsOwnCutOffWrite() {
        val broken = BrokenArchive(java.util.zip.ZipException("truncated"))
        assertTrue(SyncDiagnosis.mayReplaceBroken(pendingWrite = "abc", error = broken))
        // Nobody here was writing it: the wrong file, or another app's.
        assertFalse(SyncDiagnosis.mayReplaceBroken(pendingWrite = null, error = broken))
        // A whole archive Pastille can't decode (a newer version) or a read that failed is never replaced.
        assertFalse(SyncDiagnosis.mayReplaceBroken(pendingWrite = "abc", error = IllegalArgumentException("Unsupported backup version")))
        assertFalse(SyncDiagnosis.mayReplaceBroken(pendingWrite = "abc", error = FileNotFoundException()))
        assertFalse(SyncDiagnosis.mayReplaceBroken(pendingWrite = "abc", error = StaleRead()))
    }

    @Test
    fun theBytesWeReplacedAreAStaleRead() {
        assertTrue(SyncDiagnosis.isStaleRead(hash = "old", replacedHash = "old", writtenHash = "new"))
        assertFalse(SyncDiagnosis.isStaleRead(hash = "other", replacedHash = "old", writtenHash = "new"))
        assertFalse(SyncDiagnosis.isStaleRead(hash = "old", replacedHash = null, writtenHash = "new"))
        // The write never landed (read back differed): the old bytes are simply what the file holds.
        assertFalse(SyncDiagnosis.isStaleRead(hash = "old", replacedHash = "old", writtenHash = "old"))
    }

    @Test
    fun staleAndBrokenReadsWaitRatherThanFail() {
        assertEquals(BackupProblem.Read, SyncDiagnosis.problemFor(SyncStep.Read, StaleRead(), readable = true, canWrite = true))
        assertEquals(BackupProblem.Read, SyncDiagnosis.problemFor(SyncStep.Read, BrokenArchive(IOException()), readable = true, canWrite = true))
    }

    @Test
    fun zeroModifiedTimeIsUnknown() {
        assertEquals(null, SyncDiagnosis.knownModified(0))
        assertEquals(null, SyncDiagnosis.knownModified(null))
        assertEquals(5L, SyncDiagnosis.knownModified(5))
    }

    @Test
    fun classifiesEachFailure() {
        val sec = SecurityException()
        assertEquals(BackupProblem.NotKept, SyncDiagnosis.problemFor(SyncStep.Grant, sec, readable = true, canWrite = true))
        assertEquals(BackupProblem.NotKept, SyncDiagnosis.problemFor(SyncStep.Grant, sec, readable = true, canWrite = null))
        assertEquals(BackupProblem.ReadOnly, SyncDiagnosis.problemFor(SyncStep.Grant, sec, readable = true, canWrite = false))
        assertEquals(BackupProblem.ReadOnly, SyncDiagnosis.problemFor(SyncStep.Write, sec, readable = true, canWrite = false))
        assertEquals(BackupProblem.Access, SyncDiagnosis.problemFor(SyncStep.Write, sec, readable = true, canWrite = true))
        assertEquals(BackupProblem.Read, SyncDiagnosis.problemFor(SyncStep.Read, FileNotFoundException(), readable = true, canWrite = true))
        assertEquals(BackupProblem.Access, SyncDiagnosis.problemFor(SyncStep.Read, FileNotFoundException(), readable = false, canWrite = null))
        assertEquals(BackupProblem.Read, SyncDiagnosis.problemFor(SyncStep.Read, IllegalStateException(), readable = true, canWrite = null))
        assertEquals(BackupProblem.CantOverwrite, SyncDiagnosis.problemFor(SyncStep.Write, CantOverwrite("x"), readable = true, canWrite = true))
        assertEquals(BackupProblem.Mismatch, SyncDiagnosis.problemFor(SyncStep.ReadBack, ReadBackMismatch("x"), readable = true, canWrite = true))
        assertEquals(BackupProblem.Write, SyncDiagnosis.problemFor(SyncStep.Write, IOException(), readable = true, canWrite = true))
        assertEquals(
            BackupProblem.NotKept,
            SyncDiagnosis.problemFor(SyncStep.Grant, SyncFailure(SyncStep.Grant, BackupProblem.NotKept, "x"), readable = true, canWrite = false),
        )
    }

    @Test
    fun reportNamesProviderStepAndClassesButNoMessages() {
        val secret = "my wifi password is hunter2"
        val error = IOException(secret, SecurityException(secret))
        val report = SyncDiagnosis.report(SyncStep.Write, error, BackupProblem.Write, "com.pcloud.pcloud.documents", mapOf("Flags" to "0x6"))
        assertTrue(report.contains("Sync failed: write"))
        assertTrue(report.contains("Provider: com.pcloud.pcloud.documents"))
        assertTrue(report.contains("java.io.IOException < java.lang.SecurityException"))
        assertTrue(report.contains("Flags: 0x6"))
        assertFalse(report.contains("hunter2"))
    }

    @Test
    fun signatureTellsFailuresApart() {
        val a = SyncDiagnosis.signature(SyncStep.Write, IOException(), "p")
        assertEquals(a, SyncDiagnosis.signature(SyncStep.Write, IOException("other text"), "p"))
        assertFalse(a == SyncDiagnosis.signature(SyncStep.Read, IOException(), "p"))
        assertFalse(a == SyncDiagnosis.signature(SyncStep.Write, CantOverwrite("x"), "p"))
    }
}
