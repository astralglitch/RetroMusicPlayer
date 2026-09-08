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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MediaItemDao {

    @Upsert
    fun upsertMediaItem(mediaItemEntity: MediaItemEntity)

    @Delete
    fun deleteMediaItem(mediaItemEntity: MediaItemEntity)

    @Query("SELECT * FROM MediaItemEntity WHERE song_id = :songId LIMIT 1")
    fun mediaItemForSong(songId: Long): MediaItemEntity?

    @Query("SELECT * FROM MediaItemEntity WHERE type = :type")
    fun mediaItemsOfType(type: MediaItemType): List<MediaItemEntity>
}
