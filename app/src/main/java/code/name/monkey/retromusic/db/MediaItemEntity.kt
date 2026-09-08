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
import androidx.room.PrimaryKey

/**
 * Type-and-metadata sidecar for a library item (song or podcast episode).
 *
 * Deliberately kept separate from [SongEntity]/[PlayCountEntity] rather than adding columns to
 * them: most library items (plain music from a file scan) will never have a row here at all, and
 * this table is additive on top of the existing music-only schema instead of migrating it.
 *
 * [songId] mirrors the MediaStore id used elsewhere in the app (see [PlayCountEntity.id]) — the
 * same id a file-scanned track or a downloaded podcast episode is identified by.
 *
 * Type-specific data (feed URL, chapters, notes) is kept as nullable columns rather than a JSON
 * blob for now, since Room can query/index them directly; revisit if the set of type-specific
 * fields grows unwieldy.
 */
@Entity
class MediaItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: Long,

    /** Default type derived from the item's source (file scan vs. feed enclosure format). */
    @ColumnInfo(name = "type")
    val type: MediaItemType,

    /** True once the user has manually reclassified [type] away from its source-derived default. */
    @ColumnInfo(name = "is_manual_override", defaultValue = "0")
    val isManualOverride: Boolean = false,

    /** Subscription feed URL, present only for items sourced from a podcast feed. */
    @ColumnInfo(name = "feed_url")
    val feedUrl: String? = null,

    /** Serialized chapter list, present only for podcast episodes that expose chapters. */
    @ColumnInfo(name = "chapters_json")
    val chaptersJson: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
