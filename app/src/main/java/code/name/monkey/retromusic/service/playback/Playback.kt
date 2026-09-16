/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.service.playback

import android.net.Uri
import android.view.Surface
import code.name.monkey.retromusic.model.Song


interface Playback {

    val isInitialized: Boolean

    val isPlaying: Boolean

    val audioSessionId: Int

    fun setDataSource(
        song: Song, force: Boolean, completion: (success: Boolean) -> Unit,
    )

    fun setNextDataSource(path: Uri?)

    var callbacks: PlaybackCallbacks?

    fun start(): Boolean

    fun stop()

    fun release()

    fun pause(): Boolean

    fun duration(): Int

    fun position(): Int

    fun seek(whereto: Int, force: Boolean): Int

    fun setVolume(vol: Float): Boolean

    fun setAudioSessionId(sessionId: Int): Boolean

    fun setCrossFadeDuration(duration: Int)

    fun setPlaybackSpeedPitch(speed: Float, pitch: Float)

    /** Attaches (or, with null, detaches) a video output surface -- only meaningful for a
     * [Playback] backed by an actual video-capable player (see `RetroExoPlayer`). A default
     * no-op here so `CrossFadePlayer`/`CastPlayer` don't need to care about video at all. */
    fun setVideoSurface(surface: Surface?) {}

    /** Swaps the currently loaded media source for [uri] in place, resuming at
     * [resumePositionMs] and preserving whatever the play/pause state was -- the audio<->video
     * toggle's "capture position, swap, seek, resume" sequence for one [Playback] instance. No-op
     * by default, same reasoning as [setVideoSurface]. */
    fun swapMediaSource(uri: Uri, resumePositionMs: Long) {}

    /** Reports the natural (width, height) of the current video track as it becomes known/
     * changes, so a UI showing the video surface can size it to fit without stretching. No-op
     * by default, same reasoning as [setVideoSurface]. */
    fun setVideoSizeListener(listener: ((width: Int, height: Int) -> Unit)?) {}

    interface PlaybackCallbacks {
        fun onTrackWentToNext()

        fun onTrackEnded()

        fun onTrackEndedWithCrossfade()

        fun onPlayStateChanged()
    }
}
