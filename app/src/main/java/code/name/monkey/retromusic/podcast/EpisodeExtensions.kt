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
package code.name.monkey.retromusic.podcast

import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.model.Song

/**
 * Separate id space from MediaStore song ids (which come from the device's content provider and
 * are arbitrary positive longs) so an episode can never collide with a real scanned track.
 */
const val EPISODE_SONG_ID_OFFSET = 1_000_000_000_000L

fun EpisodeEntity.toSongId(): Long = EPISODE_SONG_ID_OFFSET + id

/** The [EpisodeEntity.id] a playing [Song] came from, or null if it's not a podcast episode. */
fun Song.episodeIdOrNull(): Long? = if (id >= EPISODE_SONG_ID_OFFSET) id - EPISODE_SONG_ID_OFFSET else null

/**
 * Adapts an episode into the [Song] shape [code.name.monkey.retromusic.service.MusicService]
 * already knows how to queue and play. `data` carries the downloaded file (`file://…`) when
 * present, else the streaming enclosure URL directly — see `SongExtensions.uri` for how that's
 * resolved into a playable [android.net.Uri].
 */
fun EpisodeEntity.toSong(podcast: PodcastEntity): Song {
    val playbackSource = localFilePath?.let { "file://$it" } ?: enclosureUrl
    return Song(
        id = toSongId(),
        title = title,
        trackNumber = 0,
        year = 0,
        duration = durationMs,
        data = playbackSource,
        dateModified = pubDate,
        albumId = -1,
        albumName = podcast.title,
        artistId = podcast.id,
        artistName = podcast.title,
        composer = null,
        albumArtist = null
    )
}
