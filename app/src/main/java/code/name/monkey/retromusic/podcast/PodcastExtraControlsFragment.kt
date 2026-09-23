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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import code.name.monkey.retromusic.PLAYBACK_SPEED
import code.name.monkey.retromusic.PODCAST_SKIP_BACK_SECONDS
import code.name.monkey.retromusic.PODCAST_SKIP_FORWARD_SECONDS
import code.name.monkey.retromusic.PODCAST_SPEED_STEPS
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastExtraControlsBinding
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMusicServiceFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.launch

/**
 * Podcast-appropriate transport extras (skip 30s back / 10s forward, playback speed, and --
 * for an episode whose feed published a separate video enclosure, see
 * [code.name.monkey.retromusic.db.EpisodeEntity.videoUrl] -- an audio<->video toggle button)
 * hosted by [code.name.monkey.retromusic.fragments.base.AbsPlayerControlsFragment] alongside the
 * base transport controls every [code.name.monkey.retromusic.db.MediaItemType] gets. The podcast
 * name itself isn't shown here -- the surrounding player screen's existing artist-name row
 * already carries it (see [code.name.monkey.retromusic.podcast.EpisodeExtensions.toSong], which
 * sets `artistName` to the podcast title). Skip increments and the speed-cycle list are both
 * user-configurable now (Settings -> Podcasts).
 *
 * This fragment only owns the toggle *button* -- the actual video surface lives in
 * [code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment], replacing the album
 * art there (same spot across every theme) rather than a small inline view here, per user
 * feedback on the first version of this feature. The two fragments share state via
 * [PodcastVideoToggle] since neither has a direct reference to the other.
 */
class PodcastExtraControlsFragment : AbsMusicServiceFragment(R.layout.fragment_podcast_extra_controls) {

    private var _binding: FragmentPodcastExtraControlsBinding? = null
    private val binding get() = _binding!!

    // PlaybackSpeedDialog (the speed/pitch dialog under the Now Playing overflow menu) writes to
    // the same PreferenceUtil.playbackSpeed this fragment's button does, but only this fragment's
    // own click updated its label -- a change from that other dialog just went unnoticed here.
    // Listening for the underlying preference key directly covers both directions.
    private val speedPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PLAYBACK_SPEED || key == PODCAST_SPEED_STEPS) updateSpeedLabel()
            if (key == PODCAST_SKIP_BACK_SECONDS || key == PODCAST_SKIP_FORWARD_SECONDS) updateSkipLabels()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastExtraControlsBinding.bind(view)

        binding.skipBackButton.setOnClickListener { skip(-PreferenceUtil.podcastSkipBackSeconds * 1000) }
        binding.skipForwardButton.setOnClickListener { skip(PreferenceUtil.podcastSkipForwardSeconds * 1000) }
        binding.speedButton.setOnClickListener { cycleSpeed() }
        updateSkipLabels()

        updateVideoToggleVisibility()
        binding.videoToggleButton.setOnClickListener { onVideoToggleClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PodcastVideoToggle.isActive.collect { active ->
                    binding.videoToggleLabel.setText(
                        if (active) R.string.podcast_watch_audio else R.string.podcast_watch_video
                    )
                }
            }
        }

        updateSpeedLabel()
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(speedPreferenceListener)
        updateSpeedLabel()
        updateSkipLabels()
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(speedPreferenceListener)
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        if (_binding != null) updateVideoToggleVisibility()
    }

    private fun updateVideoToggleVisibility() {
        val song = MusicPlayerRemote.currentSong
        val hasVideo = song.videoUrl != null || song.videoSourceUrl != null
        // isInvisible, not isVisible/isGone -- this sits in a row of equal-weight columns
        // alongside speed/skip-back/skip-forward, and hiding it with GONE let the other three
        // reflow to fill the freed space, shifting their positions every time the current
        // episode's video availability changed.
        binding.videoToggleContainer.isInvisible = !hasVideo
    }

    /** Turning off is always immediate. Turning on: a direct feed video (or an already-resolved
     * external link, see [VideoLinkResolver.peekCached]) activates immediately too -- the actual
     * source swap happens in [code.name.monkey.retromusic.service.MusicService.toggleEpisodeVideo],
     * triggered by [PlayerAlbumCoverFragment]'s surface-creation callback reacting to
     * [PodcastVideoToggle]. An unresolved external link needs a network round-trip first, so this
     * shows a brief loading state on the button and only activates the toggle -- making the video
     * surface appear -- once resolution actually succeeds; a failure never touches the toggle at
     * all, leaving audio playing uninterrupted. */
    private fun onVideoToggleClicked() {
        if (PodcastVideoToggle.isActive.value) {
            PodcastVideoToggle.setActive(false)
            return
        }
        val song = MusicPlayerRemote.currentSong
        if (song.videoUrl != null) {
            PodcastVideoToggle.setActive(true)
            return
        }
        val sourceUrl = song.videoSourceUrl ?: return
        if (VideoLinkResolver.peekCached(sourceUrl) != null) {
            PodcastVideoToggle.setActive(true)
            return
        }
        setVideoToggleLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = VideoLinkResolver.resolve(sourceUrl)
            if (_binding == null) return@launch
            setVideoToggleLoading(false)
            when (result) {
                is VideoLinkResolver.Result.Success -> PodcastVideoToggle.setActive(true)
                is VideoLinkResolver.Result.UnsupportedSite ->
                    requireContext().showToast(getString(R.string.podcast_video_unsupported_site))

                is VideoLinkResolver.Result.ResolutionFailed ->
                    requireContext().showToast(
                        getString(R.string.podcast_video_resolution_failed, result.reason)
                    )
            }
        }
    }

    private fun setVideoToggleLoading(loading: Boolean) {
        binding.videoToggleButton.isEnabled = !loading
        binding.videoToggleButton.isInvisible = loading
        binding.videoToggleLoading.isVisible = loading
    }

    private fun skip(deltaMs: Int) {
        val target = (MusicPlayerRemote.songProgressMillis + deltaMs)
            .coerceIn(0, MusicPlayerRemote.songDurationMillis)
        MusicPlayerRemote.seekTo(target)
    }

    private fun cycleSpeed() {
        val steps = PreferenceUtil.podcastSpeedSteps
        val currentIndex = steps.indexOfFirst { it == PreferenceUtil.playbackSpeed }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % steps.size
        PreferenceUtil.playbackSpeed = steps[nextIndex]
        updateSpeedLabel()
    }

    private fun updateSpeedLabel() {
        binding.speedLabel.text = "%.2f".format(PreferenceUtil.playbackSpeed)
    }

    private fun updateSkipLabels() {
        val back = PreferenceUtil.podcastSkipBackSeconds
        val forward = PreferenceUtil.podcastSkipForwardSeconds
        binding.skipBackLabel.text = back.toString()
        binding.skipForwardLabel.text = forward.toString()
        binding.skipBackButton.contentDescription = getString(R.string.podcast_skip_back_seconds, back)
        binding.skipForwardButton.contentDescription = getString(R.string.podcast_skip_forward_seconds, forward)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
