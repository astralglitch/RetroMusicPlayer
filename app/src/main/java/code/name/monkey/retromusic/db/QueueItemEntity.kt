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

/** One episode's slot in a [QueueEntity] -- [position] is a dense 0-based ordering within that
 * queue, rewritten in full on every reorder/insert/remove (queues are short enough that this is
 * simpler than maintaining gaps). */
@Entity(indices = [Index(value = ["queue_id", "episode_id"], unique = true)])
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "queue_id")
    val queueId: Long,

    @ColumnInfo(name = "episode_id")
    val episodeId: Long,

    val position: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
