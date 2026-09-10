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
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.interfaces.IPodcastClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

/**
 * Drives the Podcasts-world Home tab, mirroring HomeAdapter (Music world) -- one section per
 * [PodcastHome] entry, view type keyed off [PodcastHome.section]. Suggestions and Top
 * Subscriptions scroll horizontally (cover-art cards); Favorites/Inbox/Continue Listening are
 * short non-scrolling vertical lists (already capped at 3 items by PodcastHomeViewModel).
 */
class PodcastHomeAdapter(
    private val activity: AppCompatActivity,
    private val onPlayEpisode: (EpisodeEntity) -> Unit,
    private val onToggleFavorited: (EpisodeEntity, Boolean) -> Unit,
    private val onStreamEpisode: (EpisodeEntity) -> Unit,
    private val onSeeAllEpisodes: (Int) -> Unit,
    private val onPodcast: (Long, View) -> Unit,
    private val onSeeAllPodcasts: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var list = listOf<PodcastHome>()
    private var podcastsById = emptyMap<Long, PodcastEntity>()

    override fun getItemViewType(position: Int): Int = list[position].section

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout =
            LayoutInflater.from(activity).inflate(R.layout.section_recycler_view, parent, false)
        return when (viewType) {
            PODCAST_TOP_SUBSCRIPTIONS -> PodcastSectionViewHolder(layout)
            PODCAST_SUGGESTIONS -> SuggestionSectionViewHolder(layout)
            else -> EpisodeSectionViewHolder(layout)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val home = list[position]
        when (holder) {
            is SuggestionSectionViewHolder -> holder.bindView(home, home.items as List<EpisodeEntity>)
            is PodcastSectionViewHolder -> holder.bindView(home, home.items as List<PodcastEntity>)
            is EpisodeSectionViewHolder -> holder.bindView(home, home.items as List<EpisodeEntity>)
        }
    }

    override fun getItemCount(): Int = list.size

    @SuppressLint("NotifyDataSetChanged")
    fun swapData(sections: List<PodcastHome>, podcastsById: Map<Long, PodcastEntity>) {
        list = sections
        this.podcastsById = podcastsById
        notifyDataSetChanged()
    }

    open class AbsHomeViewItem(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerView)
        val title: AppCompatTextView = itemView.findViewById(R.id.title)
        val clickableArea: ViewGroup = itemView.findViewById(R.id.clickable_area)
    }

    /** A short, non-scrolling vertical list -- Favorites / Inbox / Continue Listening. */
    private inner class EpisodeSectionViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: PodcastHome, episodes: List<EpisodeEntity>) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener { onSeeAllEpisodes(home.section) }
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
                    onTogglePlayed = { _, _ -> },
                    onToggleFavorited = onToggleFavorited,
                    onStream = onStreamEpisode,
                    podcastsById = podcastsById
                ).apply { submitList(episodes) }
            }
        }
    }

    /** Horizontal strip of subscribed shows -- Top Subscriptions. */
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
                            // Not `onPodcast(...)` -- that shadows this override and recurses
                            // into itself (StackOverflowError) instead of calling the outer
                            // class's callback property.
                            this@PodcastHomeAdapter.onPodcast(podcastId, view)
                        }
                    }
                )
            }
        }
    }

    /** Horizontal strip of randomly suggested episodes, shown with their podcast's cover art
     * since episodes have none of their own. */
    private inner class SuggestionSectionViewHolder(view: View) : AbsHomeViewItem(view) {
        fun bindView(home: PodcastHome, episodes: List<EpisodeEntity>) {
            title.setText(home.titleRes)
            clickableArea.setOnClickListener { onSeeAllEpisodes(home.section) }
            recyclerView.apply {
                layoutManager = GridLayoutManager(activity, 1, GridLayoutManager.HORIZONTAL, false)
                isNestedScrollingEnabled = false
                adapter = EpisodeSuggestionAdapter(episodes, podcastsById, onPlayEpisode, onStreamEpisode)
            }
        }
    }

    private class EpisodeSuggestionAdapter(
        private val episodes: List<EpisodeEntity>,
        private val podcastsById: Map<Long, PodcastEntity>,
        private val onClick: (EpisodeEntity) -> Unit,
        private val onStream: (EpisodeEntity) -> Unit
    ) : RecyclerView.Adapter<EpisodeSuggestionAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val episode = episodes[position]
            val podcast = podcastsById[episode.podcastId]
            holder.title.text = episode.title
            Glide.with(holder.itemView)
                .load(podcast?.imageUrl)
                .apply(RequestOptions().placeholder(R.drawable.default_audio_art).error(R.drawable.default_audio_art))
                .into(holder.image)
            holder.itemView.setOnClickListener { onClick(episode) }
            // Suggestions cards have no other long-press menu (unlike EpisodeAdapter rows) --
            // this is just the offline-first "Stream episode" escape hatch, same as everywhere
            // else with an episode list.
            holder.itemView.setOnLongClickListener {
                if (episode.downloadState == EpisodeDownloadState.DOWNLOADED) return@setOnLongClickListener false
                PopupMenu(it.context, it).apply {
                    menu.add(R.string.podcast_stream_episode)
                    setOnMenuItemClickListener { onStream(episode); true }
                }.show()
                true
            }
        }

        override fun getItemCount(): Int = episodes.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image: ImageView = itemView.findViewById(R.id.image)
            val title: AppCompatTextView = itemView.findViewById(R.id.title)
        }
    }
}
