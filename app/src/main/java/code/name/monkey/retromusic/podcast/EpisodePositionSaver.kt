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

import code.name.monkey.retromusic.db.EpisodeDao
import code.name.monkey.retromusic.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Persists podcast episode playback position (see `EpisodeEntity.playbackPositionMs`) into the
 * DB. Fire-and-forget by design: called from [code.name.monkey.retromusic.service.MusicService]
 * on its own playback-progress/pause/track-change hooks, which shouldn't block on a DB write.
 */
class EpisodePositionSaver(private val episodeDao: EpisodeDao) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * @param actualDurationMs the player-measured duration of the currently loaded media, when
     * known. Prefer this over `song.duration` (feed-reported `<itunes:duration>`, or 0 for
     * folder-scanned episodes): feed metadata can be missing or wrong, which previously caused
     * episodes to either never show progress or get auto-marked "played" after only a few
     * minutes. Also backfills `EpisodeEntity.durationMs` so the episode list can render a real
     * progress bar instead of falling back to an indeterminate one.
     */
    fun save(song: Song, positionMs: Int, actualDurationMs: Int = 0) {
        val episodeId = song.episodeIdOrNull() ?: return
        val durationMs: Long = if (actualDurationMs > 0) actualDurationMs.toLong() else song.duration
        scope.launch {
            episodeDao.updatePlaybackPosition(episodeId, positionMs.toLong())
            if (actualDurationMs > 0 && actualDurationMs.toLong() != song.duration) {
                episodeDao.updateDuration(episodeId, actualDurationMs.toLong())
            }
            // Auto-mark as played on natural completion, mirroring how podcast apps treat
            // "finished listening" -- and auto-*un*mark it the moment playback is meaningfully
            // away from the end again (e.g. restarting a played episode from 0), so the episode
            // list's "Played" label/greyout and progress bar don't get stuck reflecting a
            // completion that's no longer true. This only runs while actually playing that
            // episode (see the callers), so it never fights a deliberate manual "mark as played"
            // on an episode the user isn't currently listening to.
            if (durationMs > 0) {
                val remainingMs = durationMs - positionMs
                episodeDao.updatePlayed(episodeId, remainingMs <= durationMs * 0.05)
            }
        }
    }
}
