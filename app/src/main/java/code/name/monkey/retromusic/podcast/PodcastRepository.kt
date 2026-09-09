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
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastDao
import code.name.monkey.retromusic.db.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastRepository(
    private val okHttpClient: OkHttpClient,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) {

    fun podcasts(): Flow<List<PodcastEntity>> = podcastDao.podcasts()

    fun episodesForPodcast(podcastId: Long): Flow<List<EpisodeEntity>> =
        episodeDao.episodesForPodcast(podcastId)

    /** In-progress episodes across all subscriptions, newest first -- backs the Podcasts-world
     * Home "Continue Listening" section. */
    fun continueListening(limit: Int = 12): Flow<List<EpisodeEntity>> =
        episodeDao.continueListening(limit)

    /** Not-yet-started episodes across all subscriptions, newest first -- backs the Podcasts-world
     * Home "New Episodes" section. */
    fun newEpisodes(limit: Int = 12): Flow<List<EpisodeEntity>> =
        episodeDao.newEpisodes(limit)

    /** Starred episodes, newest first -- backs the Podcasts-world Home "Favorites" section. */
    fun favoritedEpisodes(limit: Int = 12): Flow<List<EpisodeEntity>> =
        episodeDao.favoritedEpisodes(limit)

    /** Starred subscriptions -- kept for parity with episode favorites; not surfaced as its own
     * Home section yet. */
    fun favoritedPodcasts(): Flow<List<PodcastEntity>> = podcastDao.favoritedPodcasts()

    /** A random pick of not-yet-played episodes across all subscriptions -- backs the
     * Podcasts-world Home "Suggestions" section. */
    fun randomUnplayedEpisodes(limit: Int = 10): Flow<List<EpisodeEntity>> =
        episodeDao.randomUnplayedEpisodes(limit)

    suspend fun setEpisodeFavorited(episodeId: Long, favorited: Boolean) = withContext(Dispatchers.IO) {
        episodeDao.updateFavorited(episodeId, favorited)
    }

    suspend fun setPodcastFavorited(podcastId: Long, favorited: Boolean) = withContext(Dispatchers.IO) {
        podcastDao.updateFavorited(podcastId, favorited)
    }

    suspend fun podcastById(podcastId: Long): PodcastEntity? = podcastDao.podcastById(podcastId)

    /** Live-updating single episode -- backs EpisodeDetailsFragment. */
    fun episode(episodeId: Long): Flow<EpisodeEntity?> = episodeDao.observeEpisode(episodeId)

    suspend fun setEpisodePlayed(episodeId: Long, played: Boolean) = withContext(Dispatchers.IO) {
        episodeDao.updatePlayed(episodeId, played)
    }

    /** Removes the podcast and its episode rows. Caller is responsible for cleaning up any
     * downloaded episode files first (see EpisodeDownloadManager) -- this repository doesn't
     * know about the download layer. */
    suspend fun unsubscribe(podcast: PodcastEntity) = withContext(Dispatchers.IO) {
        episodeDao.deleteEpisodesForPodcast(podcast.id)
        podcastDao.deletePodcast(podcast)
    }

    /** Subscribes to [feedUrl] if not already subscribed, then fetches its current episode list. */
    suspend fun subscribe(feedUrl: String): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = podcastDao.podcastForFeedUrl(feedUrl)
            if (existing != null) {
                refresh(existing)
                return@runCatching existing
            }

            val feed = fetchAndParse(feedUrl)
            val podcastId = podcastDao.upsertPodcast(
                PodcastEntity(
                    feedUrl = feedUrl,
                    title = feed.title.ifBlank { feedUrl },
                    imageUrl = feed.imageUrl,
                    description = feed.description,
                    lastFetched = System.currentTimeMillis()
                )
            )
            persistEpisodes(podcastId, feed)
            podcastDao.podcastById(podcastId) ?: error("Podcast disappeared right after insert")
        }
    }

    /** Re-fetches an already-subscribed feed and upserts any new/changed episodes. */
    suspend fun refresh(podcast: PodcastEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val feed = fetchAndParse(podcast.feedUrl)
            persistEpisodes(podcast.id, feed)
            podcastDao.upsertPodcast(
                PodcastEntity(
                    id = podcast.id,
                    feedUrl = podcast.feedUrl,
                    title = feed.title.ifBlank { podcast.title },
                    imageUrl = feed.imageUrl ?: podcast.imageUrl,
                    description = feed.description ?: podcast.description,
                    lastFetched = System.currentTimeMillis(),
                    favorited = podcast.favorited
                )
            )
            Unit
        }
    }

    private fun fetchAndParse(feedUrl: String): ParsedFeed {
        val request = Request.Builder().url(feedUrl).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to fetch feed ($feedUrl): HTTP ${response.code}")
            }
            val body = response.body ?: error("Empty response body for feed $feedUrl")
            return body.byteStream().use { PodcastFeedParser.parse(it) }
        }
    }

    private suspend fun persistEpisodes(podcastId: Long, feed: ParsedFeed) {
        val entities = feed.episodes.map { parsed ->
            val existing = episodeDao.episodeForGuid(podcastId, parsed.guid)
            EpisodeEntity(
                id = existing?.id ?: 0,
                podcastId = podcastId,
                guid = parsed.guid,
                title = parsed.title,
                enclosureUrl = parsed.enclosureUrl,
                pubDate = parsed.pubDate,
                durationMs = parsed.durationMs,
                description = parsed.description,
                localFilePath = existing?.localFilePath,
                downloadState = existing?.downloadState ?: EpisodeDownloadState.NOT_DOWNLOADED,
                downloadId = existing?.downloadId,
                playbackPositionMs = existing?.playbackPositionMs ?: 0,
                played = existing?.played ?: false,
                favorited = existing?.favorited ?: false
            )
        }
        episodeDao.upsertEpisodes(entities)
    }
}
