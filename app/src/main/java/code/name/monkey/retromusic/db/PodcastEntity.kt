/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a [PodcastEntity]'s episodes are discovered from. */
enum class PodcastSourceType {
    RSS,
    FOLDER
}

/** Whether tapping play on an episode should also auto-activate the audio<->video toggle when a
 * video is available -- see `PreferenceUtil.globalPlayPreference` (the app-wide default) and
 * [PodcastEntity.playPreference] (a per-podcast override, null meaning "use the global default"). */
enum class PlayPreference {
    AUDIO_FIRST,
    VIDEO_FIRST
}

/** A subscribed podcast feed, or a local/network folder mapped as a podcast source. */
@Entity(indices = [Index(value = ["feed_url"], unique = true)])
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Present for [PodcastSourceType.RSS] sources; null for [PodcastSourceType.FOLDER] ones.
     * Still indexed unique so two RSS subscriptions can't collide -- folder sources dedupe on
     * [folderUri] instead (checked in code, not the DB, since a nullable column can't carry a
     * second unique index without allowing multiple NULLs to collide on some SQLite versions). */
    @ColumnInfo(name = "feed_url")
    val feedUrl: String? = null,

    val title: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    val description: String? = null,

    @ColumnInfo(name = "last_fetched")
    val lastFetched: Long = 0,

    /** Starred subscription -- separate from favorited episodes (EpisodeEntity.favorited). */
    val favorited: Boolean = false,

    /** Which [QueueEntity] this show's new episodes are auto-added to. Null means "the default
     * queue" rather than baking in a specific id, so it stays correct even if the default
     * queue's own id ever changes. */
    @ColumnInfo(name = "target_queue_id")
    val targetQueueId: Long? = null,

    @ColumnInfo(name = "source_type", defaultValue = "RSS")
    val sourceType: PodcastSourceType = PodcastSourceType.RSS,

    /** Persisted SAF tree URI string (see `ContentResolver.takePersistableUriPermission`),
     * present only for [PodcastSourceType.FOLDER] sources. */
    @ColumnInfo(name = "folder_uri")
    val folderUri: String? = null,

    /** Null = use `PreferenceUtil.globalPlayPreference`. */
    @ColumnInfo(name = "play_preference")
    val playPreference: PlayPreference? = null,

    /** Null = use `PreferenceUtil.globalAutoDownloadVideo`. See `EpisodeDownloadManager.enqueueVideo`. */
    @ColumnInfo(name = "auto_download_video")
    val autoDownloadVideo: Boolean? = null
)
