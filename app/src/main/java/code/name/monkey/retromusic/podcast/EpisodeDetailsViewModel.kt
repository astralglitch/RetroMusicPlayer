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
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Backs EpisodeDetailsFragment -- a single-episode subpage reached from cross-podcast lists
 * (Podcasts Home) where tapping an entry used to jump straight into playback. Episodes usually
 * carry enough of their own info (description, duration, download state) to warrant a page of
 * their own, mirroring PodcastDetailsViewModel's shape but scoped to one episode. */
class EpisodeDetailsViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: EpisodeDownloadManager,
    episodeId: Long
) : ViewModel() {

    val episode: LiveData<EpisodeEntity?> = repository.episode(episodeId).asLiveData()

    private val _podcast = MutableLiveData<PodcastEntity?>()
    val podcast: LiveData<PodcastEntity?> = _podcast

    private val _downloadProgress = MutableLiveData<Int?>(null)
    val downloadProgress: LiveData<Int?> = _downloadProgress

    private var loadedPodcastFor: Long? = null

    init {
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
                delay(1000)
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
}
