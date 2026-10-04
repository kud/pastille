package app.pastille.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnDefaultTest {

    @Test
    fun `fresh installs return after a snippet but stay after an image`() {
        assertTrue(returnDefault(legacy = null, image = false))
        assertFalse(returnDefault(legacy = null, image = true))
    }

    @Test
    fun `an upgrade keeps the old single switch for both`() {
        assertTrue(returnDefault(legacy = true, image = true))
        assertFalse(returnDefault(legacy = false, image = false))
    }
}
