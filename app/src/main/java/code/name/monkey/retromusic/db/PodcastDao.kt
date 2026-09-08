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

    @Query("SELECT * FROM PodcastEntity WHERE feed_url = :feedUrl LIMIT 1")
    suspend fun podcastForFeedUrl(feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM PodcastEntity WHERE id = :podcastId LIMIT 1")
    suspend fun podcastById(podcastId: Long): PodcastEntity?
}
