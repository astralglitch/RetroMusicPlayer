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
import code.name.monkey.retromusic.databinding.ItemPodcastBinding
import code.name.monkey.retromusic.db.PodcastEntity

class PodcastAdapter(private val onClick: (PodcastEntity) -> Unit) :
    ListAdapter<PodcastEntity, PodcastAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPodcastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPodcastBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(podcast: PodcastEntity) {
            binding.podcastTitle.text = podcast.title
            binding.root.setOnClickListener { onClick(podcast) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PodcastEntity>() {
            override fun areItemsTheSame(oldItem: PodcastEntity, newItem: PodcastEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PodcastEntity, newItem: PodcastEntity) =
                oldItem == newItem
        }
    }
}
