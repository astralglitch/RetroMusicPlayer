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

import android.os.Bundle
import android.view.View
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastExtraControlsBinding
import code.name.monkey.retromusic.fragments.base.AbsMusicServiceFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.util.PreferenceUtil

/**
 * Podcast-appropriate transport extras (skip ±10/30s, playback speed) hosted by
 * [code.name.monkey.retromusic.fragments.base.AbsPlayerControlsFragment] alongside the base
 * transport controls every [code.name.monkey.retromusic.db.MediaItemType] gets. The podcast name
 * itself isn't shown here -- the surrounding player screen's existing artist-name row already
 * carries it (see [code.name.monkey.retromusic.podcast.EpisodeExtensions.toSong], which sets
 * `artistName` to the podcast title). Skip increments and the speed cycle are fixed for now; the
 * project intent is for these to become per-user configurable, same as everything else here (see
 * docs/PODCAST_DESIGN.md).
 */
class PodcastExtraControlsFragment : AbsMusicServiceFragment(R.layout.fragment_podcast_extra_controls) {

    private var _binding: FragmentPodcastExtraControlsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastExtraControlsBinding.bind(view)

        binding.skipBackButton.setOnClickListener { skip(-SKIP_BACK_MS) }
        binding.skipForwardButton.setOnClickListener { skip(SKIP_FORWARD_MS) }
        binding.speedButton.setOnClickListener { cycleSpeed() }

        updateSpeedLabel()
    }

    private fun skip(deltaMs: Int) {
        val target = (MusicPlayerRemote.songProgressMillis + deltaMs)
            .coerceIn(0, MusicPlayerRemote.songDurationMillis)
        MusicPlayerRemote.seekTo(target)
    }

    private fun cycleSpeed() {
        val currentIndex = SPEED_STEPS.indexOfFirst { it == PreferenceUtil.playbackSpeed }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % SPEED_STEPS.size
        PreferenceUtil.playbackSpeed = SPEED_STEPS[nextIndex]
        updateSpeedLabel()
    }

    private fun updateSpeedLabel() {
        binding.speedLabel.text = "%.2f".format(PreferenceUtil.playbackSpeed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SKIP_BACK_MS = 10_000
        private const val SKIP_FORWARD_MS = 30_000
        private val SPEED_STEPS = floatArrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }
}
