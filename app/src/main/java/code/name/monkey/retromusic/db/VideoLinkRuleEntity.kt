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

enum class VideoLinkMatchType {
    /** [VideoLinkRuleEntity.pattern] is a key into `code.name.monkey.retromusic.podcast.VideoLinkPresets`. */
    PRESET,

    /** [VideoLinkRuleEntity.pattern] is a raw regex applied directly to each candidate URL. */
    REGEX
}

/**
 * A per-podcast rule for auto-detecting a video link in an episode's show notes -- see
 * `code.name.monkey.retromusic.podcast.VideoLinkMatcher`. Multiple rules can apply to the same
 * podcast; [priority] (lower first) breaks ties when more than one link in a single episode's
 * notes matches, deciding which becomes the primary [EpisodeEntity.videoSourceUrl] versus a
 * secondary [EpisodeEntity.videoLinkCandidates] entry.
 */
@Entity(indices = [Index("podcast_id")])
data class VideoLinkRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "podcast_id")
    val podcastId: Long,

    @ColumnInfo(name = "match_type")
    val matchType: VideoLinkMatchType,

    val pattern: String,

    val enabled: Boolean = true,

    val priority: Int = 0
)
