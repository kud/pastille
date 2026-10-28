package app.pastille.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `categoryId` INTEGER")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `imageFile` TEXT")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `imageWidth` INTEGER")
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `imageHeight` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snippets_categoryId` ON `snippets` (`categoryId`)")
    }
}

// Manual order: positions start from the order people already saw (pinned, then most recently used).
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE `snippets` SET `position` = (SELECT COUNT(*) FROM `snippets` AS other WHERE " +
                "other.pinned > `snippets`.pinned OR (other.pinned = `snippets`.pinned AND " +
                "(other.lastUsedAt > `snippets`.lastUsedAt OR (other.lastUsedAt = `snippets`.lastUsedAt AND other.id < `snippets`.id))))",
        )
    }
}

// The 30-day bin: a binned snippet keeps its row, with the time it was deleted.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `deletedAt` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snippets_deletedAt` ON `snippets` (`deletedAt`)")
    }
}

// Tags: several per snippet, kept apart from folders. No SQLite foreign keys (house rule).
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `snippet_tags` (`snippetId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`snippetId`, `tagId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_snippet_tags_tagId` ON `snippet_tags` (`tagId`)")
    }
}

// Stickers: an image snippet carrying one mark. No CHECK (ALTER can't add one) and no index.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `sticker` INTEGER NOT NULL DEFAULT 0")
    }
}

// SQLite's own random UUID v4, evaluated once per row, so every existing row gets its own.
private const val SQL_UUID =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

// Sync: a stable uid on snippets, folders and tags (backfilled), an edit time on folders and tags,
// and tombstones for deletions that must reach the other phone. Nothing existing is rewritten
// except the new columns.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `snippets` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `snippets` SET `uid` = $SQL_UUID")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_snippets_uid` ON `snippets` (`uid`)")

        db.execSQL("ALTER TABLE `categories` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `categories` SET `uid` = $SQL_UUID, `updatedAt` = `createdAt`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_uid` ON `categories` (`uid`)")

        db.execSQL("ALTER TABLE `tags` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `tags` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `tags` SET `uid` = $SQL_UUID, `updatedAt` = `createdAt`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_uid` ON `tags` (`uid`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `tombstones` (`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
    }
}
