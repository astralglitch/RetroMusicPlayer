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

import android.view.View
import android.view.ViewGroup

/** How a video `SurfaceView` should be sized relative to its parent, given the video's natural
 * (width, height). Shared by the inline surface ([code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment],
 * [Fit] only) and the fullscreen dialog ([code.name.monkey.retromusic.podcast.PodcastVideoFullscreenDialogFragment],
 * both, user-toggleable) so both places size video consistently instead of each rolling their own
 * math (or, as the fullscreen dialog did before, none at all -- the cause of it stretching video
 * to fill the screen regardless of aspect ratio). */
enum class VideoAspectMode {
    /** Letterbox: fit entirely inside the parent, preserving aspect ratio (no cropping, may leave
     * bars). The long-standing default. */
    FIT,

    /** Fill the parent completely, preserving aspect ratio (crops whichever dimension overflows). */
    CROP
}

/** Computes and applies new `layoutParams` width/height for [surfaceView] so it renders the
 * `(videoWidth, videoHeight)` video at its correct aspect ratio within `surfaceView`'s parent,
 * per [mode]. No-op if the parent isn't laid out yet or `videoWidth`/`videoHeight` are invalid. */
fun applyVideoAspect(surfaceView: View, videoWidth: Int, videoHeight: Int, mode: VideoAspectMode) {
    if (videoWidth <= 0 || videoHeight <= 0) return
    val parent = surfaceView.parent as? View ?: return
    val parentWidth = parent.width
    val parentHeight = parent.height
    if (parentWidth <= 0) return

    val fitHeight = (parentWidth.toFloat() * videoHeight / videoWidth).toInt().coerceAtLeast(1)

    val (width, height) = if (mode == VideoAspectMode.FIT || parentHeight <= 0 || fitHeight <= parentHeight) {
        // FIT (or CROP where fit-width already covers the parent's height): pin width, derive height.
        ViewGroup.LayoutParams.MATCH_PARENT to fitHeight
    } else {
        // CROP and fit-width would leave the parent's height uncovered -- pin height, derive (wider) width instead.
        val fitWidth = (parentHeight.toFloat() * videoWidth / videoHeight).toInt().coerceAtLeast(1)
        fitWidth to ViewGroup.LayoutParams.MATCH_PARENT
    }

    surfaceView.layoutParams = surfaceView.layoutParams.apply {
        this.width = width
        this.height = height
    }
}
