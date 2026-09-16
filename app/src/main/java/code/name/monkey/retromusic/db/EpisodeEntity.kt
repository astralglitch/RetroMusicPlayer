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

    /** User-authored notes, distinct from the feed-supplied [description]. */
    @ColumnInfo(name = "user_notes")
    val userNotes: String? = null,

    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null,

    @ColumnInfo(name = "download_state")
    val downloadState: EpisodeDownloadState = EpisodeDownloadState.NOT_DOWNLOADED,

    @ColumnInfo(name = "download_id")
    val downloadId: Long? = null,

    @ColumnInfo(name = "playback_position_ms")
    val playbackPositionMs: Long = 0,

    /** Explicit played/unplayed state -- set automatically on natural completion (see
     * EpisodePositionSaver) or manually via the episode long-press menu. Kept separate from
     * playbackPositionMs so "mark as unplayed" doesn't have to also discard progress. */
    val played: Boolean = false,

    /** Starred from the episode long-press menu -- backs the Podcasts-world Home "Favorites"
     * section. Separate from [played], same idea as e-mail starring vs. read state. */
    val favorited: Boolean = false,

    /** A separate video source for this episode, present when the feed's enclosure `type`
     * starts with "video/" (see PodcastFeedParser). Drives the audio<->video toggle -- see
     * `MediaItemType.PODCAST_VIDEO` and `EpisodeExtensions.toSong`. Directly playable, unlike
     * [videoSourceUrl] below. */
    @ColumnInfo(name = "video_url")
    val videoUrl: String? = null,

    /** A link to a video version of this episode that lives elsewhere -- a YouTube/Vimeo/etc.
     * URL found in the show notes, or manually assigned by the user (see episode details' "Video
     * link" menu action). Unlike [videoUrl], this is NOT directly playable by ExoPlayer -- it's a
     * page URL, opened externally (browser/other app) for now. Resolving it into an actual
     * in-app-playable stream (AGENTS.md goal #5, via NewPipeExtractor) is separate, later work. */
    @ColumnInfo(name = "video_source_url")
    val videoSourceUrl: String? = null
)
