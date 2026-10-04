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
