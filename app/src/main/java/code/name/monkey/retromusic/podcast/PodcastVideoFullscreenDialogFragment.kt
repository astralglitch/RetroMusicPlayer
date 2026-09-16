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

import android.app.Dialog
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.DialogPodcastVideoFullscreenBinding
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper

/**
 * Fullscreen video playback -- an immersive overlay within the same screen/Activity rather than
 * a separate Activity (see docs/PODCAST_DESIGN.md "Now Playing screen should be type-aware").
 * Retargets the single ExoPlayer session's video output to this dialog's own
 * [SurfaceView] (see [MusicPlayerRemote.setVideoSurface]) rather than moving the inline
 * [android.view.SurfaceView] itself between view hierarchies, which would risk a surface
 * destroy/recreate glitch. On dismiss, [PodcastVideoFullscreen] flips back to false and
 * [code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment] reattaches its own
 * inline surface in response.
 *
 * Minimal transport controls only (play/pause, seek, skip back/forward) -- no auto-hide, no
 * scrubbing preview, nothing beyond what's needed to actually use it. Auto-hide is a natural
 * follow-up, not built yet.
 */
class PodcastVideoFullscreenDialogFragment : DialogFragment(), MusicProgressViewUpdateHelper.Callback {

    private var _binding: DialogPodcastVideoFullscreenBinding? = null
    private val binding get() = _binding!!
    private var progressViewUpdateHelper: MusicProgressViewUpdateHelper? = null
    private var isUserSeeking = false
    private var aspectMode = VideoAspectMode.FIT
    private var lastVideoWidth = 0
    private var lastVideoHeight = 0

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            MusicPlayerRemote.setVideoSurface(holder.surface)
            MusicPlayerRemote.setVideoSizeListener(::onVideoSizeChanged)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            MusicPlayerRemote.setVideoSizeListener(null)
        }
    }

    private fun onVideoSizeChanged(width: Int, height: Int) {
        lastVideoWidth = width
        lastVideoHeight = height
        applyAspect()
    }

    private fun applyAspect() {
        val binding = _binding ?: return
        applyVideoAspect(binding.fullscreenVideoSurfaceView, lastVideoWidth, lastVideoHeight, aspectMode)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.FullScreenDialogStyle).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPodcastVideoFullscreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PodcastVideoFullscreen.setFullscreen(true)

        binding.fullscreenVideoSurfaceView.holder.addCallback(surfaceCallback)
        binding.fullscreenVideoSurfaceView.setOnClickListener { toggleControlsVisible() }
        binding.closeButton.setOnClickListener { dismiss() }
        binding.fullscreenAspectButton.setOnClickListener { toggleAspectMode() }
        binding.fullscreenPlayPauseButton.setOnClickListener { togglePlayPause() }
        binding.fullscreenSkipBackButton.setOnClickListener { skip(-SKIP_BACK_MS) }
        binding.fullscreenSkipForwardButton.setOnClickListener { skip(SKIP_FORWARD_MS) }
        binding.fullscreenSeekBar.max = MusicPlayerRemote.songDurationMillis.coerceAtLeast(0)
        binding.fullscreenSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                MusicPlayerRemote.seekTo(seekBar.progress)
            }
        })
        updatePlayPauseIcon()

        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this)
    }

    /** Controls start visible (so the close button is discoverable without guessing to tap the
     * video first) and toggle off/on on tap, same pattern as most video players. No auto-hide
     * timer yet -- a plain toggle is enough for now. */
    private fun toggleControlsVisible() {
        binding.controlsOverlay.isVisible = !binding.controlsOverlay.isVisible
    }

    /** Cycles Fit (letterbox, no cropping) <-> Crop (fills the screen, crops overflow). Fit is
     * the default on every open -- this doesn't persist across sessions, matching the fullscreen
     * dialog's generally disposable/no-saved-state nature (see the class doc). */
    private fun toggleAspectMode() {
        aspectMode = if (aspectMode == VideoAspectMode.FIT) VideoAspectMode.CROP else VideoAspectMode.FIT
        applyAspect()
    }

    override fun onResume() {
        super.onResume()
        progressViewUpdateHelper?.start()
    }

    override fun onPause() {
        super.onPause()
        progressViewUpdateHelper?.stop()
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.fullscreenSeekBar.max = total.coerceAtLeast(0)
        if (!isUserSeeking) {
            binding.fullscreenSeekBar.progress = progress
        }
        updatePlayPauseIcon()
    }

    private fun togglePlayPause() {
        if (MusicPlayerRemote.isPlaying) {
            MusicPlayerRemote.pauseSong()
        } else {
            MusicPlayerRemote.resumePlaying()
        }
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        binding.fullscreenPlayPauseButton.setImageResource(
            if (MusicPlayerRemote.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
    }

    private fun skip(deltaMs: Int) {
        val target = (MusicPlayerRemote.songProgressMillis + deltaMs)
            .coerceIn(0, MusicPlayerRemote.songDurationMillis)
        MusicPlayerRemote.seekTo(target)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressViewUpdateHelper?.stop()
        MusicPlayerRemote.setVideoSizeListener(null)
        PodcastVideoFullscreen.setFullscreen(false)
        _binding = null
    }

    companion object {
        const val TAG = "PodcastVideoFullscreenDialogFragment"
        private const val SKIP_BACK_MS = 30_000
        private const val SKIP_FORWARD_MS = 10_000
    }
}
