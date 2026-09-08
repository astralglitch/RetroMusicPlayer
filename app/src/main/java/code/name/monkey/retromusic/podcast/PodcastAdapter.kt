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
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.adapter.base.MediaEntryViewHolder
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.interfaces.IPodcastClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

/**
 * A generic grid/list adapter for subscribed podcasts, mirroring ArtistAdapter/AlbumAdapter --
 * itemLayoutRes is swapped between the shared item_list/item_grid/item_card/etc layouts (see
 * AbsRecyclerViewCustomGridSizeFragment) so podcasts get the same grid-size and grid-style
 * controls as every other tab, rather than the old fixed horizontal strip.
 */
class PodcastAdapter(
    private val activity: FragmentActivity,
    var dataSet: List<PodcastEntity>,
    var itemLayoutRes: Int,
    private val listener: IPodcastClickListener
) : RecyclerView.Adapter<PodcastAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = dataSet[position].id

    @SuppressLint("NotifyDataSetChanged")
    fun swapDataSet(dataSet: List<PodcastEntity>) {
        this.dataSet = dataSet
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = try {
            LayoutInflater.from(activity).inflate(itemLayoutRes, parent, false)
        } catch (e: Resources.NotFoundException) {
            LayoutInflater.from(activity).inflate(R.layout.item_grid, parent, false)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val podcast = dataSet[position]
        holder.title?.text = podcast.title
        holder.text?.isVisible = false
        holder.menu?.isVisible = false
        val transitionName = podcast.id.toString()
        if (holder.imageContainer != null) {
            holder.imageContainer?.transitionName = transitionName
        } else {
            holder.image?.transitionName = transitionName
        }
        if (holder.image != null) {
            Glide.with(activity)
                .load(podcast.imageUrl)
                .apply(RequestOptions().placeholder(R.drawable.default_audio_art).error(R.drawable.default_audio_art))
                .into(holder.image!!)
        }
    }

    override fun getItemCount(): Int = dataSet.size

    inner class ViewHolder(itemView: View) : MediaEntryViewHolder(itemView) {
        override fun onClick(v: View?) {
            val podcast = dataSet[layoutPosition]
            listener.onPodcast(podcast.id, imageContainer ?: image ?: itemView)
        }

        override fun onLongClick(v: View?): Boolean = false
    }
}
