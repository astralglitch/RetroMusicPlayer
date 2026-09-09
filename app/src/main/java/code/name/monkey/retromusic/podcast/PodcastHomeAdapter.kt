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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.interfaces.IPodcastClickListener

/**
 * Drives the Podcasts-world Home tab, mirroring HomeAdapter (Music world) -- one section per
 * [PodcastHome] entry, view type keyed off [PodcastHome.section]. Episode sections (Continue
 * Listening / New Episodes) reuse EpisodeAdapter's full-width rows in a short, non-scrolling
 * vertical list; the podcasts section reuses PodcastAdapter in a horizontal grid, like the
 * Music Home's Recent/Top Albums strips.
 */
class PodcastHomeAdapter(
    private val activity: AppCompatActivity,
    private val onPlayEpisode: (EpisodeEntity) -> Unit,
    private val onSeeAllEpisodes: () -> Unit,
    private val onPodcast: (Long, View) -> Unit,
    private val onSeeAllPodcasts: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var list = listOf<PodcastHome>()

    override fun getItemViewType(position: Int): Int = list[position].section

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout =
            LayoutInflater.from(activity).inflate(R.layout.section_recycler_view, parent, false)
        return when (viewType) {
            PODCAST_YOUR_PODCASTS -> PodcastSectionViewHolder(layout)
            else -> EpisodeSectionViewHolder(layout)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val home = list[position]
        when (holder) {
            is EpisodeSectionViewHolder -> holder.bindView(home, home.items as List<EpisodeEntity>)
            is PodcastSectionViewHolder -> holder.bindView(home, home.items as List<PodcastEntity>)
        }
    }

    override fun getItemCount(): Int = list.size

    @SuppressLint("NotifyDataSetChanged")
    fun swapData(sections: List<PodcastHome>) {
        list = sections
        notifyDataSetChanged()
    }

    open class AbsHomeViewItem(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)
        val title: AppCompatTextView = itemView.findViewById(R.id.title)
        val clickableArea: ViewGroup = itemView.findViewById(R.id.clickable_area)
    }

    private inner class EpisodeSectionViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: PodcastHome, episodes: List<EpisodeEntity>) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener { onSeeAllEpisodes() }
            recyclerView.apply {
                layoutManager = LinearLayoutManager(activity)
                isNestedScrollingEnabled = false
                // Download/played actions aren't wired here -- a tap anywhere on a Home teaser
                // row, including its action button, just opens the full podcast/episode screen
                // where those actions live for real.
                adapter = EpisodeAdapter(
                    onPlay = onPlayEpisode,
                    onDownload = onPlayEpisode,
                    onDeleteDownload = onPlayEpisode,
                    onTogglePlayed = { _, _ -> }
                ).apply { submitList(episodes) }
            }
        }
    }

    private inner class PodcastSectionViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: PodcastHome, podcasts: List<PodcastEntity>) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener { onSeeAllPodcasts() }
            recyclerView.apply {
                layoutManager = GridLayoutManager(activity, 1, GridLayoutManager.HORIZONTAL, false)
                isNestedScrollingEnabled = false
                adapter = PodcastAdapter(
                    activity,
                    podcasts,
                    R.layout.item_image,
                    object : IPodcastClickListener {
                        override fun onPodcast(podcastId: Long, view: View) {
                            onPodcast(podcastId, view)
                        }
                    }
                )
            }
        }
    }
}
