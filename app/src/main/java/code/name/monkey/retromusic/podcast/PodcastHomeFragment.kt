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
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastHomeBinding
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.extensions.elevatedAccentColor
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.profileBannerOptions
import code.name.monkey.retromusic.glide.RetroGlideExtension.userProfileOptions
import code.name.monkey.retromusic.util.PreferenceUtil.userName
import com.bumptech.glide.Glide
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Podcasts-world Home tab -- the podcast equivalent of the Music world's HomeFragment: the same
 * welcome header, a 4-button action grid (History/Downloads/Favorites/Statistics in place of
 * Music's History/Last added/Most played/Shuffle), then a stack of sections built from podcast
 * concepts (Suggestions/Top Subscriptions/Favorites/Inbox/Continue Listening) instead of
 * albums/artists/playlists. See PodcastHomeViewModel/PodcastHomeAdapter.
 */
class PodcastHomeFragment : AbsMainActivityFragment(R.layout.fragment_podcast_home) {

    private var _binding: FragmentPodcastHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModel<PodcastHomeViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastHomeBinding.bind(view)
        mainActivity.setSupportActionBar(binding.appBarLayout.toolbar)
        binding.appBarLayout.title = getString(R.string.podcast_tab_home)
        binding.appBarLayout.toolbar.setNavigationOnClickListener {
            mainActivity.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.imageLayout.titleWelcome.text = String.format("%s", userName)
        loadProfile()
        setupActionButtons()

        val adapter = PodcastHomeAdapter(
            activity = mainActivity,
            onPlayEpisode = ::openEpisode,
            onToggleFavorited = { episode, favorited -> viewModel.setEpisodeFavorited(episode, favorited) },
            onSeeAllEpisodes = ::openEpisodeSection,
            onPodcast = { podcastId, _ -> openPodcast(podcastId) },
            onSeeAllPodcasts = ::openSubscriptions
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(mainActivity)
            this.adapter = adapter
        }

        viewModel.podcasts.observe(viewLifecycleOwner) { podcasts ->
            adapter.swapData(viewModel.homeSections.value.orEmpty(), podcasts.associateBy { it.id })
        }
        viewModel.homeSections.observe(viewLifecycleOwner) { sections ->
            adapter.swapData(sections, viewModel.podcasts.value.orEmpty().associateBy { it.id })
            binding.emptyText.isVisible = sections.isEmpty()
        }
    }

    private fun loadProfile() {
        binding.imageLayout.bannerImage?.let {
            Glide.with(requireContext())
                .load(RetroGlideExtension.getBannerModel())
                .profileBannerOptions(RetroGlideExtension.getBannerModel())
                .into(it)
        }
        Glide.with(requireActivity())
            .load(RetroGlideExtension.getUserModel())
            .userProfileOptions(RetroGlideExtension.getUserModel(), requireContext())
            .into(binding.imageLayout.userImage)
    }

    private fun setupActionButtons() {
        val buttons = binding.actions
        buttons.podcastHistory.elevatedAccentColor()
        buttons.podcastDownloads.elevatedAccentColor()
        buttons.podcastFavorites.elevatedAccentColor()
        buttons.podcastStatistics.elevatedAccentColor()
        buttons.podcastHistory.setOnClickListener {
            findNavController().navigate(R.id.podcasts_history_fragment)
        }
        buttons.podcastDownloads.setOnClickListener {
            findNavController().navigate(R.id.podcasts_downloads_fragment)
        }
        buttons.podcastFavorites.setOnClickListener {
            findNavController().navigate(R.id.podcasts_favorites_fragment)
        }
        buttons.podcastStatistics.setOnClickListener {
            findNavController().navigate(R.id.podcasts_statistics_fragment)
        }
    }

    private fun openEpisode(episode: EpisodeEntity) {
        findNavController().navigate(
            R.id.episodeDetailsFragment,
            bundleOf("extra_episode_id" to episode.id)
        )
    }

    private fun openPodcast(podcastId: Long) {
        findNavController().navigate(
            R.id.podcastDetailsFragment,
            bundleOf("extra_podcast_id" to podcastId)
        )
    }

    private fun openEpisodeSection(@PodcastHomeSection section: Int) {
        val destination = when (section) {
            PODCAST_FAVORITES -> R.id.podcasts_favorites_fragment
            PODCAST_INBOX -> R.id.podcasts_inbox_fragment
            // Multi-queue isn't built yet (see docs/PODCAST_DESIGN.md) -- Continue Listening's
            // "see all" still only has the placeholder Queue tab to land on.
            PODCAST_CONTINUE_LISTENING -> R.id.podcasts_queue_fragment
            else -> R.id.podcasts_episodes_fragment
        }
        findNavController().navigate(destination)
    }

    private fun openSubscriptions() {
        findNavController().navigate(R.id.podcasts_fragment)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem) = false

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
