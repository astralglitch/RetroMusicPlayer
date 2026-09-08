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
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Upsert
    suspend fun upsertEpisode(episodeEntity: EpisodeEntity): Long

    @Upsert
    suspend fun upsertEpisodes(episodeEntities: List<EpisodeEntity>)

    @Query("SELECT * FROM EpisodeEntity WHERE podcast_id = :podcastId ORDER BY pub_date DESC")
    fun episodesForPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity WHERE id = :episodeId LIMIT 1")
    suspend fun episodeById(episodeId: Long): EpisodeEntity?

    @Query("SELECT * FROM EpisodeEntity WHERE download_id = :downloadId LIMIT 1")
    suspend fun episodeForDownloadId(downloadId: Long): EpisodeEntity?

    @Query("SELECT * FROM EpisodeEntity WHERE podcast_id = :podcastId AND guid = :guid LIMIT 1")
    suspend fun episodeForGuid(podcastId: Long, guid: String): EpisodeEntity?
}
