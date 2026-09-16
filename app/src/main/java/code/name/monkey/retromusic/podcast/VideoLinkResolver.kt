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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException

/**
 * Resolves a podcast episode's manually-assigned [code.name.monkey.retromusic.db.EpisodeEntity.videoSourceUrl]
 * (a YouTube/etc. page link, not directly playable) to a real, ExoPlayer-playable stream URL, via
 * NewPipeExtractor.
 *
 * Important scope limit: NewPipeExtractor only supports a fixed set of sites (YouTube, SoundCloud,
 * PeerTube, Bandcamp, media.ccc.de) -- notably **not Vimeo**, despite [videoSourceUrl]'s generic
 * name/hint text. Anything NewPipeExtractor doesn't recognize surfaces as [UnsupportedSite]; callers
 * are expected to fall back to opening the link externally in that case. See
 * docs/PODCAST_DESIGN.md "Podcast video, stage 2" for the reasoning.
 *
 * Resolved stream URLs are signed/time-limited by the source site, so they are cached only for the
 * lifetime of the process (never persisted to Room) and only for a short window.
 */
object VideoLinkResolver {

    sealed class Result {
        data class Success(val streamUrl: String, val isHls: Boolean) : Result()
        data object UnsupportedSite : Result()
        data class ResolutionFailed(val reason: String) : Result()
    }

    private data class CacheEntry(val result: Result.Success, val resolvedAtMs: Long)

    private val cache = mutableMapOf<String, CacheEntry>()
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 min -- comfortably under typical signed-URL expiry

    suspend fun resolve(sourceUrl: String): Result = withContext(Dispatchers.IO) {
        cache[sourceUrl]?.let { entry ->
            if (System.currentTimeMillis() - entry.resolvedAtMs < CACHE_TTL_MS) {
                return@withContext entry.result
            }
            cache.remove(sourceUrl)
        }

        val service = try {
            NewPipe.getServiceByUrl(sourceUrl)
        } catch (e: ExtractionException) {
            Log.w(TAG, "getServiceByUrl() didn't recognize $sourceUrl -- treating as unsupported site", e)
            return@withContext Result.UnsupportedSite
        }

        try {
            val extractor = service.getStreamExtractor(sourceUrl)
            extractor.fetchPage()

            val progressiveUrl = extractor.videoStreams.firstOrNull { !it.isVideoOnly }?.url
            val hlsUrl = extractor.hlsUrl
            Log.d(TAG, "Resolved $sourceUrl via ${service.serviceInfo.name}: progressiveUrl=${progressiveUrl != null}, hlsUrl=${!hlsUrl.isNullOrBlank()}")

            val result = when {
                !progressiveUrl.isNullOrBlank() -> Result.Success(progressiveUrl, isHls = false)
                !hlsUrl.isNullOrBlank() -> Result.Success(hlsUrl, isHls = true)
                else -> Result.ResolutionFailed("No playable stream found for this video")
            }

            if (result is Result.Success) {
                cache[sourceUrl] = CacheEntry(result, System.currentTimeMillis())
            }
            result
        } catch (e: PrivateContentException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: private", e)
            Result.ResolutionFailed("This video is private")
        } catch (e: AgeRestrictedContentException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: age-restricted", e)
            Result.ResolutionFailed("This video is age-restricted")
        } catch (e: GeographicRestrictionException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: geo-restricted", e)
            Result.ResolutionFailed("This video isn't available in your region")
        } catch (e: PaidContentException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: paid content", e)
            Result.ResolutionFailed("This video requires payment")
        } catch (e: ContentNotAvailableException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: not available", e)
            Result.ResolutionFailed("This video is unavailable or was removed")
        } catch (e: ExtractionException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: extraction error", e)
            Result.ResolutionFailed(e.message ?: "Couldn't load this video")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Resolution failed for $sourceUrl: network error", e)
            Result.ResolutionFailed(e.message ?: "Network error while loading this video")
        } catch (e: Throwable) {
            // Catches NoSuchMethodError/LinkageError etc. too -- NewPipeExtractor's public API has
            // shifted across versions, and a mismatch there would otherwise surface as a silent
            // coroutine failure with no visible reason.
            Log.e(TAG, "Unexpected error resolving $sourceUrl", e)
            Result.ResolutionFailed(e.message ?: "Unexpected error")
        }
    }

    /** Synchronous, non-suspending cache lookup -- used by [code.name.monkey.retromusic.service.MusicService]
     * at toggle time, which needs an already-resolved stream (if any) without awaiting a network
     * call. Returns null if never resolved or the cache entry has expired; callers should only
     * activate video playback once a prior [resolve] call has populated this. */
    fun peekCached(sourceUrl: String): Result.Success? {
        val entry = cache[sourceUrl] ?: return null
        if (System.currentTimeMillis() - entry.resolvedAtMs >= CACHE_TTL_MS) {
            cache.remove(sourceUrl)
            return null
        }
        return entry.result
    }

    fun clearCache() {
        cache.clear()
    }

    private const val TAG = "VideoLinkResolver"
}
