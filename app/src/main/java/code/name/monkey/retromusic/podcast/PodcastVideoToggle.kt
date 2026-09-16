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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared on/off state for the podcast audio<->video toggle, observed by two independent player
 * fragments that otherwise have no direct reference to each other:
 * [PodcastExtraControlsFragment] owns the toggle button, [code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment]
 * owns the actual video surface (it replaces the album art there, same spot for every theme).
 * A tiny observable singleton is simpler than wiring a callback interface through
 * `AbsPlayerControlsFragment`/`AbsPlayerFragment`, which don't otherwise relate these two.
 */
object PodcastVideoToggle {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    fun setActive(active: Boolean) {
        _isActive.value = active
    }
}
