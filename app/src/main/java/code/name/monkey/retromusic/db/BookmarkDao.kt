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
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BookmarkDao {

    @Insert
    fun insertBookmark(bookmarkEntity: BookmarkEntity): Long

    @Delete
    fun deleteBookmark(bookmarkEntity: BookmarkEntity)

    @Query("SELECT * FROM BookmarkEntity WHERE episode_id = :episodeId ORDER BY timestamp_ms ASC")
    fun bookmarksForEpisode(episodeId: Long): List<BookmarkEntity>
}
