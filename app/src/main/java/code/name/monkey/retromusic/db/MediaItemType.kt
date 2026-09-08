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

/**
 * What kind of playback experience a [MediaItemEntity] should get. This is a property of the
 * item itself (set from source defaults, overridable per-item by the user) — never a rigid
 * category inferred from where the item came from.
 */
enum class MediaItemType {
    MUSIC,
    PODCAST_AUDIO,
    PODCAST_VIDEO
}
