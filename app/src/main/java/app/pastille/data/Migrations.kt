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
