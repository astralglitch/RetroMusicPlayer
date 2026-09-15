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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.QueueEntity
import kotlinx.coroutines.launch

/**
 * Backs QueueFragment -- see the "Multi-queue" section of docs/PODCAST_DESIGN.md. Shows one
 * queue (the "active" one, persisted in PreferenceUtil.activeQueueId via
 * QueueRepository.activeQueueId()) at a time, with actions to switch/create/rename/delete queues
 * and reorder/remove its episodes.
 */
class QueueViewModel(
    private val queueRepository: QueueRepository
) : ViewModel() {

    val queues: LiveData<List<QueueEntity>> = queueRepository.queues().asLiveData()

    private val _activeQueueId = MutableLiveData<Long>()
    val activeQueueId: LiveData<Long> = _activeQueueId

    val activeQueue: LiveData<QueueEntity?> = activeQueueId.switchMap { id ->
        queues.map { list -> list.firstOrNull { it.id == id } }
    }

    val episodes: LiveData<List<EpisodeEntity>> = activeQueueId.switchMap { id ->
        queueRepository.episodesForQueue(id).asLiveData()
    }

    init {
        viewModelScope.launch {
            _activeQueueId.value = queueRepository.activeQueueId()
        }
    }

    fun switchTo(queueId: Long) {
        queueRepository.setActiveQueue(queueId)
        _activeQueueId.value = queueId
    }

    fun createQueue(name: String, switchToIt: Boolean = true) {
        viewModelScope.launch {
            val id = queueRepository.createQueue(name)
            if (switchToIt) switchTo(id)
        }
    }

    fun renameQueue(queue: QueueEntity, name: String) {
        viewModelScope.launch { queueRepository.renameQueue(queue, name) }
    }

    /** Falls back to the default queue if the deleted queue was the active one. */
    fun deleteQueue(queue: QueueEntity) {
        viewModelScope.launch {
            queueRepository.deleteQueue(queue)
            if (activeQueueId.value == queue.id) {
                switchTo(queueRepository.defaultQueue().id)
            }
        }
    }

    fun removeFromQueue(episode: EpisodeEntity) {
        val queueId = activeQueueId.value ?: return
        viewModelScope.launch { queueRepository.removeFromQueue(queueId, episode.id) }
    }

    fun reorder(orderedEpisodeIds: List<Long>) {
        val queueId = activeQueueId.value ?: return
        viewModelScope.launch { queueRepository.reorder(queueId, orderedEpisodeIds) }
    }
}
