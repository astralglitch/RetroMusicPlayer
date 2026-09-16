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
import androidx.lifecycle.viewModelScope
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.db.VideoLinkMatchType
import code.name.monkey.retromusic.db.VideoLinkRuleEntity
import kotlinx.coroutines.launch

/** Scoped to one podcast, backing [VideoLinkRulesFragment]. */
class VideoLinkRulesViewModel(
    private val repository: PodcastRepository,
    private val podcastId: Long
) : ViewModel() {

    private val _podcast = MutableLiveData<PodcastEntity?>()
    val podcast: LiveData<PodcastEntity?> = _podcast

    val rules: LiveData<List<VideoLinkRuleEntity>> = repository.rulesForPodcast(podcastId).asLiveData()

    init {
        viewModelScope.launch { _podcast.value = repository.podcastById(podcastId) }
    }

    fun addPresetRule(presetId: String) {
        viewModelScope.launch {
            val nextPriority = (rules.value?.maxOfOrNull { it.priority } ?: -1) + 1
            repository.upsertVideoLinkRule(
                VideoLinkRuleEntity(
                    podcastId = podcastId,
                    matchType = VideoLinkMatchType.PRESET,
                    pattern = presetId,
                    priority = nextPriority
                )
            )
        }
    }

    fun addCustomRule(regex: String) {
        viewModelScope.launch {
            val nextPriority = (rules.value?.maxOfOrNull { it.priority } ?: -1) + 1
            repository.upsertVideoLinkRule(
                VideoLinkRuleEntity(
                    podcastId = podcastId,
                    matchType = VideoLinkMatchType.REGEX,
                    pattern = regex,
                    priority = nextPriority
                )
            )
        }
    }

    fun setRuleEnabled(rule: VideoLinkRuleEntity, enabled: Boolean) {
        viewModelScope.launch { repository.upsertVideoLinkRule(rule.copy(enabled = enabled)) }
    }

    fun deleteRule(rule: VideoLinkRuleEntity) {
        viewModelScope.launch { repository.deleteVideoLinkRule(rule) }
    }

    fun reapplyRules(onDone: (updatedCount: Int) -> Unit) {
        viewModelScope.launch { onDone(repository.reapplyVideoLinkRules(podcastId)) }
    }
}
