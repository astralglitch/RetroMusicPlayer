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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastsBinding
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMusicServiceFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Bare-bones subscribe / list episodes / play / download screen — the "get the basics working"
 * milestone from the podcast build-order notes (see docs/PODCAST_DESIGN.md). Not wired into the
 * app's navigation graph or bottom nav yet; nav/multi-queue design is still open, so this is
 * reachable only for manual testing until that's settled.
 */
class PodcastsFragment : AbsMusicServiceFragment(R.layout.fragment_podcasts) {

    private var _binding: FragmentPodcastsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PodcastsViewModel by viewModel()

    private lateinit var podcastAdapter: PodcastAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    /** Set right before opening a queue on an episode; consumed once its metadata loads. */
    private var pendingResumeEpisodeId: Long? = null
    private var pendingResumePositionMs: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastsBinding.bind(view)

        val initialTopPadding = binding.root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val statusBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = initialTopPadding + statusBarInset)
            windowInsets
        }

        podcastAdapter = PodcastAdapter { viewModel.select(it) }
        binding.podcastsRecyclerView.adapter = podcastAdapter

        episodeAdapter = EpisodeAdapter(
            onPlay = { playEpisode(it) },
            onDownload = { viewModel.download(it) },
            onDeleteDownload = { viewModel.deleteDownload(it) }
        )
        binding.episodesRecyclerView.adapter = episodeAdapter

        binding.subscribeButton.setOnClickListener {
            val feedUrl = binding.feedUrlInput.text?.toString().orEmpty()
            if (feedUrl.isNotBlank()) {
                viewModel.subscribe(feedUrl)
                binding.feedUrlInput.text?.clear()
            }
        }

        viewModel.podcasts.observe(viewLifecycleOwner) { podcasts ->
            podcastAdapter.submitList(podcasts)
            if (viewModel.selectedPodcast.value == null) {
                podcasts.firstOrNull()?.let { viewModel.select(it) }
            }
        }

        viewModel.episodes.observe(viewLifecycleOwner) { episodeAdapter.submitList(it) }
        viewModel.downloadProgress.observe(viewLifecycleOwner) { episodeAdapter.updateProgress(it) }

        viewModel.subscribeError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                requireContext().showToast(error)
                viewModel.consumeSubscribeError()
            }
        }
    }

    private fun playEpisode(episode: EpisodeEntity) {
        val podcast = viewModel.selectedPodcast.value ?: return
        val episodes = viewModel.episodes.value.orEmpty()
        val startPosition = episodes.indexOf(episode).coerceAtLeast(0)
        val queue = episodes.map { it.toSong(podcast) }

        // openQueue is async (goes through the service binder), so we can't seek right after
        // calling it -- the new track isn't loaded yet. Instead, stash the resume target and
        // consume it in onPlayingMetaChanged() once the service reports the episode is current.
        if (episode.playbackPositionMs > 0) {
            pendingResumeEpisodeId = episode.id
            pendingResumePositionMs = episode.playbackPositionMs.toInt()
        }
        MusicPlayerRemote.openQueue(queue, startPosition, true)
    }

    override fun onPlayingMetaChanged() {
        val targetEpisodeId = pendingResumeEpisodeId ?: return
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == targetEpisodeId) {
            MusicPlayerRemote.seekTo(pendingResumePositionMs)
            pendingResumeEpisodeId = null
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconcileDownloads()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
