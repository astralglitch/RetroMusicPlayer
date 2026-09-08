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

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `PodcastEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`feed_url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`image_url` TEXT, `description` TEXT, `last_fetched` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_PodcastEntity_feed_url` ON `PodcastEntity` (`feed_url`)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `EpisodeEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`podcast_id` INTEGER NOT NULL, `guid` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`enclosure_url` TEXT NOT NULL, `pub_date` INTEGER NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `description` TEXT, `local_file_path` TEXT, " +
                "`download_state` TEXT NOT NULL, `download_id` INTEGER, `playback_position_ms` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_EpisodeEntity_podcast_id_guid` ON `EpisodeEntity` (`podcast_id`, `guid`)"
        )
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `EpisodeEntity` ADD COLUMN `played` INTEGER NOT NULL DEFAULT 0"
        )
    }
}