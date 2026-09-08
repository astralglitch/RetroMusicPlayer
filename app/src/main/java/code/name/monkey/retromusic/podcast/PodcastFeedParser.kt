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

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedFeed(
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val episodes: List<ParsedEpisode>
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val enclosureUrl: String,
    val pubDate: Long,
    val durationMs: Long,
    val description: String?
)

/**
 * Minimal RSS 2.0 (+ iTunes namespace) podcast feed parser, built on Android's bundled
 * [XmlPullParser] rather than pulling in a feed-parsing library (e.g. `rome`, which assumes a
 * full JVM and doesn't target Android cleanly). Only reads the fields the app currently uses;
 * extend as more of the spec (chapters, transcripts, season/episode numbers) is needed.
 */
object PodcastFeedParser {

    // RFC 822 date, e.g. "Wed, 02 Oct 2024 15:00:00 +0000" — the format almost every RSS feed
    // uses for pubDate. Feeds that deviate fall back to 0 (episodes still parse, just unordered).
    private val pubDateFormat =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var channelTitle = ""
        var channelImage: String? = null
        var channelDescription: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        var inChannel = false
        var inItem = false

        var itemGuid: String? = null
        var itemTitle: String? = null
        var itemEnclosureUrl: String? = null
        var itemPubDate: Long = 0
        var itemDurationMs: Long = 0
        var itemDescription: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "channel" -> inChannel = true
                        "item" -> {
                            inItem = true
                            itemGuid = null
                            itemTitle = null
                            itemEnclosureUrl = null
                            itemPubDate = 0
                            itemDurationMs = 0
                            itemDescription = null
                        }

                        "title" -> {
                            val text = parser.nextTextSafe()
                            if (inItem) itemTitle = text else if (inChannel) channelTitle = text
                        }

                        "description", "summary" -> {
                            val text = parser.nextTextSafe()
                            if (inItem) itemDescription = text else if (inChannel) channelDescription =
                                text
                        }

                        "guid" -> if (inItem) itemGuid = parser.nextTextSafe()

                        "pubDate" -> if (inItem) itemPubDate = parsePubDate(parser.nextTextSafe())

                        "enclosure" -> if (inItem) {
                            itemEnclosureUrl = parser.getAttributeValue(null, "url")
                        }

                        "duration" -> if (inItem) itemDurationMs = parseItunesDuration(parser.nextTextSafe())

                        "image" -> if (inChannel && !inItem) {
                            // <itunes:image href="..."/> (self-closing) vs RSS <image><url>...</url></image>
                            parser.getAttributeValue(null, "href")?.let { channelImage = it }
                        }

                        "url" -> if (inChannel && !inItem && channelImage == null) {
                            channelImage = parser.nextTextSafe()
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "channel" -> inChannel = false
                        "item" -> {
                            inItem = false
                            val url = itemEnclosureUrl
                            val guid = itemGuid ?: url
                            if (url != null && guid != null && itemTitle != null) {
                                episodes += ParsedEpisode(
                                    guid = guid,
                                    title = itemTitle,
                                    enclosureUrl = url,
                                    pubDate = itemPubDate,
                                    durationMs = itemDurationMs,
                                    description = itemDescription
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = channelTitle,
            imageUrl = channelImage,
            description = channelDescription,
            episodes = episodes
        )
    }

    private fun XmlPullParser.nextTextSafe(): String =
        try {
            nextText()
        } catch (e: Exception) {
            ""
        }.trim()

    private fun parsePubDate(raw: String): Long =
        try {
            pubDateFormat.parse(raw)?.time ?: 0
        } catch (e: Exception) {
            0
        }

    /** iTunes duration is either plain seconds ("1830") or "HH:MM:SS" / "MM:SS". */
    private fun parseItunesDuration(raw: String): Long {
        if (raw.isBlank()) return 0
        val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
        return seconds * 1000
    }
}
