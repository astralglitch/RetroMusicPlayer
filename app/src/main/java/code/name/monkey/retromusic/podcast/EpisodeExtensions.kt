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

import androidx.lifecycle.LifecycleCoroutineScope
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PlayPreference
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        albumArtist = null,
        artworkUrl = podcast.imageUrl,
        videoUrl = videoUrl,
        videoSourceUrl = videoSourceUrl
    )
}

/**
 * Auto-activates the audio<->video toggle right after [episode] starts playing, if this
 * podcast's effective play preference ([PodcastEntity.playPreference], falling back to
 * [PreferenceUtil.globalPlayPreference]) is [PlayPreference.VIDEO_FIRST] and a video is actually
 * available for it. A no-op otherwise -- called from every "start playback" site (episode
 * details, a podcast's own episode list, cross-podcast episode lists, the queue) right after
 * `MusicPlayerRemote.openQueue`.
 *
 * `openQueue` only queues the track asynchronously (the service advances to it on its own
 * coroutine), so this waits, bounded, for `MusicPlayerRemote.currentSong` to actually become
 * [episode] before flipping [PodcastVideoToggle] -- same reasoning as
 * `EpisodeDetailsFragment.ensurePlayingAndActivateVideo`, which predates this and still owns the
 * *manual* "Video available" chip/dialog flow.
 */
fun LifecycleCoroutineScope.activateVideoIfPreferred(episode: EpisodeEntity, podcast: PodcastEntity) {
    val preference = podcast.playPreference ?: PreferenceUtil.globalPlayPreference
    if (preference != PlayPreference.VIDEO_FIRST) return
    if (episode.videoUrl == null && episode.videoSourceUrl == null) return
    launch {
        withTimeoutOrNull(3000) {
            while (MusicPlayerRemote.currentSong.episodeIdOrNull() != episode.id) {
                delay(50)
            }
        }
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == episode.id) {
            PodcastVideoToggle.setActive(true)
        }
    }
}
