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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
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

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
