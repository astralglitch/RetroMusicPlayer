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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentEpisodeListBinding
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * A plain "every episode matching some filter, across all subscriptions" screen -- one shared
 * class backs the Podcasts-world Favorites/Downloads/History/Inbox/Episodes tabs, which differ
 * only in which [EpisodeListKind] they query (see EpisodeListViewModel) and their title/empty
 * message. Mirrors how AbsPodcastPlaceholderFragment shared one class for these before they had
 * real screens.
 */
abstract class AbsEpisodeListFragment(
    @StringRes private val titleRes: Int,
    @StringRes private val emptyTextRes: Int,
    private val kind: EpisodeListKind
) : AbsMainActivityFragment(R.layout.fragment_episode_list) {

    private var _binding: FragmentEpisodeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModel<EpisodeListViewModel> { parametersOf(kind) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEpisodeListBinding.bind(view)
        mainActivity.setSupportActionBar(binding.appBarLayout.toolbar)
        binding.appBarLayout.title = getString(titleRes)
        binding.appBarLayout.toolbar.setNavigationOnClickListener {
            mainActivity.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.emptyText.setText(emptyTextRes)
        binding.recyclerView.layoutManager = LinearLayoutManager(mainActivity)

        viewModel.episodes.observe(viewLifecycleOwner) { updateAdapter() }
        viewModel.podcasts.observe(viewLifecycleOwner) { updateAdapter() }
    }

    /** Rebuilds the adapter whenever either the episode list or the podcasts lookup (used for
     * each row's thumbnail) changes -- lists here are short enough that this is cheaper than
     * plumbing a separate "update thumbnails in place" path. */
    private fun updateAdapter() {
        val episodes = viewModel.episodes.value.orEmpty()
        val podcastsById = viewModel.podcasts.value.orEmpty().associateBy { it.id }
        binding.recyclerView.adapter = EpisodeAdapter(
            onPlay = ::openEpisode,
            onDownload = { viewModel.download(it) },
            onDeleteDownload = { viewModel.deleteDownload(it) },
            onTogglePlayed = { episode, played -> viewModel.setPlayed(episode, played) },
            onToggleFavorited = { episode, favorited -> viewModel.setFavorited(episode, favorited) },
            onStream = ::streamEpisode,
            podcastsById = podcastsById
        ).apply { submitList(episodes) }
        binding.emptyText.isVisible = episodes.isEmpty()
    }

    private fun openEpisode(episode: EpisodeEntity) {
        findNavController().navigate(
            R.id.episodeDetailsFragment,
            bundleOf("extra_episode_id" to episode.id)
        )
    }

    /** The long-press "Stream episode" escape hatch -- plays straight from the enclosure URL
     * without downloading first, bypassing PreferenceUtil.preferStreaming's offline-first
     * default that otherwise hides Play until an episode is downloaded. */
    private fun streamEpisode(episode: EpisodeEntity) {
        val podcast = viewModel.podcasts.value?.firstOrNull { it.id == episode.podcastId } ?: return
        MusicPlayerRemote.openQueue(listOf(episode.toSong(podcast)), 0, true)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem) = false

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PodcastFavoritesFragment : AbsEpisodeListFragment(
    R.string.podcast_tab_favorites, R.string.podcast_favorites_empty, EpisodeListKind.FAVORITES
)

class PodcastDownloadsFragment : AbsEpisodeListFragment(
    R.string.podcast_tab_downloads, R.string.podcast_downloads_empty, EpisodeListKind.DOWNLOADS
)

class PodcastHistoryFragment : AbsEpisodeListFragment(
    R.string.podcast_tab_history, R.string.podcast_history_empty, EpisodeListKind.HISTORY
)

class PodcastInboxFragment : AbsEpisodeListFragment(
    R.string.podcast_tab_inbox, R.string.podcast_inbox_empty, EpisodeListKind.INBOX
)

class PodcastEpisodesFragment : AbsEpisodeListFragment(
    R.string.podcast_tab_episodes, R.string.podcast_episodes_empty, EpisodeListKind.ALL
)
