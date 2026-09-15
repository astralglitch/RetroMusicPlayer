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
interface QueueDao {

    @Upsert
    suspend fun upsertQueue(queue: QueueEntity): Long

    @Delete
    suspend fun deleteQueue(queue: QueueEntity)

    @Query("SELECT * FROM QueueEntity ORDER BY created_at ASC")
    fun queues(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM QueueEntity WHERE id = :queueId LIMIT 1")
    suspend fun queueById(queueId: Long): QueueEntity?

    @Query("SELECT * FROM QueueEntity WHERE is_default = 1 LIMIT 1")
    suspend fun defaultQueue(): QueueEntity?

    @Upsert
    suspend fun upsertItem(item: QueueItemEntity): Long

    @Upsert
    suspend fun upsertItems(items: List<QueueItemEntity>)

    @Query("DELETE FROM QueueItemEntity WHERE queue_id = :queueId AND episode_id = :episodeId")
    suspend fun deleteItem(queueId: Long, episodeId: Long)

    @Query("DELETE FROM QueueItemEntity WHERE queue_id = :queueId")
    suspend fun clearQueue(queueId: Long)

    @Query("SELECT * FROM QueueItemEntity WHERE queue_id = :queueId ORDER BY position ASC")
    fun itemsForQueue(queueId: Long): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM QueueItemEntity WHERE queue_id = :queueId ORDER BY position ASC")
    suspend fun itemsForQueueOnce(queueId: Long): List<QueueItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM QueueItemEntity WHERE queue_id = :queueId")
    suspend fun maxPosition(queueId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM QueueItemEntity WHERE queue_id = :queueId AND episode_id = :episodeId)")
    suspend fun containsEpisode(queueId: Long, episodeId: Long): Boolean

    /** Every episode across every queue joined with its EpisodeEntity, ordered per-queue by
     * position -- lets the repository build each queue's episode list in one query instead of N. */
    @Query(
        "SELECT EpisodeEntity.* FROM QueueItemEntity " +
            "INNER JOIN EpisodeEntity ON EpisodeEntity.id = QueueItemEntity.episode_id " +
            "WHERE QueueItemEntity.queue_id = :queueId " +
            "ORDER BY QueueItemEntity.position ASC"
    )
    fun episodesForQueue(queueId: Long): Flow<List<EpisodeEntity>>
}
