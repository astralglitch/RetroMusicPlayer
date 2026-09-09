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

    @Query("SELECT * FROM EpisodeEntity WHERE id = :episodeId LIMIT 1")
    fun observeEpisode(episodeId: Long): Flow<EpisodeEntity?>

    @Query("SELECT * FROM EpisodeEntity WHERE download_id = :downloadId LIMIT 1")
    suspend fun episodeForDownloadId(downloadId: Long): EpisodeEntity?

    @Query("SELECT * FROM EpisodeEntity WHERE podcast_id = :podcastId AND guid = :guid LIMIT 1")
    suspend fun episodeForGuid(podcastId: Long, guid: String): EpisodeEntity?

    @Query("UPDATE EpisodeEntity SET playback_position_ms = :positionMs WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long)

    @Query("DELETE FROM EpisodeEntity WHERE podcast_id = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: Long)

    @Query("UPDATE EpisodeEntity SET played = :played WHERE id = :episodeId")
    suspend fun updatePlayed(episodeId: Long, played: Boolean)

    @Query("SELECT * FROM EpisodeEntity WHERE playback_position_ms > 0 AND played = 0 ORDER BY pub_date DESC LIMIT :limit")
    fun continueListening(limit: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity WHERE playback_position_ms = 0 AND played = 0 ORDER BY pub_date DESC LIMIT :limit")
    fun newEpisodes(limit: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity WHERE favorited = 1 ORDER BY pub_date DESC LIMIT :limit")
    fun favoritedEpisodes(limit: Int): Flow<List<EpisodeEntity>>

    /** A fresh random pick of not-yet-played episodes every time the underlying table changes --
     * backs the Podcasts-world Home "Suggestions" section. */
    @Query("SELECT * FROM EpisodeEntity WHERE played = 0 ORDER BY RANDOM() LIMIT :limit")
    fun randomUnplayedEpisodes(limit: Int): Flow<List<EpisodeEntity>>

    @Query("UPDATE EpisodeEntity SET favorited = :favorited WHERE id = :episodeId")
    suspend fun updateFavorited(episodeId: Long, favorited: Boolean)

    @Query("SELECT * FROM EpisodeEntity WHERE download_state = :state ORDER BY pub_date DESC")
    fun episodesByDownloadState(state: EpisodeDownloadState): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity WHERE played = 1 ORDER BY pub_date DESC")
    fun playedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity WHERE favorited = 1 ORDER BY pub_date DESC")
    fun allFavoritedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity WHERE playback_position_ms = 0 AND played = 0 ORDER BY pub_date DESC")
    fun allNewEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM EpisodeEntity ORDER BY pub_date DESC")
    fun allEpisodes(): Flow<List<EpisodeEntity>>
}
