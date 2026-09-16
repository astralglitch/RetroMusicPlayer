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
 * Whether the podcast video is currently shown fullscreen ([PodcastVideoFullscreenDialogFragment]
 * is up), observed by [code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment] so
 * it knows to hand the video surface back to its own inline [android.view.SurfaceView] once
 * fullscreen closes. Same reasoning as [PodcastVideoToggle] for keeping this as a tiny observable
 * singleton rather than a callback interface threaded through the player fragment hierarchy.
 */
object PodcastVideoFullscreen {
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }
}
