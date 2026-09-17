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
import code.name.monkey.retromusic.helper.MusicPlayerRemote

/**
 * Seeks to an episode's saved [EpisodeEntity.playbackPositionMs] once it actually becomes the
 * current track. `MusicPlayerRemote.openQueue` is async (it goes through the service binder and
 * the new track needs to be prepared), so we can't seek right after calling it -- instead stash
 * the target here and call [onPlayingMetaChanged] from the fragment's own override of that
 * callback once the service reports the episode is current.
 *
 * Originally written inline in PodcastDetailsFragment; extracted so every "press play on an
 * episode" entry point resumes consistently instead of only the one place that had it.
 */
class EpisodeResumeSeeker {

    private var pendingEpisodeId: Long? = null
    private var pendingPositionMs: Int = 0

    /** Call right before/after starting playback of [episode]. Deliberately skips a `played`
     * episode even if it still has a non-zero `playbackPositionMs` (e.g. sitting right at the
     * very end, or auto-marked played early) -- seeking there would land on/near the end of the
     * file and immediately trigger auto-advance to the next track, which looks like "tapping
     * play skipped to the next episode" instead of restarting this one from 0. */
    fun arm(episode: EpisodeEntity) {
        if (!episode.played && episode.playbackPositionMs > 0) {
            pendingEpisodeId = episode.id
            pendingPositionMs = episode.playbackPositionMs.toInt()
        }
    }

    /** Call from the fragment's `onPlayingMetaChanged()` override. */
    fun onPlayingMetaChanged() {
        val targetEpisodeId = pendingEpisodeId ?: return
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == targetEpisodeId) {
            MusicPlayerRemote.seekTo(pendingPositionMs)
            pendingEpisodeId = null
        }
    }
}
