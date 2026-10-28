package app.pastille.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThirdPartyNoticesTest {
    private fun words(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    @Test
    fun `the in-app notices carry the same licence text as the docs`() {
        val doc = listOf(File("../docs/third-party-notices.md"), File("docs/third-party-notices.md"))
            .first { it.exists() }
            .readText()
        assertTrue(THIRD_PARTY_NOTICES.isNotEmpty())
        THIRD_PARTY_NOTICES.forEach { notice ->
            assertTrue(notice.name, words(doc).contains(words(notice.licence)))
        }
    }
}
