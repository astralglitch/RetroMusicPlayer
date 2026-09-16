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

/**
 * Built-in "video link rule" presets offered when a user adds a rule via
 * [VideoLinkRulesFragment], keyed by a stable id stored as [code.name.monkey.retromusic.db.VideoLinkRuleEntity.pattern]
 * when its `matchType` is `PRESET`. Limited to sites [VideoLinkResolver] actually knows how to
 * resolve to a playable stream (YouTube, SoundCloud) plus Vimeo -- a common show-notes link that
 * NewPipeExtractor can't resolve, but still worth auto-detecting so it at least falls back to
 * "open externally" instead of sitting unrecognized in the notes text.
 */
data class VideoLinkPreset(val id: String, val displayName: String, val domainRegex: String)

object VideoLinkPresets {
    val all: List<VideoLinkPreset> = listOf(
        VideoLinkPreset("youtube", "YouTube", """(?:youtube\.com/watch|youtu\.be/)"""),
        VideoLinkPreset("vimeo", "Vimeo", """vimeo\.com/"""),
        VideoLinkPreset("soundcloud", "SoundCloud", """soundcloud\.com/""")
    )

    fun byId(id: String): VideoLinkPreset? = all.firstOrNull { it.id == id }
}
