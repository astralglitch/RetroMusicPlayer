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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastSettingsBinding
import code.name.monkey.retromusic.databinding.ItemPodcastSettingBinding
import code.name.monkey.retromusic.databinding.ItemPodcastSettingHeaderBinding
import code.name.monkey.retromusic.db.PlayPreference
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.db.PodcastSourceType
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.util.PreferenceUtil
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * A dedicated full-screen settings page per podcast, reached from the gear icon on
 * [PodcastDetailsFragment] -- replaces that screen's original single feed-URL-edit dialog, which
 * read as too thin once there was clearly going to be more here over time (rename, per-podcast
 * playback rules, auto-download policy, etc. -- see AGENTS.md). Modeled on AntennaPod's own
 * per-podcast settings screen (grouped sections, icon+title+subtitle rows) per the user's
 * reference screenshot, though most sections here are still placeholders -- only the Feed
 * section actually does anything yet. Built by adding rows programmatically to a plain
 * LinearLayout rather than a Preference/RecyclerView screen, since the row count is small and
 * fixed and this avoids a second UI framework for what's currently a handful of items.
 */
class PodcastSettingsFragment : AbsMainActivityFragment(R.layout.fragment_podcast_settings) {

    private var _binding: FragmentPodcastSettingsBinding? = null
    private val binding get() = _binding!!

    private val arguments by navArgs<PodcastSettingsFragmentArgs>()
    private val viewModel by viewModel<PodcastDetailsViewModel> {
        parametersOf(arguments.extraPodcastId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastSettingsBinding.bind(view)
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = getString(R.string.podcast_settings)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewModel.podcast.observe(viewLifecycleOwner) { podcast ->
            if (podcast != null) buildSettingsList(podcast)
        }
    }

    private fun buildSettingsList(podcast: PodcastEntity) {
        binding.toolbar.subtitle = podcast.title
        binding.settingsContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        addHeader(inflater, R.string.podcast_settings_section_feed)
        if (podcast.sourceType == PodcastSourceType.RSS) {
            addItem(
                inflater,
                icon = R.drawable.ic_edit,
                title = getString(R.string.podcast_settings_feed_url),
                subtitle = podcast.feedUrl,
                onClick = { showFeedUrlDialog(podcast) }
            )
        } else {
            addItem(
                inflater,
                icon = R.drawable.ic_folder,
                title = getString(R.string.podcast_settings_folder_path),
                subtitle = podcast.folderUri,
                enabled = false
            )
        }

        addHeader(inflater, R.string.podcast_settings_section_display)
        addItem(
            inflater,
            icon = R.drawable.ic_edit,
            title = getString(R.string.podcast_settings_rename),
            subtitle = getString(R.string.podcast_settings_coming_soon),
            enabled = false,
            onClick = ::showComingSoon
        )
        addItem(
            inflater,
            icon = R.drawable.ic_edit,
            title = getString(R.string.podcast_settings_tags),
            subtitle = getString(R.string.podcast_settings_coming_soon),
            enabled = false,
            onClick = ::showComingSoon
        )

        addHeader(inflater, R.string.podcast_settings_section_playback)
        addItem(
            inflater,
            icon = R.drawable.ic_playback_speed,
            title = getString(R.string.podcast_settings_playback_speed),
            subtitle = getString(R.string.podcast_settings_coming_soon),
            enabled = false,
            onClick = ::showComingSoon
        )
        addItem(
            inflater,
            icon = R.drawable.ic_skip_next,
            title = getString(R.string.podcast_settings_auto_skip),
            subtitle = getString(R.string.podcast_settings_coming_soon),
            enabled = false,
            onClick = ::showComingSoon
        )
        addItem(
            inflater,
            icon = R.drawable.ic_volume_up,
            title = getString(R.string.podcast_settings_volume_adaptation),
            subtitle = getString(R.string.podcast_settings_coming_soon),
            enabled = false,
            onClick = ::showComingSoon
        )
        addItem(
            inflater,
            icon = R.drawable.ic_play_circle,
            title = getString(R.string.podcast_settings_play_preference),
            subtitle = playPreferenceSubtitle(podcast.playPreference),
            onClick = { showPlayPreferenceDialog(podcast.playPreference) }
        )

        addHeader(inflater, R.string.podcast_settings_section_automation)
        addItem(
            inflater,
            icon = R.drawable.ic_refresh,
            title = getString(R.string.podcast_settings_keep_updated),
            subtitle = getString(R.string.podcast_settings_coming_soon),
            enabled = false,
            showSwitch = true,
            switchChecked = true,
            onClick = ::showComingSoon
        )
        addItem(
            inflater,
            icon = R.drawable.ic_videocam,
            title = getString(R.string.podcast_settings_video_link_rules),
            subtitle = getString(R.string.podcast_settings_video_link_rules_subtitle),
            onClick = {
                findNavController().navigate(
                    PodcastSettingsFragmentDirections
                        .actionPodcastSettingsFragmentToVideoLinkRulesFragment(podcast.id)
                )
            }
        )
        addItem(
            inflater,
            icon = R.drawable.ic_download,
            title = getString(R.string.podcast_settings_auto_download_video),
            subtitle = autoDownloadVideoSubtitle(podcast.autoDownloadVideo),
            onClick = { showAutoDownloadVideoDialog(podcast.autoDownloadVideo) }
        )
        addItem(
            inflater,
            icon = R.drawable.ic_play_circle,
            title = getString(R.string.podcast_settings_video_inapp_playback),
            subtitle = getString(R.string.podcast_settings_video_inapp_playback_subtitle),
            showSwitch = true,
            switchChecked = PreferenceUtil.playVideoLinksInApp,
            onSwitchToggle = { checked -> PreferenceUtil.playVideoLinksInApp = checked }
        )
        addItem(
            inflater,
            icon = R.drawable.ic_play_circle,
            title = getString(R.string.podcast_settings_play_preference_global_title),
            subtitle = getString(R.string.podcast_settings_global_play_preference_subtitle),
            onClick = { showGlobalPlayPreferenceDialog() }
        )
        addItem(
            inflater,
            icon = R.drawable.ic_download,
            title = getString(R.string.podcast_settings_auto_download_video_global_title),
            subtitle = getString(R.string.podcast_settings_global_auto_download_video_subtitle),
            showSwitch = true,
            switchChecked = PreferenceUtil.globalAutoDownloadVideo,
            onSwitchToggle = { checked -> PreferenceUtil.globalAutoDownloadVideo = checked }
        )
    }

    private fun playPreferenceSubtitle(preference: PlayPreference?) = when (preference) {
        null -> getString(R.string.podcast_settings_play_preference_use_global)
        PlayPreference.AUDIO_FIRST -> getString(R.string.podcast_settings_play_preference_audio_first)
        PlayPreference.VIDEO_FIRST -> getString(R.string.podcast_settings_play_preference_video_first)
    }

    private fun showPlayPreferenceDialog(current: PlayPreference?) {
        val options = arrayOf(
            getString(R.string.podcast_settings_play_preference_use_global),
            getString(R.string.podcast_settings_play_preference_audio_first),
            getString(R.string.podcast_settings_play_preference_video_first)
        )
        val checkedIndex = when (current) {
            null -> 0
            PlayPreference.AUDIO_FIRST -> 1
            PlayPreference.VIDEO_FIRST -> 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_settings_play_preference)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                viewModel.setPlayPreference(
                    when (which) {
                        1 -> PlayPreference.AUDIO_FIRST
                        2 -> PlayPreference.VIDEO_FIRST
                        else -> null
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGlobalPlayPreferenceDialog() {
        val options = arrayOf(
            getString(R.string.podcast_settings_play_preference_audio_first),
            getString(R.string.podcast_settings_play_preference_video_first)
        )
        val checkedIndex = if (PreferenceUtil.globalPlayPreference == PlayPreference.VIDEO_FIRST) 1 else 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_settings_play_preference)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                PreferenceUtil.globalPlayPreference =
                    if (which == 1) PlayPreference.VIDEO_FIRST else PlayPreference.AUDIO_FIRST
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun autoDownloadVideoSubtitle(autoDownload: Boolean?) = when (autoDownload) {
        null -> getString(R.string.podcast_settings_auto_download_video_use_global)
        true -> getString(R.string.podcast_settings_auto_download_video_on)
        false -> getString(R.string.podcast_settings_auto_download_video_off)
    }

    private fun showAutoDownloadVideoDialog(current: Boolean?) {
        val options = arrayOf(
            getString(R.string.podcast_settings_auto_download_video_use_global),
            getString(R.string.podcast_settings_auto_download_video_on),
            getString(R.string.podcast_settings_auto_download_video_off)
        )
        val checkedIndex = when (current) {
            null -> 0
            true -> 1
            false -> 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_settings_auto_download_video)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                viewModel.setAutoDownloadVideo(
                    when (which) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showComingSoon() {
        requireContext().showToast(getString(R.string.podcast_settings_coming_soon))
    }

    private fun addHeader(inflater: LayoutInflater, titleRes: Int) {
        val header = ItemPodcastSettingHeaderBinding.inflate(inflater, binding.settingsContainer, false)
        header.root.setText(titleRes)
        binding.settingsContainer.addView(header.root)
    }

    private fun addItem(
        inflater: LayoutInflater,
        icon: Int,
        title: String,
        subtitle: String? = null,
        enabled: Boolean = true,
        showSwitch: Boolean = false,
        switchChecked: Boolean = false,
        onClick: (() -> Unit)? = null,
        onSwitchToggle: ((Boolean) -> Unit)? = null
    ) {
        val item = ItemPodcastSettingBinding.inflate(inflater, binding.settingsContainer, false)
        item.settingIcon.setImageResource(icon)
        item.settingTitle.text = title
        item.settingSubtitle.isVisible = !subtitle.isNullOrBlank()
        item.settingSubtitle.text = subtitle
        item.settingRow.alpha = if (enabled || onClick != null || onSwitchToggle != null) 1f else 0.5f
        item.settingSwitch.isVisible = showSwitch
        item.settingSwitch.isChecked = switchChecked
        when {
            onSwitchToggle != null -> {
                item.settingSwitch.setOnCheckedChangeListener { _, checked -> onSwitchToggle(checked) }
                item.settingRow.setOnClickListener { item.settingSwitch.toggle() }
            }

            onClick != null -> item.settingRow.setOnClickListener { onClick() }
            else -> {
                item.settingRow.isClickable = false
                item.settingRow.isFocusable = false
            }
        }
        binding.settingsContainer.addView(item.root)
    }

    private fun showFeedUrlDialog(podcast: PodcastEntity) {
        val input = EditText(requireContext()).apply {
            setText(podcast.feedUrl)
            setHint(R.string.podcast_feed_url_hint)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_settings_feed_url)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newUrl = input.text?.toString()?.trim()
                if (!newUrl.isNullOrBlank() && newUrl != podcast.feedUrl) {
                    viewModel.setFeedUrl(newUrl)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
