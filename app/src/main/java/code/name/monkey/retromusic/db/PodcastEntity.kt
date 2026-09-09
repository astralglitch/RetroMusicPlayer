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
package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A subscribed podcast feed. */
@Entity(indices = [Index(value = ["feed_url"], unique = true)])
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "feed_url")
    val feedUrl: String,

    val title: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    val description: String? = null,

    @ColumnInfo(name = "last_fetched")
    val lastFetched: Long = 0,

    /** Starred subscription -- separate from favorited episodes (EpisodeEntity.favorited). */
    val favorited: Boolean = false
)
