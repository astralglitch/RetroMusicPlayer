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
 * Where a podcast's episode list comes from -- an RSS feed, or a local/network folder mapped as
 * a podcast source (a folder's audio files are treated as "episodes" the same way music files
 * are picked up, per AGENTS.md goal #2). See [PodcastRepository.subscribeRss]/[subscribeFolder].
 */
sealed interface PodcastSource {
    data class RssSource(val feedUrl: String) : PodcastSource
    data class FolderSource(val treeUri: String) : PodcastSource
}
