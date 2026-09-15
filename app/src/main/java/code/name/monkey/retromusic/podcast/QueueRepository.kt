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

import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.QueueDao
import code.name.monkey.retromusic.db.QueueEntity
import code.name.monkey.retromusic.db.QueueItemEntity
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Multi-queue support -- see the "Multi-queue" section of docs/PODCAST_DESIGN.md. Every install
 * has exactly one [QueueEntity.isDefault] queue (seeded by MIGRATION_28_29), which can't be
 * deleted and is where [PodcastRepository.persistEpisodes] auto-adds a podcast's new episodes
 * unless that podcast has its own [code.name.monkey.retromusic.db.PodcastEntity.targetQueueId].
 */
class QueueRepository(
    private val queueDao: QueueDao
) {
    fun queues(): Flow<List<QueueEntity>> = queueDao.queues()

    fun episodesForQueue(queueId: Long): Flow<List<EpisodeEntity>> = queueDao.episodesForQueue(queueId)

    suspend fun defaultQueue(): QueueEntity =
        queueDao.defaultQueue() ?: error("No default queue -- MIGRATION_28_29 didn't run?")

    /** Resolves PreferenceUtil.activeQueueId, falling back to the default queue if unset or if
     * that queue was since deleted. */
    suspend fun activeQueueId(): Long {
        val stored = PreferenceUtil.activeQueueId
        if (stored > 0 && queueDao.queueById(stored) != null) return stored
        return defaultQueue().id
    }

    /** The universal long-press "Add to queue" entry point -- adds to whichever queue is
     * currently active rather than asking which queue every time. */
    suspend fun addToActiveQueue(episodeId: Long) = withContext(Dispatchers.IO) {
        addToQueue(activeQueueId(), episodeId)
    }

    fun setActiveQueue(queueId: Long) {
        PreferenceUtil.activeQueueId = queueId
    }

    suspend fun createQueue(name: String): Long = withContext(Dispatchers.IO) {
        queueDao.upsertQueue(QueueEntity(name = name))
    }

    suspend fun renameQueue(queue: QueueEntity, name: String) = withContext(Dispatchers.IO) {
        queueDao.upsertQueue(queue.copy(name = name))
    }

    /** The default queue can't be deleted -- callers should check [QueueEntity.isDefault] first
     * and steer the user away from offering this action for it. */
    suspend fun deleteQueue(queue: QueueEntity) = withContext(Dispatchers.IO) {
        if (queue.isDefault) return@withContext
        queueDao.clearQueue(queue.id)
        queueDao.deleteQueue(queue)
    }

    /** Adds [episodeId] to the end of [queueId], unless it's already in there. */
    suspend fun addToQueue(queueId: Long, episodeId: Long) = withContext(Dispatchers.IO) {
        if (queueDao.containsEpisode(queueId, episodeId)) return@withContext
        val nextPosition = queueDao.maxPosition(queueId) + 1
        queueDao.upsertItem(QueueItemEntity(queueId = queueId, episodeId = episodeId, position = nextPosition))
    }

    suspend fun removeFromQueue(queueId: Long, episodeId: Long) = withContext(Dispatchers.IO) {
        queueDao.deleteItem(queueId, episodeId)
    }

    /** Rewrites every item's position to match [orderedEpisodeIds] -- the full list, in the new
     * order, after a drag-reorder. Queues are short enough that a full rewrite is simpler than
     * patching individual positions.
     *
     * Carries over each row's existing primary key (looked up by episode id) so @Upsert resolves
     * against it and does an UPDATE -- passing fresh `id = 0` rows here would instead try to
     * INSERT new rows and collide with the (queue_id, episode_id) unique index. */
    suspend fun reorder(queueId: Long, orderedEpisodeIds: List<Long>) = withContext(Dispatchers.IO) {
        val existingIdByEpisode = queueDao.itemsForQueueOnce(queueId).associate { it.episodeId to it.id }
        val items = orderedEpisodeIds.mapIndexed { index, episodeId ->
            QueueItemEntity(
                id = existingIdByEpisode[episodeId] ?: 0,
                queueId = queueId,
                episodeId = episodeId,
                position = index
            )
        }
        queueDao.upsertItems(items)
    }
}
