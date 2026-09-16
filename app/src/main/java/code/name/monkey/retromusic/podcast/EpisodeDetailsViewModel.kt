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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.db.BookmarkDao
import code.name.monkey.retromusic.db.BookmarkEntity
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backs EpisodeDetailsFragment -- a single-episode subpage reached from cross-podcast lists
 * (Podcasts Home) where tapping an entry used to jump straight into playback. Episodes usually
 * carry enough of their own info (description, duration, download state) to warrant a page of
 * their own, mirroring PodcastDetailsViewModel's shape but scoped to one episode. */
class EpisodeDetailsViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: EpisodeDownloadManager,
    private val bookmarkDao: BookmarkDao,
    private val episodeId: Long
) : ViewModel() {

    val episode: LiveData<EpisodeEntity?> = repository.episode(episodeId).asLiveData()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks = _bookmarks.asStateFlow()

    private val _podcast = MutableLiveData<PodcastEntity?>()
    val podcast: LiveData<PodcastEntity?> = _podcast

    private val _downloadProgress = MutableLiveData<Int?>(null)
    val downloadProgress: LiveData<Int?> = _downloadProgress

    /** Whether this episode is the one currently loaded in the player AND actively playing --
     * distinct from merely being the current song (which could be paused). Drives the Play
     * button showing "Pause" instead, and not restarting from 0 when this episode is already
     * playing (see EpisodeDetailsFragment.play()). */
    private val _isPlayingThisEpisode = MutableLiveData(false)
    val isPlayingThisEpisode: LiveData<Boolean> = _isPlayingThisEpisode

    private var loadedPodcastFor: Long? = null

    init {
        loadBookmarks()
        viewModelScope.launch {
            while (true) {
                val current = episode.value
                if (current != null && loadedPodcastFor != current.podcastId) {
                    loadedPodcastFor = current.podcastId
                    _podcast.value = repository.podcastById(current.podcastId)
                }
                if (current?.downloadState == EpisodeDownloadState.DOWNLOADING && current.downloadId != null) {
                    _downloadProgress.value = downloadManager.queryProgressPercent(current.downloadId)
                } else {
                    _downloadProgress.value = null
                }
                _isPlayingThisEpisode.value = current != null &&
                    MusicPlayerRemote.currentSong.episodeIdOrNull() == current.id &&
                    MusicPlayerRemote.isPlaying
                delay(500)
            }
        }
    }

    fun download() {
        val current = episode.value ?: return
        viewModelScope.launch { downloadManager.enqueue(current) }
    }

    fun deleteDownload() {
        val current = episode.value ?: return
        viewModelScope.launch { downloadManager.deleteDownload(current) }
    }

    fun setPlayed(played: Boolean) {
        val current = episode.value ?: return
        viewModelScope.launch { repository.setEpisodePlayed(current.id, played) }
    }

    fun setFavorited(favorited: Boolean) {
        val current = episode.value ?: return
        viewModelScope.launch { repository.setEpisodeFavorited(current.id, favorited) }
    }

    /** Adds a bookmark at the player's current position -- only meaningful while this episode is
     * the one actually playing, same assumption the skip controls make. */
    fun addBookmarkAtCurrentPosition() {
        val current = episode.value ?: return
        if (current.id != episodeId) return
        val position = MusicPlayerRemote.songProgressMillis.toLong()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.insertBookmark(BookmarkEntity(episodeId = episodeId, timestampMs = position))
            }
            loadBookmarks()
        }
    }

    fun setNotes(notes: String?) {
        viewModelScope.launch { repository.setEpisodeNotes(episodeId, notes) }
    }

    fun setVideoSourceUrl(url: String?) {
        viewModelScope.launch { repository.setEpisodeVideoSourceUrl(episodeId, url) }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            _bookmarks.value = withContext(Dispatchers.IO) { bookmarkDao.bookmarksForEpisode(episodeId) }
        }
    }
}
