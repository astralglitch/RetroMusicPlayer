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

import androidx.annotation.IntDef
import androidx.annotation.StringRes

const val PODCAST_CONTINUE_LISTENING = 0
const val PODCAST_NEW_EPISODES = 1
const val PODCAST_YOUR_PODCASTS = 2

@IntDef(PODCAST_CONTINUE_LISTENING, PODCAST_NEW_EPISODES, PODCAST_YOUR_PODCASTS)
@Retention(AnnotationRetention.SOURCE)
annotation class PodcastHomeSection

/** A Podcasts-world Home section, mirroring [code.name.monkey.retromusic.model.Home] for the
 * Music world -- [items] is either a list of EpisodeEntity (episode sections) or PodcastEntity
 * (the podcasts section). */
data class PodcastHome(
    val items: List<Any>,
    @PodcastHomeSection
    val section: Int,
    @StringRes
    val titleRes: Int
)
