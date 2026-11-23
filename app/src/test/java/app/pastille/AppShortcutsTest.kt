package app.pastille

import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppShortcutsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun newSnippetShortcutOpensAnEmptySnippet() {
        val request = AppShortcuts.newSnippetIntent(context).toLaunchRequest()
        assertEquals(LaunchRequest.NewSnippet(categoryId = null), request)
    }

    @Test
    fun newSnippetShortcutTargetsTheMainActivityInThisPackage() {
        val intent = AppShortcuts.newSnippetIntent(context)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
    }

    @Test
    fun publishingTwiceLeavesOneShortcut() {
        AppShortcuts.publish(context)
        AppShortcuts.publish(context)
        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(listOf(AppShortcuts.NEW_SNIPPET_ID), shortcuts.map { it.id })
        assertEquals("New snippet", shortcuts.single().shortLabel.toString())
        assertEquals(MainActivity.ACTION_NEW_SNIPPET, shortcuts.single().intent.action)
    }

    @Test
    fun anUnrelatedActionIsNotANewSnippet() {
        assertNull(Intent("app.pastille.action.SOMETHING_ELSE").toLaunchRequest())
    }
}
