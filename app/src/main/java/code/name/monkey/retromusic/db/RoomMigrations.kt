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

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `EpisodeEntity` ADD COLUMN `favorited` INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE `PodcastEntity` ADD COLUMN `favorited` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `QueueEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `is_default` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `QueueItemEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`queue_id` INTEGER NOT NULL, `episode_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `added_at` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_QueueItemEntity_queue_id_episode_id` " +
                "ON `QueueItemEntity` (`queue_id`, `episode_id`)"
        )
        database.execSQL(
            "ALTER TABLE `PodcastEntity` ADD COLUMN `target_queue_id` INTEGER"
        )

        val now = System.currentTimeMillis()
        database.execSQL(
            "INSERT INTO `QueueEntity` (`name`, `is_default`, `created_at`) VALUES ('Queue', 1, $now)"
        )
        val defaultQueueId = database.query("SELECT last_insert_rowid()").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        }
        // Seed the new default queue with existing not-yet-played episodes, oldest first, so it
        // isn't empty on first run for anyone who already had subscriptions. Avoids window
        // functions (ROW_NUMBER etc.) since minSdk 24 devices can't be relied on to bundle a
        // recent enough SQLite -- a correlated-subquery count is the old-fashioned equivalent.
        database.execSQL(
            "INSERT INTO `QueueItemEntity` (`queue_id`, `episode_id`, `position`, `added_at`) " +
                "SELECT $defaultQueueId, e.`id`, " +
                "(SELECT COUNT(*) FROM `EpisodeEntity` e2 WHERE e2.`played` = 0 AND " +
                "(e2.`pub_date` < e.`pub_date` OR (e2.`pub_date` = e.`pub_date` AND e2.`id` < e.`id`))), " +
                "$now " +
                "FROM `EpisodeEntity` e WHERE e.`played` = 0"
        )
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // feed_url goes from NOT NULL to nullable (folder-sourced podcasts have no feed), which
        // SQLite's ALTER TABLE can't express directly -- rebuild the table the standard way
        // (rename, recreate with the new schema, copy, drop). Every existing row came from RSS,
        // so source_type backfills to 'RSS' and folder_uri stays null for all of them.
        database.execSQL("ALTER TABLE `PodcastEntity` RENAME TO `PodcastEntity_old`")
        database.execSQL(
            "CREATE TABLE `PodcastEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`feed_url` TEXT, `title` TEXT NOT NULL, " +
                "`image_url` TEXT, `description` TEXT, `last_fetched` INTEGER NOT NULL, " +
                "`favorited` INTEGER NOT NULL DEFAULT 0, `target_queue_id` INTEGER, " +
                "`source_type` TEXT NOT NULL DEFAULT 'RSS', `folder_uri` TEXT)"
        )
        database.execSQL(
            "INSERT INTO `PodcastEntity` (`id`, `feed_url`, `title`, `image_url`, `description`, " +
                "`last_fetched`, `favorited`, `target_queue_id`, `source_type`, `folder_uri`) " +
                "SELECT `id`, `feed_url`, `title`, `image_url`, `description`, `last_fetched`, " +
                "`favorited`, `target_queue_id`, 'RSS', NULL FROM `PodcastEntity_old`"
        )
        database.execSQL("DROP TABLE `PodcastEntity_old`")
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_PodcastEntity_feed_url` ON `PodcastEntity` (`feed_url`)"
        )

        database.execSQL(
            "ALTER TABLE `EpisodeEntity` ADD COLUMN `user_notes` TEXT"
        )
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `EpisodeEntity` ADD COLUMN `video_url` TEXT"
        )
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `EpisodeEntity` ADD COLUMN `video_source_url` TEXT"
        )
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `VideoLinkRuleEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`podcast_id` INTEGER NOT NULL, " +
                "`match_type` TEXT NOT NULL, " +
                "`pattern` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`priority` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_VideoLinkRuleEntity_podcast_id` ON `VideoLinkRuleEntity` (`podcast_id`)"
        )
        database.execSQL("ALTER TABLE `EpisodeEntity` ADD COLUMN `video_link_candidates` TEXT")
        database.execSQL(
            "ALTER TABLE `EpisodeEntity` ADD COLUMN `video_download_state` TEXT NOT NULL DEFAULT 'NOT_DOWNLOADED'"
        )
        database.execSQL("ALTER TABLE `EpisodeEntity` ADD COLUMN `video_download_id` INTEGER")
        database.execSQL("ALTER TABLE `EpisodeEntity` ADD COLUMN `video_local_file_path` TEXT")
        database.execSQL("ALTER TABLE `PodcastEntity` ADD COLUMN `play_preference` TEXT")
        database.execSQL("ALTER TABLE `PodcastEntity` ADD COLUMN `auto_download_video` INTEGER")
    }
}