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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import code.name.monkey.retromusic.R
import kotlinx.coroutines.flow.combine

/**
 * Backs the Podcasts-world Home tab (PodcastHomeFragment) -- assembles the same kind of
 * "different sections" list the Music world's LibraryViewModel.getHome() builds, just over
 * podcast concepts (in-progress episodes, new episodes, subscribed shows) instead of
 * albums/artists/playlists. Empty sections are dropped, same as Repository.homeSections().
 */
class PodcastHomeViewModel(
    repository: PodcastRepository
) : ViewModel() {

    val homeSections: LiveData<List<PodcastHome>> = combine(
        repository.continueListening(),
        repository.newEpisodes(),
        repository.podcasts()
    ) { continueListening, newEpisodes, podcasts ->
        buildList {
            if (continueListening.isNotEmpty()) {
                add(PodcastHome(continueListening, PODCAST_CONTINUE_LISTENING, R.string.podcast_home_continue_listening))
            }
            if (newEpisodes.isNotEmpty()) {
                add(PodcastHome(newEpisodes, PODCAST_NEW_EPISODES, R.string.podcast_home_new_episodes))
            }
            if (podcasts.isNotEmpty()) {
                add(PodcastHome(podcasts, PODCAST_YOUR_PODCASTS, R.string.podcast_home_your_podcasts))
            }
        }
    }.asLiveData()
}
