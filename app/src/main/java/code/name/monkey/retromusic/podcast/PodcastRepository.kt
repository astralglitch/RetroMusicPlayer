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

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import code.name.monkey.retromusic.db.EpisodeDao
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.MediaItemDao
import code.name.monkey.retromusic.db.MediaItemEntity
import code.name.monkey.retromusic.db.MediaItemType
import code.name.monkey.retromusic.db.PodcastDao
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.db.PodcastEpisodeStats
import code.name.monkey.retromusic.db.PlayPreference
import code.name.monkey.retromusic.db.PodcastSourceType
import code.name.monkey.retromusic.db.VideoLinkRuleDao
import code.name.monkey.retromusic.db.VideoLinkRuleEntity
import com.google.gson.Gson
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val mediaItemDao: MediaItemDao,
    private val queueRepository: QueueRepository,
    private val videoLinkRuleDao: VideoLinkRuleDao,
    private val episodeDownloadManager: EpisodeDownloadManager
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

    /** Full (uncapped) lists backing the Podcasts-world tabs -- Favorites/Inbox/Episodes/
     * Downloads/History. The Home sections above use the same underlying data but capped to a
     * handful of items. */
    fun allFavoritedEpisodes(): Flow<List<EpisodeEntity>> = episodeDao.allFavoritedEpisodes()

    fun allNewEpisodes(): Flow<List<EpisodeEntity>> = episodeDao.allNewEpisodes()

    fun allEpisodes(): Flow<List<EpisodeEntity>> = episodeDao.allEpisodes()

    fun playedEpisodes(): Flow<List<EpisodeEntity>> = episodeDao.playedEpisodes()

    fun episodeStats(): Flow<List<PodcastEpisodeStats>> = episodeDao.episodeStats()

    fun downloadedEpisodes(): Flow<List<EpisodeEntity>> =
        episodeDao.episodesByDownloadState(EpisodeDownloadState.DOWNLOADED)

    suspend fun setEpisodeFavorited(episodeId: Long, favorited: Boolean) = withContext(Dispatchers.IO) {
        episodeDao.updateFavorited(episodeId, favorited)
    }

    suspend fun setPodcastFavorited(podcastId: Long, favorited: Boolean) = withContext(Dispatchers.IO) {
        podcastDao.updateFavorited(podcastId, favorited)
    }

    /** [queueId] null means "back to the default queue" -- see QueueRepository. */
    suspend fun setPodcastTargetQueue(podcastId: Long, queueId: Long?) = withContext(Dispatchers.IO) {
        podcastDao.updateTargetQueue(podcastId, queueId)
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
    suspend fun subscribeRss(feedUrl: String): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = podcastDao.podcastForFeedUrl(feedUrl)
            if (existing != null) {
                refresh(existing).getOrThrow()
                return@runCatching existing
            }

            val feed = fetchAndParse(feedUrl)
            val podcastId = podcastDao.upsertPodcast(
                PodcastEntity(
                    feedUrl = feedUrl,
                    title = feed.title.ifBlank { feedUrl },
                    imageUrl = feed.imageUrl,
                    description = feed.description,
                    lastFetched = System.currentTimeMillis(),
                    sourceType = PodcastSourceType.RSS
                )
            )
            persistEpisodes(podcastId, feed.episodes, targetQueueId = null)
            podcastDao.podcastById(podcastId) ?: error("Podcast disappeared right after insert")
        }
    }

    /** Maps [treeUri] (from `ACTION_OPEN_DOCUMENT_TREE`) as a podcast source if not already
     * mapped, then scans its current audio files into episodes. Caller is responsible for
     * `takePersistableUriPermission` on [treeUri] before calling this, so the mapping survives
     * app restarts. */
    suspend fun subscribeFolder(treeUri: Uri, displayName: String): Result<PodcastEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uriString = treeUri.toString()
                val existing = podcastDao.podcastForFolderUri(uriString)
                if (existing != null) {
                    refresh(existing).getOrThrow()
                    return@runCatching existing
                }

                val podcastId = podcastDao.upsertPodcast(
                    PodcastEntity(
                        title = displayName,
                        lastFetched = System.currentTimeMillis(),
                        sourceType = PodcastSourceType.FOLDER,
                        folderUri = uriString
                    )
                )
                val episodes = FolderPodcastScanner.scan(context, treeUri)
                persistEpisodes(podcastId, episodes, targetQueueId = null)
                podcastDao.podcastById(podcastId) ?: error("Podcast disappeared right after insert")
            }
        }

    /** Re-fetches an already-subscribed podcast's episode list -- RSS re-fetch or folder
     * re-scan, dispatched on [PodcastEntity.sourceType]. */
    suspend fun refresh(podcast: PodcastEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (podcast.sourceType) {
                PodcastSourceType.RSS -> {
                    val feedUrl = podcast.feedUrl ?: error("RSS podcast ${podcast.id} has no feed_url")
                    val feed = fetchAndParse(feedUrl)
                    persistEpisodes(podcast.id, feed.episodes, podcast.targetQueueId)
                    podcastDao.upsertPodcast(
                        podcast.copy(
                            title = feed.title.ifBlank { podcast.title },
                            imageUrl = feed.imageUrl ?: podcast.imageUrl,
                            description = feed.description ?: podcast.description,
                            lastFetched = System.currentTimeMillis()
                        )
                    )
                }

                PodcastSourceType.FOLDER -> {
                    val folderUri = podcast.folderUri ?: error("Folder podcast ${podcast.id} has no folder_uri")
                    val episodes = FolderPodcastScanner.scan(context, folderUri.toUri())
                    persistEpisodes(podcast.id, episodes, podcast.targetQueueId)
                    podcastDao.upsertPodcast(podcast.copy(lastFetched = System.currentTimeMillis()))
                }
            }
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

    /** Persists [episodes] (from either an RSS feed or a folder scan), then routes any genuinely
     * new ones (not just updated existing rows) into [targetQueueId], or the default queue if the
     * podcast doesn't have its own -- see the "Multi-queue" section of docs/PODCAST_DESIGN.md.
     * Also registers each episode in [MediaItemEntity], making that table the queryable type
     * registry for the item rather than just an id-range heuristic. Folder-sourced episodes that
     * disappeared from the folder are left in place (not deleted), mirroring how an RSS refresh
     * already treats items dropped from the feed. */
    private suspend fun persistEpisodes(
        podcastId: Long,
        episodes: List<ParsedEpisode>,
        targetQueueId: Long?
    ) {
        // Only ever run for genuinely new rows (an `existing` row already has its own
        // videoSourceUrl/candidates, which must never be clobbered by a later refresh -- whether
        // it was set manually or by an earlier rule match).
        val rules = videoLinkRuleDao.enabledRulesForPodcastSync(podcastId)

        val entities = episodes.map { parsed ->
            val existing = episodeDao.episodeForGuid(podcastId, parsed.guid)
            val matches = if (existing == null && parsed.videoUrl == null) {
                VideoLinkMatcher.findMatches(parsed.description, rules)
            } else {
                emptyList()
            }
            EpisodeEntity(
                id = existing?.id ?: 0,
                podcastId = podcastId,
                guid = parsed.guid,
                title = parsed.title,
                enclosureUrl = parsed.enclosureUrl,
                videoUrl = parsed.videoUrl,
                pubDate = parsed.pubDate,
                durationMs = parsed.durationMs,
                // Feeds frequently omit <enclosure length> (0), or a re-fetch after download
                // could regress a value we already measured from the actual file -- keep
                // whichever is known.
                fileSizeBytes = parsed.fileSizeBytes.takeIf { it > 0 } ?: existing?.fileSizeBytes ?: 0,
                description = parsed.description,
                userNotes = existing?.userNotes,
                localFilePath = existing?.localFilePath,
                downloadState = existing?.downloadState ?: EpisodeDownloadState.NOT_DOWNLOADED,
                downloadId = existing?.downloadId,
                playbackPositionMs = existing?.playbackPositionMs ?: 0,
                played = existing?.played ?: false,
                favorited = existing?.favorited ?: false,
                videoSourceUrl = existing?.videoSourceUrl ?: matches.firstOrNull(),
                videoLinkCandidates = existing?.videoLinkCandidates
                    ?: matches.drop(1).takeIf { it.isNotEmpty() }?.let { Gson().toJson(it) },
                videoDownloadState = existing?.videoDownloadState ?: EpisodeDownloadState.NOT_DOWNLOADED,
                videoDownloadId = existing?.videoDownloadId,
                videoLocalFilePath = existing?.videoLocalFilePath
            )
        }
        // @Upsert returns each row's id for a fresh insert, -1 for a row that instead went
        // through the update path -- exactly the "is this genuinely new" signal we need, with no
        // extra queries.
        val rowIds = episodeDao.upsertEpisodes(entities)
        val newEpisodeIds = rowIds.filter { it != -1L }

        entities.forEachIndexed { index, entity ->
            val episodeId = if (entity.id != 0L) entity.id else rowIds.getOrNull(index)?.takeIf { it != -1L }
            if (episodeId != null) {
                val type = if (entity.videoUrl != null) MediaItemType.PODCAST_VIDEO else MediaItemType.PODCAST_AUDIO
                mediaItemDao.upsertMediaItem(
                    MediaItemEntity(songId = EPISODE_SONG_ID_OFFSET + episodeId, type = type)
                )
            }
        }

        if (newEpisodeIds.isNotEmpty()) {
            val queueId = targetQueueId ?: queueRepository.defaultQueue().id
            newEpisodeIds.forEach { episodeId -> queueRepository.addToQueue(queueId, episodeId) }
            autoDownloadNewEpisodes(podcastId, newEpisodeIds)
        }
    }

    /** Fires the podcast's effective auto-download policy (per-podcast override, falling back
     * to the global default) for each genuinely new episode -- audio defaults on (small, fast
     * files), video defaults off (large, avoid using storage unasked). See
     * `PreferenceUtil.globalAutoDownloadAudio`/`globalAutoDownloadVideo` and
     * `PodcastEntity.autoDownloadAudio`/`autoDownloadVideo`. */
    private suspend fun autoDownloadNewEpisodes(podcastId: Long, newEpisodeIds: List<Long>) {
        val podcast = podcastDao.podcastById(podcastId) ?: return
        val downloadAudio = podcast.autoDownloadAudio ?: PreferenceUtil.globalAutoDownloadAudio
        val downloadVideo = podcast.autoDownloadVideo ?: PreferenceUtil.globalAutoDownloadVideo
        if (!downloadAudio && !downloadVideo) return
        newEpisodeIds.forEach { episodeId ->
            val episode = episodeDao.episodeById(episodeId) ?: return@forEach
            if (downloadAudio) episodeDownloadManager.enqueue(episode)
            if (downloadVideo) episodeDownloadManager.enqueueVideo(episode)
        }
    }

    suspend fun setEpisodeNotes(episodeId: Long, notes: String?) = withContext(Dispatchers.IO) {
        episodeDao.updateUserNotes(episodeId, notes)
    }

    suspend fun setEpisodeVideoSourceUrl(episodeId: Long, url: String?) = withContext(Dispatchers.IO) {
        episodeDao.updateVideoSourceUrl(episodeId, url)
    }

    fun rulesForPodcast(podcastId: Long): Flow<List<VideoLinkRuleEntity>> =
        videoLinkRuleDao.rulesForPodcast(podcastId)

    suspend fun upsertVideoLinkRule(rule: VideoLinkRuleEntity) =
        withContext(Dispatchers.IO) { videoLinkRuleDao.upsertRule(rule) }

    suspend fun deleteVideoLinkRule(rule: VideoLinkRuleEntity) =
        withContext(Dispatchers.IO) { videoLinkRuleDao.deleteRule(rule) }

    suspend fun setPodcastPlayPreference(podcastId: Long, preference: PlayPreference?) =
        withContext(Dispatchers.IO) { podcastDao.updatePlayPreference(podcastId, preference) }

    suspend fun setPodcastAutoDownloadVideo(podcastId: Long, autoDownload: Boolean?) =
        withContext(Dispatchers.IO) { podcastDao.updateAutoDownloadVideo(podcastId, autoDownload) }

    suspend fun setPodcastAutoDownloadAudio(podcastId: Long, autoDownload: Boolean?) =
        withContext(Dispatchers.IO) { podcastDao.updateAutoDownloadAudio(podcastId, autoDownload) }

    suspend fun setPodcastAudioRetentionCount(podcastId: Long, count: Int?) =
        withContext(Dispatchers.IO) { podcastDao.updateAudioRetentionCount(podcastId, count) }

    suspend fun setPodcastVideoRetentionCount(podcastId: Long, count: Int?) =
        withContext(Dispatchers.IO) { podcastDao.updateVideoRetentionCount(podcastId, count) }

    /** Re-runs this podcast's currently-enabled rules against every episode that has neither a
     * feed-direct video ([EpisodeEntity.videoUrl]) nor an already-assigned/matched
     * [EpisodeEntity.videoSourceUrl] -- lets a user who defines rules *after* subscribing pick up
     * episodes fetched before any rule existed. Returns how many episodes were updated, for the
     * caller to report back (see [VideoLinkRulesFragment]). */
    suspend fun reapplyVideoLinkRules(podcastId: Long): Int = withContext(Dispatchers.IO) {
        val rules = videoLinkRuleDao.enabledRulesForPodcastSync(podcastId)
        if (rules.isEmpty()) return@withContext 0

        val candidates = episodeDao.episodesForPodcastSync(podcastId)
            .filter { it.videoUrl == null && it.videoSourceUrl == null }

        var updated = 0
        candidates.forEach { episode ->
            val matches = VideoLinkMatcher.findMatches(episode.description, rules)
            if (matches.isNotEmpty()) {
                episodeDao.upsertEpisode(
                    episode.copy(
                        videoSourceUrl = matches.first(),
                        videoLinkCandidates = matches.drop(1).takeIf { it.isNotEmpty() }?.let { Gson().toJson(it) }
                    )
                )
                updated++
            }
        }
        updated
    }

    /** Only meaningful for an [PodcastSourceType.RSS] podcast -- changing where its feed is
     * fetched from without re-subscribing (e.g. a show moved hosts). Caller should [refresh]
     * afterward to pull episodes from the new URL. */
    suspend fun setPodcastFeedUrl(podcastId: Long, feedUrl: String) = withContext(Dispatchers.IO) {
        podcastDao.updateFeedUrl(podcastId, feedUrl)
    }
}
