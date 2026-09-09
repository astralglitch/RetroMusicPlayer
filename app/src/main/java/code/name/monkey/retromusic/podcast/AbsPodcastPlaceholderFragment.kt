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
 * Stands in for a Podcasts-world bottom-nav tab that doesn't have a real screen yet -- currently
 * just Queue (needs the not-yet-built multi-queue feature, see docs/PODCAST_DESIGN.md) and
 * Statistics (needs a real listening-stats design). See PodcastCategoryInfo.Category. Home,
 * Favorites, Downloads, History, Inbox and Episodes have all since moved to real screens
 * (PodcastHomeFragment / AbsEpisodeListFragment).
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

class PodcastQueueFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_queue)
class PodcastStatisticsFragment : AbsPodcastPlaceholderFragment(R.string.podcast_tab_statistics)
