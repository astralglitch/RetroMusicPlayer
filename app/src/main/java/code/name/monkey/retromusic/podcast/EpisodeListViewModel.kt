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
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.launch

/** Which full (uncapped) episode list a [EpisodeListViewModel]/[AbsEpisodeListFragment] shows --
 * one shared pair of classes for every simple "list of episodes across all subscriptions"
 * Podcasts-world tab, same idea as [AbsPodcastPlaceholderFragment] before these had real
 * screens. */
enum class EpisodeListKind { FAVORITES, DOWNLOADS, HISTORY, INBOX, ALL }

class EpisodeListViewModel(
    private val repository: PodcastRepository,
    private val downloadManager: EpisodeDownloadManager,
    kind: EpisodeListKind
) : ViewModel() {

    val episodes: LiveData<List<EpisodeEntity>> = when (kind) {
        EpisodeListKind.FAVORITES -> repository.allFavoritedEpisodes()
        EpisodeListKind.DOWNLOADS -> repository.downloadedEpisodes()
        EpisodeListKind.HISTORY -> repository.playedEpisodes()
        EpisodeListKind.INBOX -> repository.allNewEpisodes()
        EpisodeListKind.ALL -> repository.allEpisodes()
    }.asLiveData()

    val podcasts: LiveData<List<PodcastEntity>> = repository.podcasts().asLiveData()

    fun setPlayed(episode: EpisodeEntity, played: Boolean) {
        viewModelScope.launch { repository.setEpisodePlayed(episode.id, played) }
    }

    fun setFavorited(episode: EpisodeEntity, favorited: Boolean) {
        viewModelScope.launch { repository.setEpisodeFavorited(episode.id, favorited) }
    }

    fun download(episode: EpisodeEntity) {
        viewModelScope.launch { downloadManager.enqueue(episode) }
    }

    fun deleteDownload(episode: EpisodeEntity) {
        viewModelScope.launch { downloadManager.deleteDownload(episode) }
    }
}
