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
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentEpisodeListBinding
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The Podcasts-world Queue tab -- see the "Multi-queue" section of docs/PODCAST_DESIGN.md. Shows
 * one queue (the active one) at a time; drag a row to reorder, tap to start playing the queue
 * from that episode, long-press for the usual episode actions plus "Remove from queue". The
 * overflow menu switches/creates/renames/deletes queues.
 */
class QueueFragment : AbsMainActivityFragment(R.layout.fragment_episode_list) {

    private var _binding: FragmentEpisodeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModel<QueueViewModel>()
    private val podcastRepository by inject<PodcastRepository>()

    /** The adapter's own working copy, mutated live during a drag before being committed to the
     * ViewModel on drop -- submitList()-ing a reordered list is the normal way to animate a
     * ListAdapter reorder. */
    private var workingEpisodes: MutableList<EpisodeEntity> = mutableListOf()
    private lateinit var adapter: EpisodeAdapter
    private val resumeSeeker = EpisodeResumeSeeker()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEpisodeListBinding.bind(view)
        mainActivity.setSupportActionBar(binding.appBarLayout.toolbar)
        binding.appBarLayout.toolbar.setNavigationOnClickListener {
            mainActivity.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.emptyText.setText(R.string.podcast_queue_empty)

        adapter = EpisodeAdapter(
            onPlay = { playFrom(it) },
            onPlayPauseButton = { onEpisodePlayPauseButton(it) },
            onDownload = { playFrom(it) },
            onDeleteDownload = { playFrom(it) },
            onTogglePlayed = { episode, played ->
                viewLifecycleOwner.lifecycleScope.launch { podcastRepository.setEpisodePlayed(episode.id, played) }
            },
            onToggleFavorited = { episode, favorited ->
                viewLifecycleOwner.lifecycleScope.launch { podcastRepository.setEpisodeFavorited(episode.id, favorited) }
            },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) }
        )
        adapter.setPlaybackState(MusicPlayerRemote.currentSong.episodeIdOrNull(), MusicPlayerRemote.isPlaying)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(mainActivity)
            adapter = this@QueueFragment.adapter
        }
        attachDragToReorder()

        viewModel.activeQueue.observe(viewLifecycleOwner) { queue ->
            binding.appBarLayout.title = queue?.name ?: getString(R.string.podcast_tab_queue)
        }
        viewModel.episodes.observe(viewLifecycleOwner) { episodes ->
            workingEpisodes = episodes.toMutableList()
            adapter.submitList(episodes)
            binding.emptyText.isVisible = episodes.isEmpty()
        }
    }

    private fun attachDragToReorder() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0 || from >= workingEpisodes.size || to >= workingEpisodes.size) return false
                workingEpisodes.add(to, workingEpisodes.removeAt(from))
                adapter.submitList(workingEpisodes.toList())
                return true
            }

            // Swipe in either direction removes the row from this queue -- the resulting
            // viewModel.episodes update (submitList) handles the row's removal animation, so no
            // manual notifyItemChanged/undo snap-back is needed here (unlike SwipeActionHelper's
            // toggle-style actions elsewhere, which do restore the row).
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position < 0 || position >= workingEpisodes.size) return
                viewModel.removeFromQueue(workingEpisodes[position])
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewModel.reorder(workingEpisodes.map { it.id })
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView)
    }

    /** Builds the Song queue from the episodes currently shown (fetching each one's podcast for
     * `toSong()`) and starts playback at [target] -- tapping a queue row plays sequentially from
     * there, since that's the entire point of a queue. */
    private fun playFrom(target: EpisodeEntity) {
        val episodes = workingEpisodes.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            val podcastCache = mutableMapOf<Long, code.name.monkey.retromusic.db.PodcastEntity?>()
            val songs = episodes.mapNotNull { episode ->
                val podcast = podcastCache.getOrPut(episode.podcastId) {
                    podcastRepository.podcastById(episode.podcastId)
                }
                podcast?.let { episode.toSong(it) }
            }
            val startIndex = episodes.indexOf(target).coerceAtLeast(0)
            if (songs.isNotEmpty()) {
                resumeSeeker.arm(target)
                MusicPlayerRemote.openQueue(songs, startIndex, true)
                podcastCache[target.podcastId]?.let {
                    viewLifecycleOwner.lifecycleScope.activateVideoIfPreferred(target, it)
                }
            }
        }
    }

    private fun onEpisodePlayPauseButton(episode: EpisodeEntity) {
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == episode.id) {
            if (MusicPlayerRemote.isPlaying) MusicPlayerRemote.pauseSong() else MusicPlayerRemote.resumePlaying()
        } else {
            playFrom(episode)
        }
    }

    override fun onPlayingMetaChanged() {
        adapter.setPlaybackState(MusicPlayerRemote.currentSong.episodeIdOrNull(), MusicPlayerRemote.isPlaying)
        resumeSeeker.onPlayingMetaChanged()
    }

    override fun onPlayStateChanged() {
        adapter.setPlaybackState(MusicPlayerRemote.currentSong.episodeIdOrNull(), MusicPlayerRemote.isPlaying)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_queue, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        menu.findItem(R.id.action_delete_queue)?.isVisible = viewModel.activeQueue.value?.isDefault == false
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_switch_queue -> showSwitchQueueDialog()
            R.id.action_new_queue -> showNewQueueDialog()
            R.id.action_rename_queue -> showRenameQueueDialog()
            R.id.action_delete_queue -> confirmDeleteQueue()
            else -> return false
        }
        return true
    }

    private fun showSwitchQueueDialog() {
        val queues = viewModel.queues.value.orEmpty()
        if (queues.isEmpty()) return
        val names = queues.map { it.name }.toTypedArray()
        val currentIndex = queues.indexOfFirst { it.id == viewModel.activeQueueId.value }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_switch_queue)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                viewModel.switchTo(queues[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewQueueDialog() {
        val input = EditText(requireContext()).apply { hint = getString(R.string.podcast_queue_name_hint) }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_new_queue)
            .setView(container)
            .setPositiveButton(R.string.podcast_new_queue) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.createQueue(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameQueueDialog() {
        val queue = viewModel.activeQueue.value ?: return
        val input = EditText(requireContext()).apply { setText(queue.name) }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_rename_queue)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) viewModel.renameQueue(queue, name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteQueue() {
        val queue = viewModel.activeQueue.value ?: return
        if (queue.isDefault) return
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.podcast_delete_queue_confirm, queue.name))
            .setPositiveButton(R.string.podcast_delete_queue) { _, _ -> viewModel.deleteQueue(queue) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
