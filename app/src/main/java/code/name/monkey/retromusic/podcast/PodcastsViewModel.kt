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
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.launch

class PodcastsViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: EpisodeDownloadManager
) : ViewModel() {

    val podcasts: LiveData<List<PodcastEntity>> = repository.podcasts().asLiveData()

    private val _selectedPodcast = MutableLiveData<PodcastEntity?>()
    val selectedPodcast: LiveData<PodcastEntity?> = _selectedPodcast

    private val _episodes = MutableLiveData<List<EpisodeEntity>>(emptyList())
    val episodes: LiveData<List<EpisodeEntity>> = _episodes

    private val _subscribeError = MutableLiveData<String?>()
    val subscribeError: LiveData<String?> = _subscribeError

    private var episodesJob: kotlinx.coroutines.Job? = null

    fun subscribe(feedUrl: String) {
        viewModelScope.launch {
            repository.subscribe(feedUrl.trim())
                .onSuccess { select(it) }
                .onFailure { _subscribeError.postValue(it.message ?: "Failed to subscribe") }
        }
    }

    fun select(podcast: PodcastEntity) {
        _selectedPodcast.value = podcast
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            repository.episodesForPodcast(podcast.id).collect { _episodes.postValue(it) }
        }
    }

    fun refresh(podcast: PodcastEntity) {
        viewModelScope.launch {
            repository.refresh(podcast)
        }
    }

    fun download(episode: EpisodeEntity) {
        viewModelScope.launch {
            downloadManager.enqueue(episode)
        }
    }

    /** Self-heals any episode stuck showing "Downloading…" -- see EpisodeDownloadManager docs. */
    fun reconcileDownloads() {
        viewModelScope.launch {
            downloadManager.reconcileInFlightDownloads(_episodes.value.orEmpty())
        }
    }

    fun consumeSubscribeError() {
        _subscribeError.value = null
    }
}
