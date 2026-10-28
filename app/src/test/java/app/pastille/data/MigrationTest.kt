package app.pastille.data

import android.database.SQLException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PastilleDatabase::class.java,
    )

    private fun createVersion1WithTwoSnippets() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt) " +
                    "VALUES (1, 'greeting', 'hello there', 1, 10, 20, 30)",
            )
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt) " +
                    "VALUES (2, '', 'multi\nline', 0, 11, 21, 31)",
            )
            close()
        }
    }

    @Test
    fun migration1To2KeepsEverySnippet() {
        createVersion1WithTwoSnippets()

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT id, title, text, pinned, createdAt, updatedAt, lastUsedAt, " +
                "categoryId, imageFile, imageWidth, imageHeight FROM snippets ORDER BY id",
        ).use { cursor ->
            assertEquals(2, cursor.count)

            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("greeting", cursor.getString(1))
            assertEquals("hello there", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(10L, cursor.getLong(4))
            assertEquals(20L, cursor.getLong(5))
            assertEquals(30L, cursor.getLong(6))
            (7..10).forEach { assertTrue("column $it should be null", cursor.isNull(it)) }

            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals("multi\nline", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            (7..10).forEach { assertTrue("column $it should be null", cursor.isNull(it)) }
        }
    }

    @Test
    fun categoryNamesAreUniqueIgnoringCase() {
        createVersion1WithTwoSnippets()
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.execSQL("INSERT INTO categories (name, position, createdAt) VALUES ('Work', 0, 1)")
        try {
            db.execSQL("INSERT INTO categories (name, position, createdAt) VALUES ('work', 1, 2)")
            fail("expected the unique NOCASE index to reject 'work'")
        } catch (_: SQLException) {
        }
    }

    @Test
    fun roomOpensTheMigratedDatabase() {
        createVersion1WithTwoSnippets()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PastilleDatabase::class.java,
            DB_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
        helper.closeWhenFinished(database)

        val snippets = runBlocking { database.snippets().getAll() }

        assertEquals(listOf(1L, 2L), snippets.map { it.id })
        assertTrue(snippets.first().pinned)
        assertTrue(snippets.all { it.categoryId == null && it.imageFile == null })
        assertNull(snippets.first().imageWidth)
        assertTrue(runBlocking { database.categories().getAll() }.isEmpty())
    }

    @Test
    fun migration2To3KeepsTheOrderPeopleSaw() {
        createVersion1WithTwoSnippets()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).apply {
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt) " +
                    "VALUES (3, 'recent', 'fresh', 0, 12, 22, 99)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)

        db.query("SELECT id FROM snippets ORDER BY position ASC").use { cursor ->
            val ids = buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            assertEquals(listOf(1L, 3L, 2L), ids)
        }
        db.query("SELECT COUNT(DISTINCT position) FROM snippets").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
        }
    }

    @Test
    fun migration3To4StartsWithAnEmptyBin() {
        createVersion1WithTwoSnippets()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4)

        db.query("SELECT COUNT(*) FROM snippets WHERE deletedAt IS NULL").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
        }
        db.execSQL("UPDATE snippets SET deletedAt = 5 WHERE id = 1")
        db.query("SELECT id FROM snippets WHERE deletedAt IS NOT NULL").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
        }
    }

    @Test
    fun migration4To5AddsEmptyTagTables() {
        createVersion1WithTwoSnippets()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5)

        db.query("SELECT COUNT(*) FROM snippets").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
        }
        db.execSQL("INSERT INTO tags (name, createdAt) VALUES ('work', 1)")
        try {
            db.execSQL("INSERT INTO tags (name, createdAt) VALUES ('WORK', 2)")
            fail("expected the unique NOCASE index to reject 'WORK'")
        } catch (_: SQLException) {
        }
        db.execSQL("INSERT INTO snippet_tags (snippetId, tagId) VALUES (1, 1)")
        try {
            db.execSQL("INSERT INTO snippet_tags (snippetId, tagId) VALUES (1, 1)")
            fail("expected the composite primary key to reject a duplicate link")
        } catch (_: SQLException) {
        }
    }

    @Test
    fun migration5To6KeepsImageAndTextRowsAsSnippets() {
        helper.createDatabase(DB_NAME, 5).apply {
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt, position) " +
                    "VALUES (1, 'greeting', 'hello there', 0, 10, 20, 30, 0)",
            )
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt, imageFile, imageWidth, imageHeight, position) " +
                    "VALUES (2, 'cat', '', 0, 11, 21, 31, 'abc.webp', 512, 512, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 6, true, MIGRATION_5_6)

        db.query("SELECT id, imageFile, sticker FROM snippets ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals("abc.webp", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    @Test
    fun migration6To7GivesEveryRowItsOwnUid() {
        helper.createDatabase(DB_NAME, 6).apply {
            execSQL("INSERT INTO categories (id, name, position, createdAt) VALUES (1, 'Work', 0, 100)")
            execSQL("INSERT INTO tags (id, name, createdAt) VALUES (1, 'otp', 200)")
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt, categoryId, position, deletedAt, sticker) " +
                    "VALUES (1, 'a', 'alpha', 1, 10, 20, 30, 1, 0, NULL, 0)",
            )
            execSQL(
                "INSERT INTO snippets (id, title, text, pinned, createdAt, updatedAt, lastUsedAt, categoryId, position, deletedAt, sticker) " +
                    "VALUES (2, 'b', 'beta', 0, 11, 21, 31, NULL, 1, 500, 0)",
            )
            execSQL("INSERT INTO snippet_tags (snippetId, tagId) VALUES (1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7)

        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val uids = mutableListOf<String>()
        db.query("SELECT id, title, text, updatedAt, lastUsedAt, categoryId, deletedAt, uid FROM snippets ORDER BY id").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("alpha", c.getString(2))
            assertEquals(20L, c.getLong(3))
            assertEquals(30L, c.getLong(4))
            assertEquals(1L, c.getLong(5))
            uids += c.getString(7)
            assertTrue(c.moveToNext())
            assertEquals(500L, c.getLong(6))
            uids += c.getString(7)
        }
        db.query("SELECT uid, updatedAt FROM categories").use { c ->
            assertTrue(c.moveToNext())
            uids += c.getString(0)
            assertEquals(100L, c.getLong(1))
        }
        db.query("SELECT uid, updatedAt FROM tags").use { c ->
            assertTrue(c.moveToNext())
            uids += c.getString(0)
            assertEquals(200L, c.getLong(1))
        }
        uids.forEach { assertTrue("$it should be a v4 UUID", uuid.matches(it)) }
        assertEquals(uids.size, uids.toSet().size)
        db.query("SELECT COUNT(*) FROM tombstones").use { c ->
            assertTrue(c.moveToNext())
            assertEquals(0, c.getInt(0))
        }
        try {
            db.execSQL("UPDATE snippets SET uid = '${uids[0]}' WHERE id = 2")
            fail("expected the unique uid index to reject a duplicate")
        } catch (_: SQLException) {
        }
    }

    // A phone that has run every version: data from v1, folders and tags added on the way, then the
    // whole chain to v7 through Room itself, then a use, an edit and a delete as the app does them.
    @Test
    fun upgradeFromVersion1KeepsRealDataAndSyncsIt() {
        createVersion1WithTwoSnippets()
        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).apply {
            execSQL("INSERT INTO categories (id, name, position, createdAt) VALUES (7, 'Codes', 0, 40)")
            execSQL("UPDATE snippets SET categoryId = 7 WHERE id = 2")
            execSQL("INSERT INTO tags (id, name, createdAt) VALUES (3, 'otp', 50)")
            execSQL("INSERT INTO snippet_tags (snippetId, tagId) VALUES (2, 3)")
            close()
        }

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PastilleDatabase::class.java,
            DB_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
        helper.closeWhenFinished(database)
        val repository = SnippetRepository.forDatabase(database)

        runBlocking {
            val state = repository.syncState()
            assertEquals(setOf("hello there", "multi\nline"), state.snippets.map { it.text }.toSet())
            val multi = state.snippets.single { it.text == "multi\nline" }
            assertEquals(state.categories.single().uid, multi.categoryUid)
            assertEquals(listOf("otp"), multi.tags)
            assertEquals(21L, multi.updatedAt)

            // Using a snippet is not an edit any more.
            repository.recordUse(2, now = 9_000)
            val used = repository.syncState().snippets.single { it.text == "multi\nline" }
            assertEquals(9_000L, used.lastUsedAt)
            assertEquals(21L, used.updatedAt)

            // Moving it is, and deleting it for good leaves a tombstone under its uid.
            repository.setCategory(listOf(2), null, now = 9_500)
            assertEquals(9_500L, repository.get(2)!!.updatedAt)
            repository.delete(2, now = 10_000)
            repository.emptyBin()
            assertEquals(listOf(multi.uid), repository.syncState().tombstones.map { it.uid })
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
