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
import kotlinx.coroutines.flow.combine

data class PodcastListeningStat(val title: String, val playedCount: Int, val listenedMs: Long)

/** Everything here is derived from each episode's current saved state, not a listening log --
 * see docs/PODCAST_DESIGN.md "Statistics" for what that does and doesn't count. */
data class PodcastStatistics(
    val subscriptionCount: Int,
    val listenedMs: Long,
    val playedCount: Int,
    val inProgressCount: Int,
    val favoriteCount: Int,
    val downloadedCount: Int,
    val topShows: List<PodcastListeningStat>
)

class PodcastStatisticsViewModel(repository: PodcastRepository) : ViewModel() {

    val statistics: LiveData<PodcastStatistics> =
        combine(repository.podcasts(), repository.episodeStats()) { podcasts, stats ->
            val titlesById = podcasts.associate { it.id to it.title }
            PodcastStatistics(
                subscriptionCount = podcasts.size,
                listenedMs = stats.sumOf { it.listenedMs },
                playedCount = stats.sumOf { it.playedCount },
                inProgressCount = stats.sumOf { it.inProgressCount },
                favoriteCount = stats.sumOf { it.favoriteCount },
                downloadedCount = stats.sumOf { it.downloadedCount },
                topShows = stats
                    .filter { it.listenedMs > 0 }
                    .sortedByDescending { it.listenedMs }
                    .take(TOP_SHOWS_LIMIT)
                    .mapNotNull { stat ->
                        titlesById[stat.podcastId]?.let {
                            PodcastListeningStat(it, stat.playedCount, stat.listenedMs)
                        }
                    }
            )
        }.asLiveData()

    private companion object {
        const val TOP_SHOWS_LIMIT = 5
    }
}
