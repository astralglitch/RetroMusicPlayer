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

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Turns a SAF tree ([android.content.Intent.ACTION_OPEN_DOCUMENT_TREE] result) into a
 * [ParsedFeed]-shaped listing, so a local/network folder can flow through the same
 * subscribe/refresh/persist path as an RSS feed (see [PodcastRepository.subscribeFolder]).
 * There's no feed metadata to read, so [ParsedFeed.title]/[ParsedFeed.imageUrl] are left for the
 * caller to fill in from the folder's own name; only the episode list matters here.
 */
object FolderPodcastScanner {

    /** [ParsedEpisode.guid] is the file's own content URI -- stable across re-scans as long as
     * the file isn't moved, and unique without needing to hash anything. [ParsedEpisode.pubDate]
     * uses [DocumentFile.lastModified] since folder-sourced files have no feed-supplied date. */
    fun scan(context: Context, treeUri: Uri): List<ParsedEpisode> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
            .asSequence()
            .filter { it.isFile && it.type?.startsWith("audio/") == true }
            .map { file ->
                val uri = file.uri.toString()
                ParsedEpisode(
                    guid = uri,
                    title = file.name?.substringBeforeLast('.') ?: uri,
                    enclosureUrl = uri,
                    pubDate = file.lastModified(),
                    durationMs = 0,
                    description = null
                )
            }
            .sortedByDescending { it.pubDate }
            .toList()
    }
}
