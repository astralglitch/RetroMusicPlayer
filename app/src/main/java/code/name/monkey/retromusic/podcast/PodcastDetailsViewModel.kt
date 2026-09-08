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
import code.name.monkey.retromusic.db.EpisodeDownloadState.NOT_DOWNLOADED
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Scoped to one podcast (see PodcastModule's `parametersOf(podcastId)`), backing
 * PodcastDetailsFragment -- the header (podcast info) and its episode list/downloads, split out
 * of the old combined PodcastsViewModel now that browsing a podcast is its own screen. */
class PodcastDetailsViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: EpisodeDownloadManager,
    private val podcastId: Long
) : ViewModel() {

    private val _podcast = MutableLiveData<PodcastEntity?>()
    val podcast: LiveData<PodcastEntity?> = _podcast

    val episodes: LiveData<List<EpisodeEntity>> = repository.episodesForPodcast(podcastId).asLiveData()

    /** episode id -> 0-100, populated only while that episode is DOWNLOADING. */
    private val _downloadProgress = MutableLiveData<Map<Long, Int>>(emptyMap())
    val downloadProgress: LiveData<Map<Long, Int>> = _downloadProgress

    init {
        viewModelScope.launch {
            _podcast.value = repository.podcastById(podcastId)
        }
        viewModelScope.launch {
            while (true) {
                val inFlight = episodes.value.orEmpty()
                    .filter { it.downloadState == EpisodeDownloadState.DOWNLOADING && it.downloadId != null }
                if (inFlight.isNotEmpty()) {
                    _downloadProgress.value = inFlight.associate { episode ->
                        episode.id to (downloadManager.queryProgressPercent(episode.downloadId!!) ?: 0)
                    }
                }
                delay(1500)
            }
        }
    }

    fun refresh() {
        val podcast = _podcast.value ?: return
        viewModelScope.launch {
            repository.refresh(podcast)
        }
    }

    fun download(episode: EpisodeEntity) {
        viewModelScope.launch {
            downloadManager.enqueue(episode)
        }
    }

    fun deleteDownload(episode: EpisodeEntity) {
        viewModelScope.launch {
            downloadManager.deleteDownload(episode)
        }
    }

    /** Self-heals any episode stuck showing "Downloading…" -- see EpisodeDownloadManager docs. */
    fun reconcileDownloads() {
        viewModelScope.launch {
            downloadManager.reconcileInFlightDownloads(episodes.value.orEmpty())
        }
    }

    fun unsubscribe(onComplete: () -> Unit) {
        val podcast = _podcast.value ?: return
        viewModelScope.launch {
            episodes.value.orEmpty()
                .filter { it.downloadState != NOT_DOWNLOADED }
                .forEach { downloadManager.deleteDownload(it) }
            repository.unsubscribe(podcast)
            onComplete()
        }
    }
}
