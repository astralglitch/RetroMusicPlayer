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
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A short vertical-list section shows at most this many items -- "See all" opens the full tab. */
private const val LIST_SECTION_LIMIT = 3

/**
 * Backs the Podcasts-world Home tab (PodcastHomeFragment) -- assembles the same kind of
 * "different sections" list the Music world's LibraryViewModel.getHome() builds, just over
 * podcast concepts (suggested/in-progress/favorited/new episodes, subscribed shows) instead of
 * albums/artists/playlists. Empty sections are dropped, same as Repository.homeSections().
 */
class PodcastHomeViewModel(
    private val repository: PodcastRepository
) : ViewModel() {

    /** All subscriptions -- also used by PodcastHomeAdapter to look up cover art for the
     * Suggestions section's episodes, which don't carry their own artwork. */
    val podcasts: LiveData<List<PodcastEntity>> = repository.podcasts().asLiveData()

    val homeSections: LiveData<List<PodcastHome>> = combine(
        repository.randomUnplayedEpisodes(10),
        repository.podcasts(),
        repository.favoritedEpisodes(LIST_SECTION_LIMIT),
        repository.newEpisodes(LIST_SECTION_LIMIT),
        repository.continueListening(LIST_SECTION_LIMIT)
    ) { suggestions, podcasts, favorites, inbox, continueListening ->
        buildList {
            if (suggestions.isNotEmpty()) {
                add(PodcastHome(suggestions, PODCAST_SUGGESTIONS, R.string.podcast_home_suggestions))
            }
            if (podcasts.isNotEmpty()) {
                add(PodcastHome(podcasts, PODCAST_TOP_SUBSCRIPTIONS, R.string.podcast_home_top_subscriptions))
            }
            if (favorites.isNotEmpty()) {
                add(PodcastHome(favorites, PODCAST_FAVORITES, R.string.podcast_home_favorites))
            }
            if (inbox.isNotEmpty()) {
                add(PodcastHome(inbox, PODCAST_INBOX, R.string.podcast_home_inbox))
            }
            if (continueListening.isNotEmpty()) {
                add(PodcastHome(continueListening, PODCAST_CONTINUE_LISTENING, R.string.podcast_home_continue_listening))
            }
        }
    }.asLiveData()

    fun setEpisodeFavorited(episode: EpisodeEntity, favorited: Boolean) {
        viewModelScope.launch {
            repository.setEpisodeFavorited(episode.id, favorited)
        }
    }
}
