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

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentEpisodeDetailsBinding
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.util.PreferenceUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.text.DateFormat
import java.util.Date

/**
 * A single episode's own subpage -- reached by tapping an entry in a cross-podcast list
 * (Podcasts Home's Suggestions/Favorites/Inbox/Continue Listening) where an episode's title
 * alone isn't enough context to just play it outright. Shows the episode's own info (podcast,
 * duration, description) plus Play and Download/Delete actions, mirroring PodcastDetailsFragment
 * but scoped to one episode instead of a whole show.
 */
class EpisodeDetailsFragment : AbsMainActivityFragment(R.layout.fragment_episode_details) {

    private var _binding: FragmentEpisodeDetailsBinding? = null
    private val binding get() = _binding!!

    private val arguments by navArgs<EpisodeDetailsFragmentArgs>()
    private val viewModel by viewModel<EpisodeDetailsViewModel> {
        parametersOf(arguments.extraEpisodeId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEpisodeDetailsBinding.bind(view)
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = " "
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.playAction.setOnClickListener { play() }
        binding.downloadAction.setOnClickListener { onDownloadActionClicked() }

        viewModel.episode.observe(viewLifecycleOwner) { episode ->
            if (episode != null) showEpisode(episode)
        }
        viewModel.podcast.observe(viewLifecycleOwner) { podcast ->
            if (podcast != null) showPodcast(podcast)
        }
        viewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            binding.episodeProgress.isVisible = progress != null
            if (progress != null) binding.episodeProgress.progress = progress
        }
        viewModel.isPlayingThisEpisode.observe(viewLifecycleOwner) { isPlaying ->
            binding.playActionIcon.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            binding.playLabel.setText(if (isPlaying) R.string.podcast_pause else R.string.podcast_play)
        }
    }

    private fun showEpisode(episode: EpisodeEntity) {
        binding.episodeTitle.text = episode.title
        // Keep the parsed Spanned rather than flattening it to a plain String with .toString()
        // -- that discarded every link/formatting span the HTML parse just produced, which is
        // why a show note like a YouTube link rendered as inert plain text instead of a tappable
        // link (AntennaPod renders these; this app wasn't, until now). Do NOT additionally run
        // `Linkify.addLinks(TextView, ...)` after this -- that overload reads back `getText()`,
        // and since our Spanned isn't a plain Spannable, it takes a copy-then-conditionally-
        // reassign path that empirically dropped the just-parsed <a>-tag spans entirely (found by
        // instrumenting each step: spans survived parseAsHtml() and trimSpanned(), and were gone
        // only once that Linkify call had run). Bare URLs with no surrounding <a> tag (some feeds'
        // show notes are just raw pasted links) won't get auto-linked without it, but that's a
        // smaller gap than losing every proper HTML anchor.
        binding.episodeDescription.text = episode.description?.parseAsHtml()?.trimSpanned()
        binding.episodeDescription.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        val duration = if (episode.durationMs > 0) {
            val totalSeconds = episode.durationMs / 1000
            String.format(
                "%02d:%02d:%02d",
                totalSeconds / 3600,
                (totalSeconds % 3600) / 60,
                totalSeconds % 60
            )
        } else {
            null
        }
        val date = if (episode.pubDate > 0) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(episode.pubDate))
        } else {
            null
        }
        binding.episodeMeta.text = listOfNotNull(duration, date).joinToString(" · ")

        val isDownloaded = episode.downloadState == EpisodeDownloadState.DOWNLOADED
        val isDownloading = episode.downloadState == EpisodeDownloadState.DOWNLOADING

        // Offline-first default (PreferenceUtil.preferStreaming): a not-yet-downloaded episode
        // has no Play button, nudging toward downloading first. Streaming directly is still
        // reachable via the overflow menu's "Stream episode" (see onMenuItemSelected).
        binding.playAction.isVisible = isDownloaded || PreferenceUtil.preferStreaming

        binding.downloadActionIcon.setImageResource(
            if (isDownloaded) R.drawable.ic_delete else R.drawable.ic_download
        )
        binding.downloadActionLabel.setText(
            when {
                isDownloaded -> R.string.podcast_delete_download
                isDownloading -> R.string.podcast_cancel_download
                else -> R.string.podcast_download
            }
        )

        binding.videoAvailableChip.isVisible = episode.videoUrl != null || episode.videoSourceUrl != null
        binding.videoAvailableChip.setOnClickListener { showVideoAvailabilityInfo(episode) }
    }

    private fun showPodcast(podcast: PodcastEntity) {
        binding.podcastTitle.text = podcast.title
        Glide.with(this)
            .load(podcast.imageUrl)
            .apply(RequestOptions().placeholder(R.drawable.default_audio_art).error(R.drawable.default_audio_art))
            .into(binding.podcastThumbnail)
    }

    private fun play() {
        val episode = viewModel.episode.value ?: return
        // Already the loaded episode -- toggle play/pause in place rather than restarting from
        // 0, same as tapping any other already-playing item elsewhere in the app.
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == episode.id) {
            if (MusicPlayerRemote.isPlaying) {
                MusicPlayerRemote.pauseSong()
            } else {
                MusicPlayerRemote.resumePlaying()
            }
            return
        }
        val podcast = viewModel.podcast.value ?: return
        MusicPlayerRemote.openQueue(listOf(episode.toSong(podcast)), 0, true)
        viewLifecycleOwner.lifecycleScope.activateVideoIfPreferred(episode, podcast)
    }

    /** Unlike [play], never toggles pause/resume on an already-playing episode -- called right
     * before activating the video toggle, where the only goal is "this episode's audio session
     * exists so the toggle has something to swap," not "flip play state." */
    private fun ensurePlaying() {
        val episode = viewModel.episode.value ?: return
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == episode.id) return
        val podcast = viewModel.podcast.value ?: return
        MusicPlayerRemote.openQueue(listOf(episode.toSong(podcast)), 0, true)
    }

    /** [MusicPlayerRemote.openQueue] swaps its internal queue list synchronously but only
     * advances to the new track (and updates [MusicPlayerRemote.currentSong]) via a coroutine
     * launched inside `MusicService`, so it returns before that's actually happened. Activating
     * [PodcastVideoToggle] immediately after [ensurePlaying] would then have
     * `MusicService.toggleEpisodeVideo` read a stale (or momentarily empty) `currentSong` and
     * silently no-op -- the video surface attaches but no source ever gets swapped into it,
     * producing a black surface with audio continuing underneath. Wait (briefly, bounded) for
     * `currentSong` to actually become this episode before flipping the toggle. */
    private suspend fun ensurePlayingAndActivateVideo(episodeId: Long) {
        ensurePlaying()
        withTimeoutOrNull(3000) {
            while (MusicPlayerRemote.currentSong.episodeIdOrNull() != episodeId) {
                delay(50)
            }
        }
        PodcastVideoToggle.setActive(true)
    }

    private fun onDownloadActionClicked() {
        val episode = viewModel.episode.value ?: return
        when (episode.downloadState) {
            EpisodeDownloadState.DOWNLOADED -> viewModel.deleteDownload()
            EpisodeDownloadState.DOWNLOADING -> viewModel.deleteDownload()
            else -> viewModel.download()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_episode_details, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_stream_episode -> {
                play()
                return true
            }

            R.id.action_bookmark_position -> {
                viewModel.addBookmarkAtCurrentPosition()
                requireContext().showToast(getString(R.string.podcast_bookmark_added))
                return true
            }

            R.id.action_edit_notes -> {
                showEditNotesDialog()
                return true
            }

            R.id.action_view_bookmarks -> {
                showBookmarksDialog()
                return true
            }

            R.id.action_video_link -> {
                showVideoLinkDialog()
                return true
            }
        }
        return false
    }

    /** Lets the user assign/edit a link to a video version of this episode (a YouTube/Vimeo/etc.
     * page, entered by hand -- auto-detection from show notes isn't built). If in-app playback is
     * enabled (see [PreferenceUtil.playVideoLinksInApp]), the neutral button resolves the link via
     * [VideoLinkResolver] and plays it through the same audio<->video toggle/inline surface as a
     * feed-direct video (see [playOrOpenVideoLink]); unsupported sites (notably Vimeo --
     * NewPipeExtractor doesn't support it) or resolution failures fall back to opening the link
     * externally, same as when the setting is off. See docs/PODCAST_DESIGN.md "Podcast video" for
     * the full design. */
    private fun showVideoLinkDialog() {
        val current = viewModel.episode.value ?: return
        val input = EditText(requireContext()).apply {
            setText(current.videoSourceUrl)
            setHint(R.string.podcast_video_link_hint)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_video_link)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setVideoSourceUrl(input.text?.toString()?.trim()?.ifBlank { null })
            }
            .setNeutralButton(R.string.podcast_open_in_browser) { _, _ ->
                playOrOpenVideoLink(current.id, current.videoSourceUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Resolves [url] (if in-app playback is enabled) and, on success, plays [episodeId] (if not
     * already playing) and turns on the same audio<->video toggle the Now Playing screen's own
     * button uses ([PodcastVideoToggle]) -- video appears inline where the album art normally
     * sits, then the sliding player panel is expanded so it's actually visible, rather than a
     * separate standalone player. Unsupported sites/resolution failures fall back to external-open,
     * same as before. */
    private fun playOrOpenVideoLink(episodeId: Long, url: String?) {
        if (url.isNullOrBlank()) {
            requireContext().showToast(getString(R.string.podcast_video_link_none))
            return
        }
        if (!PreferenceUtil.playVideoLinksInApp) {
            openVideoLinkExternally(url)
            return
        }
        val loadingSnackbar = Snackbar.make(binding.root, R.string.podcast_video_resolving, Snackbar.LENGTH_INDEFINITE)
        loadingSnackbar.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = VideoLinkResolver.resolve(url)
            loadingSnackbar.dismiss()
            when (result) {
                is VideoLinkResolver.Result.Success -> {
                    ensurePlayingAndActivateVideo(episodeId)
                    mainActivity.expandPanel()
                }

                is VideoLinkResolver.Result.UnsupportedSite -> {
                    requireContext().showToast(getString(R.string.podcast_video_unsupported_site))
                    openVideoLinkExternally(url)
                }

                is VideoLinkResolver.Result.ResolutionFailed -> {
                    requireContext().showToast(
                        getString(R.string.podcast_video_resolution_failed, result.reason)
                    )
                    openVideoLinkExternally(url)
                }
            }
        }
    }

    private fun openVideoLinkExternally(url: String) {
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
    }

    /** A small "video available" indicator, distinguishing where the video came from: directly
     * playable ([EpisodeEntity.videoUrl], from the feed's own video enclosure) vs. an external
     * link ([EpisodeEntity.videoSourceUrl], entered by hand -- auto-detection from show notes
     * isn't built). Tapping offers the relevant action directly instead of just describing it:
     * for a feed video, play the episode (the audio/video toggle picks it up automatically); for
     * an external link, the same resolve-and-play-or-fallback flow as the video link dialog's
     * neutral button, plus an explicit "open in browser" escape hatch. */
    private fun showVideoAvailabilityInfo(episode: EpisodeEntity) {
        when {
            episode.videoUrl != null -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.podcast_video_available)
                    .setMessage(R.string.podcast_video_source_feed)
                    .setPositiveButton(R.string.action_play) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            ensurePlayingAndActivateVideo(episode.id)
                            mainActivity.expandPanel()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            episode.videoSourceUrl != null -> {
                val url = episode.videoSourceUrl
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.podcast_video_available)
                    .setMessage(getString(R.string.podcast_video_source_external, url))
                    .setPositiveButton(R.string.action_play) { _, _ -> playOrOpenVideoLink(episode.id, url) }
                    .setNeutralButton(R.string.podcast_open_in_browser) { _, _ ->
                        openVideoLinkExternally(url)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showBookmarksDialog() {
        val bookmarks = viewModel.bookmarks.value
        val labels = bookmarks.map {
            val totalSeconds = it.timestampMs / 1000
            String.format("%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_view_bookmarks)
            .apply {
                if (labels.isEmpty()) {
                    setMessage(R.string.podcast_no_bookmarks)
                } else {
                    setItems(labels.toTypedArray()) { _, index ->
                        val episode = viewModel.episode.value ?: return@setItems
                        val podcast = viewModel.podcast.value ?: return@setItems
                        MusicPlayerRemote.openQueue(listOf(episode.toSong(podcast)), 0, true)
                        MusicPlayerRemote.seekTo(bookmarks[index].timestampMs.toInt())
                    }
                }
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun showEditNotesDialog() {
        val current = viewModel.episode.value ?: return
        val input = EditText(requireContext()).apply {
            setText(current.userNotes)
            setHint(R.string.podcast_edit_notes)
            minLines = 3
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_edit_notes)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setNotes(input.text?.toString()?.trim()?.ifBlank { null })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
