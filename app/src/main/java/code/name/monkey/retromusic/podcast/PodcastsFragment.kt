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

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastsBinding
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Bare-bones subscribe / list episodes / play / download screen — the "get the basics working"
 * milestone from the podcast build-order notes (see docs/PODCAST_DESIGN.md). Reachable as a
 * bottom-nav category (CategoryInfo.Category.Podcasts) alongside Songs/Albums/etc.; the
 * Subscriptions/Queue/Downloads-as-separate-tabs design is still open, so everything here lives
 * in one screen for now.
 */
class PodcastsFragment : AbsMainActivityFragment(R.layout.fragment_podcasts) {

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

        // Reached as a plain main_graph destination (like HomeFragment/SongsFragment), each of
        // which draws its own app bar under the status bar -- this one doesn't have one yet, so
        // pad manually rather than let the subscribe row sit under the status bar.
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
        episodeAdapter.setCurrentEpisodeId(MusicPlayerRemote.currentSong.episodeIdOrNull())

        binding.addPodcastFab.setOnClickListener { showAddPodcastDialog() }

        libraryViewModel.getFabMargin().observe(viewLifecycleOwner) { margin ->
            binding.addPodcastFab.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = margin
            }
        }

        viewModel.podcasts.observe(viewLifecycleOwner) { podcasts ->
            podcastAdapter.submitList(podcasts)
            if (viewModel.selectedPodcast.value == null) {
                podcasts.firstOrNull()?.let { viewModel.select(it) }
            }
        }

        viewModel.episodes.observe(viewLifecycleOwner) {
            episodeAdapter.submitList(it)
            // Also (not only) trigger here, not just from onResume(): on a freshly created
            // fragment, onResume() runs before this list has loaded from the DB, so an
            // onResume()-only trigger silently reconciles against an empty list and never
            // gets another chance until the *next* resume.
            viewModel.reconcileDownloads()
        }
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
        episodeAdapter.setCurrentEpisodeId(MusicPlayerRemote.currentSong.episodeIdOrNull())
        val targetEpisodeId = pendingResumeEpisodeId ?: return
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == targetEpisodeId) {
            MusicPlayerRemote.seekTo(pendingResumePositionMs)
            pendingResumeEpisodeId = null
        }
    }

    private fun showAddPodcastDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.podcast_feed_url_hint)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_subscribe)
            .setView(container)
            .setPositiveButton(R.string.podcast_subscribe) { _, _ ->
                val feedUrl = input.text?.toString().orEmpty()
                if (feedUrl.isNotBlank()) viewModel.subscribe(feedUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {}

    override fun onMenuItemSelected(menuItem: android.view.MenuItem) = false

    override fun onResume() {
        super.onResume()
        viewModel.reconcileDownloads()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
