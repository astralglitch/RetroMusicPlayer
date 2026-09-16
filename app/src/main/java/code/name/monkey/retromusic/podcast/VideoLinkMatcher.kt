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

import code.name.monkey.retromusic.db.VideoLinkMatchType
import code.name.monkey.retromusic.db.VideoLinkRuleEntity
import java.util.regex.Pattern

/**
 * Scans an episode's show notes for links matching a podcast's configured
 * [VideoLinkRuleEntity] rules -- the auto-detection counterpart to the manual "Video link"
 * dialog on [EpisodeDetailsFragment]. Pure/no Android or DB dependency so it's directly unit
 * testable (see `VideoLinkMatcherTest`).
 *
 * Ordering: every URL found in [description] is walked href-attributes-first (in the order
 * they appear), then bare URLs not already captured by an href (also in appearance order) --
 * a feed's show notes are sometimes plain HTML with anchors and sometimes plain text with pasted
 * links, and treating hrefs as a first pass avoids a link's own href and its visible duplicate
 * plain-text URL being counted as two separate candidates in an arbitrary interleaving. Each URL
 * surviving that pass is checked
 * against every enabled rule ordered by [VideoLinkRuleEntity.priority]. The first URL to match
 * *any* rule is the caller's primary link; every other match is a secondary candidate, in the
 * order encountered -- see [EpisodeEntity.videoLinkCandidates] for how the caller stores that.
 */
object VideoLinkMatcher {

    private val HREF_PATTERN: Pattern =
        Pattern.compile("""href\s*=\s*["'](https?://[^"']+)["']""", Pattern.CASE_INSENSITIVE)

    private val BARE_URL_PATTERN: Pattern =
        Pattern.compile("""https?://[^\s"'<>)]+""")

    /** Returns every matching link, ordered first-match-first, or empty if no rule matches
     * anything (or [rules] is empty). */
    fun findMatches(description: String?, rules: List<VideoLinkRuleEntity>): List<String> {
        if (description.isNullOrBlank() || rules.isEmpty()) return emptyList()

        val enabledRules = rules.filter { it.enabled }.sortedBy { it.priority }
        if (enabledRules.isEmpty()) return emptyList()

        val candidateUrls = extractUrlsInOrder(description)
        if (candidateUrls.isEmpty()) return emptyList()

        val compiledRules = enabledRules.mapNotNull { rule -> ruleRegex(rule)?.let { rule to it } }

        return candidateUrls.filter { url -> compiledRules.any { (_, regex) -> regex.containsMatchIn(url) } }
    }

    private fun ruleRegex(rule: VideoLinkRuleEntity): Regex? {
        val pattern = when (rule.matchType) {
            VideoLinkMatchType.PRESET -> VideoLinkPresets.byId(rule.pattern)?.domainRegex
            VideoLinkMatchType.REGEX -> rule.pattern
        }
        return pattern?.let {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                null // a malformed custom regex should never crash a feed refresh
            }
        }
    }

    /** Every distinct URL in [description], in the order it first appears -- href attributes
     * take priority over a bare-URL scan of the same text, so an anchor's visible/duplicate
     * plain-text URL (if any) doesn't produce a second, later-ordered entry for the same link. */
    private fun extractUrlsInOrder(description: String): List<String> {
        val seen = LinkedHashSet<String>()

        val hrefMatcher = HREF_PATTERN.matcher(description)
        while (hrefMatcher.find()) {
            seen += hrefMatcher.group(1)
        }

        val bareMatcher = BARE_URL_PATTERN.matcher(description)
        while (bareMatcher.find()) {
            seen += bareMatcher.group()
        }

        return seen.toList()
    }
}
