package code.name.monkey.retromusic.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE LyricsEntity")
        database.execSQL("DROP TABLE BlackListStoreEntity")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `MediaItemEntity` (" +
                "`song_id` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                "`is_manual_override` INTEGER NOT NULL DEFAULT 0, " +
                "`feed_url` TEXT, `chapters_json` TEXT, `notes` TEXT, " +
                "PRIMARY KEY(`song_id`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `BookmarkEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`episode_id` INTEGER NOT NULL, `timestamp_ms` INTEGER NOT NULL, " +
                "`note_text` TEXT, `created_at` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_BookmarkEntity_episode_id` ON `BookmarkEntity` (`episode_id`)"
        )
    }
}