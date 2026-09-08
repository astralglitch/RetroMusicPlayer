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
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.launch

/**
 * Backs the podcasts grid tab (PodcastsFragment) -- just the subscribed-podcasts list and
 * subscribing to new ones. Episode browsing/downloading is scoped per podcast now, in
 * PodcastDetailsViewModel.
 */
class PodcastsViewModel(
    private val repository: PodcastRepository
) : ViewModel() {

    val podcasts: LiveData<List<PodcastEntity>> = repository.podcasts().asLiveData()

    private val _subscribeError = MutableLiveData<String?>()
    val subscribeError: LiveData<String?> = _subscribeError

    fun subscribe(feedUrl: String) {
        viewModelScope.launch {
            repository.subscribe(feedUrl.trim())
                .onFailure { _subscribeError.postValue(it.message ?: "Failed to subscribe") }
        }
    }

    fun consumeSubscribeError() {
        _subscribeError.value = null
    }
}
