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
import code.name.monkey.retromusic.databinding.ItemEpisodeBinding
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import java.text.DateFormat
import java.util.Date

class EpisodeAdapter(
    private val onPlay: (EpisodeEntity) -> Unit,
    private val onDownload: (EpisodeEntity) -> Unit
) : ListAdapter<EpisodeEntity, EpisodeAdapter.ViewHolder>(DIFF) {

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
            val statusLabel = when (episode.downloadState) {
                EpisodeDownloadState.DOWNLOADED -> "Downloaded"
                EpisodeDownloadState.DOWNLOADING -> "Downloading…"
                EpisodeDownloadState.FAILED -> "Download failed"
                EpisodeDownloadState.NOT_DOWNLOADED -> null
            }
            binding.episodeMeta.text = listOfNotNull(date.ifBlank { null }, statusLabel)
                .joinToString(" · ")

            binding.downloadButton.isEnabled = episode.downloadState != EpisodeDownloadState.DOWNLOADING
            binding.downloadButton.alpha = if (episode.downloadState == EpisodeDownloadState.DOWNLOADED) 0.4f else 1f
            binding.downloadButton.setOnClickListener { onDownload(episode) }
            binding.playButton.setOnClickListener { onPlay(episode) }
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
