package app.pastille.ui

import app.pastille.update.AppVersion
import app.pastille.update.ReleaseAsset
import app.pastille.update.Update
import app.pastille.update.UpdateProblem
import app.pastille.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSheetTextTest {
    private val update = Update(AppVersion.parse("0.1.6-dev.10")!!, "v0.1.6-dev.10", ReleaseAsset("a.apk", "https://github.com/x", null, 1), ByteArray(32))

    @Test fun downloadIsShownExactlyAndSpokenInTens() {
        val state = UpdateState.Downloading(update, 47)
        assertEquals("Downloading 0.1.6-dev.10 · 47%", sheetStatus(state))
        assertEquals("Downloading 0.1.6-dev.10, 40 percent", spokenStatus(state))
    }

    @Test fun otherStatesAreSpokenAsShown() {
        val state = UpdateState.Failed(UpdateProblem.NETWORK, null)
        assertEquals("Couldn't reach GitHub: try again", sheetStatus(state))
        assertEquals(sheetStatus(state), spokenStatus(state))
        assertEquals("Downloading 0.1.6-dev.10…", spokenStatus(UpdateState.Downloading(update, null)))
    }
}
