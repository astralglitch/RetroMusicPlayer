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
 * Placeholder for podcast-specific controls (skip ±30s/10s, playback speed, chapter nav,
 * bookmark button, notes field — see project spec). Not yet implemented; wired up so the seam
 * exists once a real [Fragment] is built.
 */
object PodcastAudioControlsStrategy : PlayerControlsStrategy {
    override fun extrasFragment(): Fragment? = null
}
