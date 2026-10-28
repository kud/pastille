package app.pastille.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutGroupTest {
    @Test
    fun `version label carries name and code`() {
        assertEquals("0.1.5 (16)", versionLabel("0.1.5", 16, debug = false))
    }

    @Test
    fun `debug build says so`() {
        assertEquals("0.1.5 (16) · debug", versionLabel("0.1.5", 16, debug = true))
    }

}
