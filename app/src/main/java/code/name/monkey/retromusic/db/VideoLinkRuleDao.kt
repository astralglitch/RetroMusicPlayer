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
interface VideoLinkRuleDao {

    @Upsert
    suspend fun upsertRule(rule: VideoLinkRuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: VideoLinkRuleEntity)

    @Query("SELECT * FROM VideoLinkRuleEntity WHERE podcast_id = :podcastId ORDER BY priority ASC, id ASC")
    fun rulesForPodcast(podcastId: Long): Flow<List<VideoLinkRuleEntity>>

    /** Non-Flow variant for use during feed refresh (already off the main thread -- see
     * `PodcastRepository.persistEpisodes`), where a one-shot read is all that's needed. */
    @Query("SELECT * FROM VideoLinkRuleEntity WHERE podcast_id = :podcastId AND enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun enabledRulesForPodcastSync(podcastId: Long): List<VideoLinkRuleEntity>
}
