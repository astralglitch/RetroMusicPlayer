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
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import code.name.monkey.retromusic.EXTRA_PODCAST_ID
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.GridStyle
import code.name.monkey.retromusic.fragments.base.AbsRecyclerViewCustomGridSizeFragment
import code.name.monkey.retromusic.interfaces.IPodcastClickListener
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.RetroUtil
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Grid of subscribed podcasts -- the tab now mirrors Artists/Albums (grid size + grid style
 * controls in the overflow menu, tap to drill into a podcast's episode list) instead of a fixed
 * horizontal strip. Episode browsing moved to PodcastDetailsFragment.
 */
class PodcastsFragment :
    AbsRecyclerViewCustomGridSizeFragment<PodcastAdapter, GridLayoutManager>(),
    IPodcastClickListener {

    private val viewModel: PodcastsViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Repurpose the shared shuffle FAB (see AbsRecyclerViewFragment) as an "add podcast"
        // action -- podcasts have no shuffle concept, but reusing it inherits the FAB's
        // hide-on-scroll and mini-player-avoidance margin logic for free.
        shuffleButton.setImageResource(R.drawable.ic_add)
        shuffleButton.contentDescription = getString(R.string.podcast_subscribe)

        viewModel.podcasts.observe(viewLifecycleOwner) {
            adapter?.swapDataSet(it)
        }
        viewModel.subscribeError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                requireContext().showToast(error)
                viewModel.consumeSubscribeError()
            }
        }
    }

    override val titleRes: Int
        get() = R.string.podcast_subscriptions_tab

    override val emptyMessage: Int
        get() = R.string.empty

    override val isShuffleVisible: Boolean
        get() = true

    override fun onShuffleClicked() {
        showAddPodcastDialog()
    }

    private fun showAddPodcastDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.podcast_feed_url_hint)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_subscribe)
            .setView(container)
            .setPositiveButton(R.string.podcast_subscribe) { _, _ ->
                val feedUrl = input.text?.toString().orEmpty()
                if (feedUrl.isNotBlank()) {
                    viewModel.subscribe(feedUrl)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onPodcast(podcastId: Long, view: View) {
        findNavController().navigate(
            R.id.podcastDetailsFragment,
            bundleOf(EXTRA_PODCAST_ID to podcastId),
            null,
            FragmentNavigatorExtras(view to podcastId.toString())
        )
    }

    override fun createLayoutManager(): GridLayoutManager {
        return GridLayoutManager(requireActivity(), getGridSize())
    }

    override fun createAdapter(): PodcastAdapter {
        val dataSet = if (adapter == null) emptyList() else adapter!!.dataSet
        return PodcastAdapter(requireActivity(), dataSet, itemLayoutRes(), this)
    }

    override fun loadGridSize(): Int = PreferenceUtil.podcastGridSize

    override fun saveGridSize(gridColumns: Int) {
        PreferenceUtil.podcastGridSize = gridColumns
    }

    override fun loadGridSizeLand(): Int = PreferenceUtil.podcastGridSizeLand

    override fun saveGridSizeLand(gridColumns: Int) {
        PreferenceUtil.podcastGridSizeLand = gridColumns
    }

    override fun setGridSize(gridSize: Int) {
        layoutManager?.spanCount = gridSize
        adapter?.notifyDataSetChanged()
    }

    // Podcasts have only one meaningful order (subscription order) for now -- no sort-order
    // submenu is wired up in onCreateMenu(), so these just persist a fixed value.
    override fun loadSortOrder(): String = "default"

    override fun saveSortOrder(sortOrder: String) {}

    override fun setSortOrder(sortOrder: String) {}

    override fun loadLayoutRes(): Int = PreferenceUtil.podcastGridStyle.layoutResId

    override fun saveLayoutRes(layoutRes: Int) {
        PreferenceUtil.podcastGridStyle = GridStyle.values().first { it.layoutResId == layoutRes }
    }

    override fun onCreateMenu(menu: Menu, inflater: android.view.MenuInflater) {
        super.onCreateMenu(menu, inflater)
        menu.findItem(R.id.action_sort_order)?.isVisible = false
        menu.findItem(R.id.action_cast)?.isVisible = false
        val gridSizeItem = menu.findItem(R.id.action_grid_size)
        if (RetroUtil.isLandscape) {
            gridSizeItem.setTitle(R.string.action_grid_size_land)
        }
        setUpGridSizeMenu(gridSizeItem.subMenu!!)
        setupLayoutMenu(menu.findItem(R.id.action_layout_type).subMenu!!)
    }

    private fun setupLayoutMenu(subMenu: SubMenu) {
        when (itemLayoutRes()) {
            R.layout.item_card -> subMenu.findItem(R.id.action_layout_card).isChecked = true
            R.layout.item_grid -> subMenu.findItem(R.id.action_layout_normal).isChecked = true
            R.layout.item_card_color -> subMenu.findItem(R.id.action_layout_colored_card).isChecked =
                true

            R.layout.item_grid_circle -> subMenu.findItem(R.id.action_layout_circular).isChecked =
                true

            R.layout.image -> subMenu.findItem(R.id.action_layout_image).isChecked = true
            R.layout.item_image_gradient -> subMenu.findItem(R.id.action_layout_gradient_image).isChecked =
                true
        }
    }

    private fun setUpGridSizeMenu(gridSizeMenu: SubMenu) {
        when (getGridSize()) {
            1 -> gridSizeMenu.findItem(R.id.action_grid_size_1).isChecked = true
            2 -> gridSizeMenu.findItem(R.id.action_grid_size_2).isChecked = true
            3 -> gridSizeMenu.findItem(R.id.action_grid_size_3).isChecked = true
            4 -> gridSizeMenu.findItem(R.id.action_grid_size_4).isChecked = true
            5 -> gridSizeMenu.findItem(R.id.action_grid_size_5).isChecked = true
            6 -> gridSizeMenu.findItem(R.id.action_grid_size_6).isChecked = true
            7 -> gridSizeMenu.findItem(R.id.action_grid_size_7).isChecked = true
            8 -> gridSizeMenu.findItem(R.id.action_grid_size_8).isChecked = true
        }
        val gridSize: Int = maxGridSize
        if (gridSize < 8) gridSizeMenu.findItem(R.id.action_grid_size_8).isVisible = false
        if (gridSize < 7) gridSizeMenu.findItem(R.id.action_grid_size_7).isVisible = false
        if (gridSize < 6) gridSizeMenu.findItem(R.id.action_grid_size_6).isVisible = false
        if (gridSize < 5) gridSizeMenu.findItem(R.id.action_grid_size_5).isVisible = false
        if (gridSize < 4) gridSizeMenu.findItem(R.id.action_grid_size_4).isVisible = false
        if (gridSize < 3) gridSizeMenu.findItem(R.id.action_grid_size_3).isVisible = false
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (handleGridSizeMenuItem(item)) return true
        if (handleLayoutResType(item)) return true
        return super.onMenuItemSelected(item)
    }

    private fun handleLayoutResType(item: MenuItem): Boolean {
        val layoutRes = when (item.itemId) {
            R.id.action_layout_normal -> R.layout.item_grid
            R.id.action_layout_card -> R.layout.item_card
            R.id.action_layout_colored_card -> R.layout.item_card_color
            R.id.action_layout_circular -> R.layout.item_grid_circle
            R.id.action_layout_image -> R.layout.image
            R.id.action_layout_gradient_image -> R.layout.item_image_gradient
            else -> PreferenceUtil.podcastGridStyle.layoutResId
        }
        if (layoutRes != PreferenceUtil.podcastGridStyle.layoutResId) {
            item.isChecked = true
            setAndSaveLayoutRes(layoutRes)
            return true
        }
        return false
    }

    private fun handleGridSizeMenuItem(item: MenuItem): Boolean {
        val gridSize = when (item.itemId) {
            R.id.action_grid_size_1 -> 1
            R.id.action_grid_size_2 -> 2
            R.id.action_grid_size_3 -> 3
            R.id.action_grid_size_4 -> 4
            R.id.action_grid_size_5 -> 5
            R.id.action_grid_size_6 -> 6
            R.id.action_grid_size_7 -> 7
            R.id.action_grid_size_8 -> 8
            else -> 0
        }
        if (gridSize > 0) {
            item.isChecked = true
            setAndSaveGridSize(gridSize)
            return true
        }
        return false
    }
}
