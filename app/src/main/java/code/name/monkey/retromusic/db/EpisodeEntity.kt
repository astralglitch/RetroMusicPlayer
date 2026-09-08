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

enum class EpisodeDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

/**
 * One episode of a [PodcastEntity]. [guid] is the feed's stable per-episode identifier, used to
 * dedupe on refetch. [downloadId] mirrors the id returned by [android.app.DownloadManager] while
 * a download is in flight, so its completion broadcast can be matched back to this row.
 */
@Entity(indices = [Index(value = ["podcast_id", "guid"], unique = true)])
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "podcast_id")
    val podcastId: Long,

    val guid: String,

    val title: String,

    @ColumnInfo(name = "enclosure_url")
    val enclosureUrl: String,

    @ColumnInfo(name = "pub_date")
    val pubDate: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    val description: String? = null,

    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null,

    @ColumnInfo(name = "download_state")
    val downloadState: EpisodeDownloadState = EpisodeDownloadState.NOT_DOWNLOADED,

    @ColumnInfo(name = "download_id")
    val downloadId: Long? = null,

    @ColumnInfo(name = "playback_position_ms")
    val playbackPositionMs: Long = 0
)
