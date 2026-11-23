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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastStatisticsBinding
import code.name.monkey.retromusic.databinding.ItemPodcastStatRowBinding
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class PodcastStatisticsFragment : AbsMainActivityFragment(R.layout.fragment_podcast_statistics) {

    private var _binding: FragmentPodcastStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModel<PodcastStatisticsViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastStatisticsBinding.bind(view)
        mainActivity.setSupportActionBar(binding.appBarLayout.toolbar)
        binding.appBarLayout.toolbar.setNavigationOnClickListener {
            mainActivity.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.appBarLayout.title = getString(R.string.podcast_tab_statistics)

        viewModel.statistics.observe(viewLifecycleOwner, ::render)
    }

    private fun render(statistics: PodcastStatistics) {
        val isEmpty = statistics.subscriptionCount == 0
        binding.emptyText.isVisible = isEmpty
        binding.statisticsContent.isVisible = !isEmpty
        if (isEmpty) return

        binding.listeningTime.text = formatDuration(statistics.listenedMs)

        binding.summaryRows.removeAllViews()
        listOf(
            R.string.podcast_subscriptions_tab to statistics.subscriptionCount,
            R.string.podcast_statistics_played to statistics.playedCount,
            R.string.podcast_statistics_in_progress to statistics.inProgressCount,
            R.string.podcast_tab_favorites to statistics.favoriteCount,
            R.string.podcast_tab_downloads to statistics.downloadedCount
        ).forEach { (labelRes, count) ->
            addRow(binding.summaryRows, getString(labelRes), count.toString())
        }

        binding.topShowsHeader.isVisible = statistics.topShows.isNotEmpty()
        binding.topShowsRows.removeAllViews()
        statistics.topShows.forEach { show ->
            addRow(
                binding.topShowsRows,
                show.title,
                getString(
                    R.string.podcast_statistics_show_summary,
                    resources.getQuantityString(
                        R.plurals.podcast_statistics_played_episodes,
                        show.playedCount,
                        show.playedCount
                    ),
                    formatDuration(show.listenedMs)
                )
            )
        }
    }

    private fun addRow(parent: LinearLayout, label: String, value: String) {
        val row = ItemPodcastStatRowBinding.inflate(LayoutInflater.from(parent.context), parent, true)
        row.label.text = label
        row.value.text = value
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            getString(R.string.podcast_statistics_hours_minutes, hours, minutes)
        } else {
            getString(R.string.podcast_statistics_minutes, minutes)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflatePodcastWorldActions(menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem) = onPodcastWorldActionSelected(menuItem)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
