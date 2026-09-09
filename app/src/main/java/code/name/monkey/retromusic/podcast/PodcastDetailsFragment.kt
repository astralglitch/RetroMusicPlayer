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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastDetailsBinding
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import com.bumptech.glide.Glide
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * A single podcast's info (art/title/description) header plus its episode list below --
 * mirrors AlbumDetailsFragment's layout pattern. Reached from the Podcasts grid tab
 * (PodcastsFragment) instead of the old inline episode list under a horizontal podcast strip.
 */
class PodcastDetailsFragment : AbsMainActivityFragment(R.layout.fragment_podcast_details) {

    private var _binding: FragmentPodcastDetailsBinding? = null
    private val binding get() = _binding!!

    private val arguments by navArgs<PodcastDetailsFragmentArgs>()
    private val viewModel by viewModel<PodcastDetailsViewModel> {
        parametersOf(arguments.extraPodcastId)
    }

    private lateinit var episodeAdapter: EpisodeAdapter

    /** Set right before opening a queue on an episode; consumed once its metadata loads. */
    private var pendingResumeEpisodeId: Long? = null
    private var pendingResumePositionMs: Int = 0

    private enum class EpisodeFilter { ALL, DOWNLOADED, IN_PROGRESS }

    // Session-only view state -- not persisted, since what belongs here long-term (a proper
    // filter/sort design) is still open per the podcast-nav redesign notes.
    private var sortNewestFirst = true
    private var filter = EpisodeFilter.ALL
    private var allEpisodes: List<EpisodeEntity> = emptyList()

    /** What's actually bound to the adapter right now (post filter/sort) -- playEpisode() needs
     * this, not allEpisodes, so the queue position matches what the user tapped. */
    private var displayedEpisodes: List<EpisodeEntity> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastDetailsBinding.bind(view)
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = " "
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.podcastCoverContainer.transitionName = arguments.extraPodcastId.toString()

        episodeAdapter = EpisodeAdapter(
            onPlay = { playEpisode(it) },
            onDownload = { viewModel.download(it) },
            onDeleteDownload = { viewModel.deleteDownload(it) },
            onTogglePlayed = { episode, played -> viewModel.setPlayed(episode, played) },
            onToggleFavorited = { episode, favorited -> viewModel.setFavorited(episode, favorited) }
        )
        binding.episodesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            adapter = episodeAdapter
        }
        episodeAdapter.setCurrentEpisodeId(MusicPlayerRemote.currentSong.episodeIdOrNull())

        binding.resumeAction.setOnClickListener { resumePlayback() }

        viewModel.podcast.observe(viewLifecycleOwner) { podcast ->
            if (podcast != null) showPodcast(podcast)
        }
        viewModel.episodes.observe(viewLifecycleOwner) {
            allEpisodes = it
            applyEpisodeListChanges()
            viewModel.reconcileDownloads()
        }
        viewModel.downloadProgress.observe(viewLifecycleOwner) { episodeAdapter.updateProgress(it) }
    }

    private fun applyEpisodeListChanges() {
        var episodes = allEpisodes
        episodes = when (filter) {
            EpisodeFilter.ALL -> episodes
            EpisodeFilter.DOWNLOADED -> episodes.filter { it.downloadState == EpisodeDownloadState.DOWNLOADED }
            EpisodeFilter.IN_PROGRESS -> episodes.filter { !it.played && it.playbackPositionMs > 0 }
        }
        episodes = if (sortNewestFirst) {
            episodes.sortedByDescending { it.pubDate }
        } else {
            episodes.sortedBy { it.pubDate }
        }
        displayedEpisodes = episodes
        episodeAdapter.submitList(episodes)
    }

    /** Resumes the most recently in-progress episode, or starts from the newest episode if
     * nothing's in progress -- the podcast-detail equivalent of AlbumDetailsFragment's "Play
     * all", since a fixed play-from-top doesn't make sense once you're partway through a feed. */
    private fun resumePlayback() {
        if (allEpisodes.isEmpty()) return
        val inProgress = allEpisodes
            .filter { !it.played && it.playbackPositionMs > 0 }
            .maxByOrNull { it.pubDate }
        val target = inProgress ?: allEpisodes.maxByOrNull { it.pubDate } ?: return
        playEpisode(target)
    }

    private fun showPodcast(podcast: PodcastEntity) {
        binding.podcastTitle.text = podcast.title
        binding.podcastDescription.text = podcast.description?.parseAsHtml()?.toString()?.trim()
        binding.podcastDescription.isVisible = !podcast.description.isNullOrBlank()
        Glide.with(this)
            .load(podcast.imageUrl)
            .placeholder(R.drawable.default_audio_art)
            .error(R.drawable.default_audio_art)
            .into(binding.podcastCover)
    }

    private fun playEpisode(episode: EpisodeEntity) {
        val podcast = viewModel.podcast.value ?: return
        val episodes = displayedEpisodes
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

    override fun onResume() {
        super.onResume()
        viewModel.reconcileDownloads()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_podcast_detail, menu)
        val sortId = if (sortNewestFirst) R.id.action_podcast_sort_newest else R.id.action_podcast_sort_oldest
        menu.findItem(sortId).isChecked = true
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_podcast_filter -> {
                showFilterDialog()
                return true
            }

            R.id.action_podcast_sort_newest -> {
                sortNewestFirst = true
                menuItem.isChecked = true
                applyEpisodeListChanges()
                return true
            }

            R.id.action_podcast_sort_oldest -> {
                sortNewestFirst = false
                menuItem.isChecked = true
                applyEpisodeListChanges()
                return true
            }

            R.id.action_podcast_refresh -> {
                viewModel.podcast.value?.let {
                    viewModel.refresh()
                    requireContext().showToast(getString(R.string.podcast_refreshing))
                }
                return true
            }

            R.id.action_podcast_unsubscribe -> {
                confirmUnsubscribe()
                return true
            }
        }
        return false
    }

    private fun showFilterDialog() {
        val labels = arrayOf(
            getString(R.string.podcast_filter_all),
            getString(R.string.podcast_filter_downloaded),
            getString(R.string.podcast_filter_in_progress)
        )
        val options = EpisodeFilter.values()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_filter)
            .setSingleChoiceItems(labels, options.indexOf(filter)) { dialog, which ->
                filter = options[which]
                applyEpisodeListChanges()
                dialog.dismiss()
            }
            .show()
    }

    private fun confirmUnsubscribe() {
        val podcast = viewModel.podcast.value ?: return
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.podcast_unsubscribe_confirm, podcast.title))
            .setPositiveButton(R.string.podcast_unsubscribe) { _, _ ->
                viewModel.unsubscribe { findNavController().navigateUp() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
