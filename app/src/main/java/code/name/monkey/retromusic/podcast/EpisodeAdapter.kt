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

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.ItemEpisodeBinding
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.extensions.accentColor
import java.text.DateFormat
import java.util.Date

class EpisodeAdapter(
    private val onPlay: (EpisodeEntity) -> Unit,
    private val onDownload: (EpisodeEntity) -> Unit,
    private val onDeleteDownload: (EpisodeEntity) -> Unit
) : ListAdapter<EpisodeEntity, EpisodeAdapter.ViewHolder>(DIFF) {

    /** episode id -> 0-100, set by PodcastsFragment as PodcastsViewModel.downloadProgress ticks. */
    private var progressByEpisodeId: Map<Long, Int> = emptyMap()

    /** The episode currently loaded in the player, if any -- drives the "now playing" highlight,
     * mirroring how SongAdapter compares against MusicPlayerRemote.currentSong. */
    private var currentEpisodeId: Long? = null

    fun updateProgress(progress: Map<Long, Int>) {
        progressByEpisodeId = progress
        progress.keys.forEach { episodeId ->
            val position = currentList.indexOfFirst { it.id == episodeId }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    fun setCurrentEpisodeId(episodeId: Long?) {
        if (currentEpisodeId == episodeId) return
        val previousId = currentEpisodeId
        currentEpisodeId = episodeId
        listOf(previousId, episodeId).forEach { id ->
            val position = currentList.indexOfFirst { it.id == id }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemEpisodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                onPlay(getItem(layoutPosition))
            }
            binding.menu.setOnClickListener { showMenu(it) }
        }

        private fun showMenu(anchor: android.view.View) {
            val episode = getItem(layoutPosition)
            val popupMenu = PopupMenu(anchor.context, anchor)
            popupMenu.inflate(R.menu.menu_item_episode)
            val isDownloaded = episode.downloadState == EpisodeDownloadState.DOWNLOADED
            val isDownloading = episode.downloadState == EpisodeDownloadState.DOWNLOADING
            popupMenu.menu.findItem(R.id.action_episode_download).isVisible = !isDownloaded && !isDownloading
            popupMenu.menu.findItem(R.id.action_episode_delete_download).isVisible = isDownloaded || isDownloading
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_play -> onPlay(episode)
                    R.id.action_episode_download -> onDownload(episode)
                    R.id.action_episode_delete_download -> onDeleteDownload(episode)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            popupMenu.show()
        }

        fun bind(episode: EpisodeEntity) {
            binding.episodeTitle.text = episode.title
            val date = if (episode.pubDate > 0) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(episode.pubDate))
            } else {
                ""
            }
            val downloadLabel = when (episode.downloadState) {
                EpisodeDownloadState.DOWNLOADED -> "Downloaded"
                EpisodeDownloadState.DOWNLOADING -> {
                    val percent = progressByEpisodeId[episode.id]
                    if (percent != null) "Downloading… $percent%" else "Downloading…"
                }

                EpisodeDownloadState.FAILED -> "Download failed"
                EpisodeDownloadState.NOT_DOWNLOADED -> null
            }
            val playedLabel = playedLabel(episode)
            binding.episodeMeta.text = listOfNotNull(date.ifBlank { null }, playedLabel, downloadLabel)
                .joinToString(" · ")

            if (playedLabel == "In progress") {
                binding.episodeProgress.isVisible = true
                binding.episodeProgress.progress =
                    (episode.playbackPositionMs * 100 / episode.durationMs).toInt().coerceIn(0, 100)
            } else {
                binding.episodeProgress.isVisible = false
            }

            val isDownloading = episode.downloadState == EpisodeDownloadState.DOWNLOADING
            val isDownloaded = episode.downloadState == EpisodeDownloadState.DOWNLOADED
            // A stuck-looking transfer (dead/starved connection) needs a way out -- the button
            // stays enabled during DOWNLOADING too, as a cancel action, rather than being inert.
            binding.downloadButton.isEnabled = true
            binding.downloadButton.alpha = 1f
            binding.downloadButton.setImageResource(
                when {
                    isDownloaded -> R.drawable.ic_delete
                    isDownloading -> R.drawable.ic_close
                    else -> R.drawable.ic_download
                }
            )
            binding.downloadButton.contentDescription = binding.root.context.getString(
                when {
                    isDownloaded -> R.string.podcast_delete_download
                    isDownloading -> R.string.podcast_cancel_download
                    else -> R.string.podcast_download
                }
            )
            binding.downloadButton.setOnClickListener {
                if (isDownloaded || isDownloading) onDeleteDownload(episode) else onDownload(episode)
            }

            val context = binding.root.context
            if (episode.id == currentEpisodeId) {
                val accent = context.accentColor()
                binding.episodeTitle.setTextColor(accent)
                binding.episodeTitle.setTypeface(null, Typeface.BOLD)
            } else {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, outValue, true)
                val normalColor = if (outValue.resourceId != 0) {
                    ContextCompat.getColor(context, outValue.resourceId)
                } else {
                    outValue.data
                }
                binding.episodeTitle.setTextColor(normalColor)
                binding.episodeTitle.setTypeface(null, Typeface.NORMAL)
            }
        }

        private fun playedLabel(episode: EpisodeEntity): String? {
            if (episode.durationMs <= 0 || episode.playbackPositionMs <= 0) return null
            val remainingMs = episode.durationMs - episode.playbackPositionMs
            return if (remainingMs <= episode.durationMs * 0.05) "Played" else "In progress"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EpisodeEntity>() {
            override fun areItemsTheSame(oldItem: EpisodeEntity, newItem: EpisodeEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EpisodeEntity, newItem: EpisodeEntity) =
                oldItem == newItem
        }
    }
}
