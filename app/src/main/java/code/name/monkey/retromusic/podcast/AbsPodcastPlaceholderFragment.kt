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
import androidx.core.view.GravityCompat
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastPlaceholderBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment

/**
 * Stands in for a Podcasts-world bottom-nav tab that doesn't have a real screen yet (Queue,
 * Inbox, Episodes, Downloads, History, Favorites, Statistics, and a Podcasts-world Home) --
 * see PodcastCategoryInfo.Category and the podcast nav redesign notes. Each such tab is a
 * one-line subclass here rather than a real destination until it's built.
 */
abstract class AbsPodcastPlaceholderFragment(@StringRes private val titleRes: Int) :
    AbsMainActivityFragment(R.layout.fragment_podcast_placeholder) {

    private var _binding: FragmentPodcastPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastPlaceholderBinding.bind(view)
        mainActivity.setSupportActionBar(binding.appBarLayout.toolbar)
        binding.appBarLayout.toolbar.setNavigationOnClickListener {
            mainActivity.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.appBarLayout.title = getString(titleRes)
        binding.placeholderText.text = getString(R.string.podcast_tab_coming_soon)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem) = false

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PodcastsHomeFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_home)
class PodcastQueueFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_queue)
class PodcastInboxFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_inbox)
class PodcastEpisodesFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_episodes)
class PodcastDownloadsFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_downloads)
class PodcastHistoryFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_history)
class PodcastFavoritesFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_favorites)
class PodcastStatisticsFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_statistics)
