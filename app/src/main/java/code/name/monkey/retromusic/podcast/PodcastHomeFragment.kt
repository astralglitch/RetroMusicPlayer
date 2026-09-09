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
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Podcasts-world Home tab -- the podcast equivalent of the Music world's HomeFragment: a stack
 * of "different sections" (Continue Listening / New Episodes / Your Podcasts) built from
 * podcast concepts instead of albums/artists. See PodcastHomeViewModel/PodcastHomeAdapter.
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

        val adapter = PodcastHomeAdapter(
            activity = mainActivity,
            onPlayEpisode = ::openEpisode,
            onSeeAllEpisodes = ::openEpisodesTab,
            onPodcast = { podcastId, _ -> openPodcast(podcastId) },
            onSeeAllPodcasts = ::openSubscriptions
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(mainActivity)
            this.adapter = adapter
        }

        viewModel.homeSections.observe(viewLifecycleOwner) { sections ->
            adapter.swapData(sections)
            binding.emptyText.isVisible = sections.isEmpty()
        }
    }

    private fun openEpisode(episode: EpisodeEntity) = openPodcast(episode.podcastId)

    private fun openPodcast(podcastId: Long) {
        findNavController().navigate(
            R.id.podcastDetailsFragment,
            bundleOf("extra_podcast_id" to podcastId)
        )
    }

    private fun openEpisodesTab() {
        findNavController().navigate(R.id.podcasts_episodes_fragment)
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
