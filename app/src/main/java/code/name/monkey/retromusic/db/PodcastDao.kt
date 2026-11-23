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
package code.name.monkey.retromusic.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Upsert
    suspend fun upsertPodcast(podcastEntity: PodcastEntity): Long

    @Delete
    suspend fun deletePodcast(podcastEntity: PodcastEntity)

    @Query("SELECT * FROM PodcastEntity ORDER BY title ASC")
    fun podcasts(): Flow<List<PodcastEntity>>

    /** Local podcast search; [pattern] is a pre-escaped LIKE pattern. */
    @Query(
        "SELECT * FROM PodcastEntity WHERE title LIKE :pattern ESCAPE '\\' " +
            "OR description LIKE :pattern ESCAPE '\\' " +
            "ORDER BY (title LIKE :pattern ESCAPE '\\') DESC, title ASC"
    )
    fun searchPodcasts(pattern: String): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM PodcastEntity WHERE feed_url = :feedUrl LIMIT 1")
    suspend fun podcastForFeedUrl(feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM PodcastEntity WHERE folder_uri = :folderUri LIMIT 1")
    suspend fun podcastForFolderUri(folderUri: String): PodcastEntity?

    @Query("SELECT * FROM PodcastEntity WHERE id = :podcastId LIMIT 1")
    suspend fun podcastById(podcastId: Long): PodcastEntity?

    @Query("SELECT * FROM PodcastEntity WHERE favorited = 1 ORDER BY title ASC")
    fun favoritedPodcasts(): Flow<List<PodcastEntity>>

    @Query("UPDATE PodcastEntity SET favorited = :favorited WHERE id = :podcastId")
    suspend fun updateFavorited(podcastId: Long, favorited: Boolean)

    @Query("UPDATE PodcastEntity SET target_queue_id = :queueId WHERE id = :podcastId")
    suspend fun updateTargetQueue(podcastId: Long, queueId: Long?)

    @Query("UPDATE PodcastEntity SET feed_url = :feedUrl WHERE id = :podcastId")
    suspend fun updateFeedUrl(podcastId: Long, feedUrl: String)

    /** Null means "use PreferenceUtil.globalPlayPreference" -- see PlayPreference. */
    @Query("UPDATE PodcastEntity SET play_preference = :preference WHERE id = :podcastId")
    suspend fun updatePlayPreference(podcastId: Long, preference: PlayPreference?)

    /** Null means "use PreferenceUtil.globalAutoDownloadVideo". */
    @Query("UPDATE PodcastEntity SET auto_download_video = :autoDownload WHERE id = :podcastId")
    suspend fun updateAutoDownloadVideo(podcastId: Long, autoDownload: Boolean?)

    /** Null means "use PreferenceUtil.globalAutoDownloadAudio". */
    @Query("UPDATE PodcastEntity SET auto_download_audio = :autoDownload WHERE id = :podcastId")
    suspend fun updateAutoDownloadAudio(podcastId: Long, autoDownload: Boolean?)

    /** Null means "use PreferenceUtil.globalAudioRetentionCount"; -1 means unlimited. */
    @Query("UPDATE PodcastEntity SET audio_retention_count = :count WHERE id = :podcastId")
    suspend fun updateAudioRetentionCount(podcastId: Long, count: Int?)

    /** Null means "use PreferenceUtil.globalVideoRetentionCount"; -1 means unlimited. */
    @Query("UPDATE PodcastEntity SET video_retention_count = :count WHERE id = :podcastId")
    suspend fun updateVideoRetentionCount(podcastId: Long, count: Int?)
}
