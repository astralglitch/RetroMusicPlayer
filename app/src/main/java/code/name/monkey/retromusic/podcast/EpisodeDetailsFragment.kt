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

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentEpisodeDetailsBinding
import code.name.monkey.retromusic.db.EpisodeDownloadState
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
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
    }

    private fun showEpisode(episode: EpisodeEntity) {
        binding.episodeTitle.text = episode.title
        binding.episodeDescription.text = episode.description?.parseAsHtml()?.toString()?.trim()
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
        val podcast = viewModel.podcast.value ?: return
        MusicPlayerRemote.openQueue(listOf(episode.toSong(podcast)), 0, true)
    }

    private fun onDownloadActionClicked() {
        val episode = viewModel.episode.value ?: return
        when (episode.downloadState) {
            EpisodeDownloadState.DOWNLOADED -> viewModel.deleteDownload()
            EpisodeDownloadState.DOWNLOADING -> viewModel.deleteDownload()
            else -> viewModel.download()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem) = false

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
