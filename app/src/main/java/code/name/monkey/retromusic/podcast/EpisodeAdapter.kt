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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.ItemEpisodeBinding
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import java.text.DateFormat
import java.util.Date

class EpisodeAdapter(
    private val onPlay: (EpisodeEntity) -> Unit,
    private val onDownload: (EpisodeEntity) -> Unit,
    private val onDeleteDownload: (EpisodeEntity) -> Unit
) : ListAdapter<EpisodeEntity, EpisodeAdapter.ViewHolder>(DIFF) {

    /** episode id -> 0-100, set by PodcastsFragment as PodcastsViewModel.downloadProgress ticks. */
    private var progressByEpisodeId: Map<Long, Int> = emptyMap()

    fun updateProgress(progress: Map<Long, Int>) {
        progressByEpisodeId = progress
        progress.keys.forEach { episodeId ->
            val position = currentList.indexOfFirst { it.id == episodeId }
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
            binding.playButton.setOnClickListener { onPlay(episode) }
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
