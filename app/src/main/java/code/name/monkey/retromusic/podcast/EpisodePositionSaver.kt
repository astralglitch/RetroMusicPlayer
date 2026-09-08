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

    fun save(song: Song, positionMs: Int) {
        val episodeId = song.episodeIdOrNull() ?: return
        scope.launch { episodeDao.updatePlaybackPosition(episodeId, positionMs.toLong()) }
    }
}
