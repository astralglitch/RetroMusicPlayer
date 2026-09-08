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

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import code.name.monkey.retromusic.db.EpisodeDao
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Wraps the system [DownloadManager] for episode downloads rather than rolling a custom
 * download/retry queue — episodes are large, infrequent, single-shot downloads, exactly what
 * DownloadManager already handles (background execution, retries, notification progress) for
 * free. Revisit only if resumable-download or bandwidth-throttling needs outgrow it.
 */
class EpisodeDownloadManager(
    private val context: Context,
    private val episodeDao: EpisodeDao
) {
    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadsDir(): File =
        context.getExternalFilesDir("podcast_downloads") ?: context.filesDir

    suspend fun enqueue(episode: EpisodeEntity) {
        val targetFile = File(downloadsDir(), "${episode.id}.media")
        val request = DownloadManager.Request(episode.enclosureUrl.toUri())
            .setTitle(episode.title)
            .setDestinationUri(Uri.fromFile(targetFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)

        val downloadId = downloadManager.enqueue(request)
        episodeDao.upsertEpisode(
            episode.copy(
                downloadState = EpisodeDownloadState.DOWNLOADING,
                downloadId = downloadId,
                localFilePath = null
            )
        )
    }

    /** Registers the completion receiver; call from a long-lived scope (e.g. Application.onCreate). */
    fun registerReceiver(scope: CoroutineScope) {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId == -1L) return
                    scope.launch(Dispatchers.IO) { reconcile(downloadId) }
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * 0-100, or null if DownloadManager has no size estimate yet (common right at the start of a
     * transfer) or no longer knows about this download. Used to show real progress instead of a
     * static "Downloading…" label, which reads as "stuck" on a slow connection even when it's
     * still making progress.
     */
    fun queryProgressPercent(downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val downloadedSoFar =
                cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalSize = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (totalSize <= 0) return null
            return ((downloadedSoFar * 100) / totalSize).toInt().coerceIn(0, 100)
        }
    }

    /** Removes the downloaded file (via DownloadManager, which also drops its own record) and resets state. */
    suspend fun deleteDownload(episode: EpisodeEntity) {
        episode.downloadId?.let { downloadManager.remove(it) }
        episode.localFilePath?.let { File(it).delete() }
        episodeDao.upsertEpisode(
            episode.copy(
                downloadState = EpisodeDownloadState.NOT_DOWNLOADED,
                downloadId = null,
                localFilePath = null
            )
        )
    }

    /**
     * Re-checks every episode still marked DOWNLOADING against the real DownloadManager state.
     * The completion broadcast above is the fast path, but dynamically-registered receivers can
     * miss it (process death, OEM battery management, app not in the foreground at the moment
     * DownloadManager delivers it) -- so callers should also invoke this defensively, e.g. when
     * the podcast screen resumes, to self-heal a row stuck showing "Downloading…" forever.
     */
    suspend fun reconcileInFlightDownloads(episodes: List<EpisodeEntity>) {
        episodes
            .filter { it.downloadState == EpisodeDownloadState.DOWNLOADING && it.downloadId != null }
            .forEach { reconcile(it.downloadId!!) }
    }

    private suspend fun reconcile(downloadId: Long) {
        val episode = episodeDao.episodeForDownloadId(downloadId) ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val status = cursor.getInt(statusIndex)
            if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
                return // genuinely still in progress, nothing to reconcile yet
            }
            val succeeded = status == DownloadManager.STATUS_SUCCESSFUL
            val localPath = if (succeeded) cursor.getString(localUriIndex)?.toUri()?.path else null

            episodeDao.upsertEpisode(
                episode.copy(
                    downloadState = if (succeeded) EpisodeDownloadState.DOWNLOADED else EpisodeDownloadState.FAILED,
                    localFilePath = localPath
                )
            )
        }
    }
}
