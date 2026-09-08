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
package code.name.monkey.retromusic.fragments.player.controls

import androidx.fragment.app.Fragment
import code.name.monkey.retromusic.db.MediaItemType
import code.name.monkey.retromusic.podcast.PodcastExtraControlsFragment

/**
 * Extra, type-specific controls shown alongside the base transport controls
 * (play/pause/seek/queue, which every [MediaItemType] gets from [AbsPlayerControlsFragment]
 * unconditionally). A strategy contributes nothing by returning a null [extrasFragment] —
 * that's the [MediaItemType.MUSIC] case, so plain music playback stays visually a no-op.
 *
 * Kept independent of any single themed player layout: [AbsPlayerControlsFragment] hosts the
 * returned fragment in an opt-in container ([AbsPlayerControlsFragment.extraControlsContainerId])
 * so individual theme fragments can adopt type-driven extras without every theme needing to.
 */
interface PlayerControlsStrategy {
    fun extrasFragment(): Fragment?

    companion object {
        fun forType(type: MediaItemType): PlayerControlsStrategy = when (type) {
            MediaItemType.MUSIC -> MusicControlsStrategy
            MediaItemType.PODCAST_AUDIO -> PodcastAudioControlsStrategy
            MediaItemType.PODCAST_VIDEO -> PodcastAudioControlsStrategy
        }
    }
}

/** No extra controls beyond the base transport — music playback is unaffected. */
object MusicControlsStrategy : PlayerControlsStrategy {
    override fun extrasFragment(): Fragment? = null
}

/**
 * Skip ±10/30s, playback speed, and tap-through to the podcast. Chapter navigation, bookmarks,
 * and a notes field are still just design notes (see docs/PODCAST_DESIGN.md) — not built here yet.
 */
object PodcastAudioControlsStrategy : PlayerControlsStrategy {
    override fun extrasFragment(): Fragment = PodcastExtraControlsFragment()
}
