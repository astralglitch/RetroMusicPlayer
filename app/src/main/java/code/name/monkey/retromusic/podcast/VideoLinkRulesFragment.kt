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
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentVideoLinkRulesBinding
import code.name.monkey.retromusic.databinding.ItemPodcastSettingBinding
import code.name.monkey.retromusic.db.VideoLinkMatchType
import code.name.monkey.retromusic.db.VideoLinkRuleEntity
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * Lists, adds, toggles, and deletes a podcast's video-link auto-detection rules -- reached from
 * PodcastSettingsFragment's "Video link rules" row. Reuses `ItemPodcastSettingBinding` for each
 * rule row (title = preset/pattern, subtitle = the raw pattern, switch = enabled), the same
 * lightweight "rebuild views on each update" approach `PodcastSettingsFragment` already uses --
 * a rule list is always small, so a full RecyclerView/DiffUtil adapter isn't warranted.
 */
class VideoLinkRulesFragment : AbsMainActivityFragment(R.layout.fragment_video_link_rules) {

    private var _binding: FragmentVideoLinkRulesBinding? = null
    private val binding get() = _binding!!

    private val arguments by navArgs<VideoLinkRulesFragmentArgs>()
    private val viewModel by viewModel<VideoLinkRulesViewModel> {
        parametersOf(arguments.extraPodcastId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVideoLinkRulesBinding.bind(view)
        mainActivity.setSupportActionBar(binding.toolbar)
        binding.toolbar.title = " "
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.addRuleFab.setOnClickListener { showAddRuleDialog() }
        // Otherwise the FAB renders underneath the persistent mini-player bar -- same fix
        // AbsRecyclerViewFragment/SearchFragment/UserInfoFragment apply to their own FABs, via
        // the shared animated margin LiveData AbsSlidingMusicPanelActivity drives as the panel
        // slides (see docs/PODCAST_DESIGN.md note on this class of bug).
        val baseFabMarginPx = (16 * resources.displayMetrics.density).toInt()
        libraryViewModel.getFabMargin().observe(viewLifecycleOwner) { margin ->
            binding.addRuleFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = margin + baseFabMarginPx
            }
        }

        viewModel.podcast.observe(viewLifecycleOwner) { podcast ->
            binding.rulesSubtitle.text = podcast?.title
        }
        viewModel.rules.observe(viewLifecycleOwner) { rules ->
            buildRulesList(rules)
        }
    }

    private fun buildRulesList(rules: List<VideoLinkRuleEntity>) {
        binding.rulesEmptyState.isVisible = rules.isEmpty()
        binding.rulesContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        rules.forEach { rule ->
            val item = ItemPodcastSettingBinding.inflate(inflater, binding.rulesContainer, false)
            item.settingIcon.setImageResource(R.drawable.ic_videocam)
            item.settingTitle.text = when (rule.matchType) {
                VideoLinkMatchType.PRESET -> VideoLinkPresets.byId(rule.pattern)?.displayName ?: rule.pattern
                VideoLinkMatchType.REGEX -> getString(R.string.podcast_video_link_rule_custom)
            }
            item.settingSubtitle.isVisible = rule.matchType == VideoLinkMatchType.REGEX
            item.settingSubtitle.text = rule.pattern
            item.settingSwitch.isVisible = true
            item.settingSwitch.isChecked = rule.enabled
            item.settingSwitch.setOnCheckedChangeListener { _, checked ->
                viewModel.setRuleEnabled(rule, checked)
            }
            item.settingRow.setOnClickListener { item.settingSwitch.toggle() }
            item.settingRow.setOnLongClickListener {
                confirmDeleteRule(rule)
                true
            }
            binding.rulesContainer.addView(item.root)
        }
    }

    private fun confirmDeleteRule(rule: VideoLinkRuleEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_video_link_rule_delete)
            .setPositiveButton(R.string.podcast_video_link_rule_delete) { _, _ -> viewModel.deleteRule(rule) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddRuleDialog() {
        val options = VideoLinkPresets.all.map { it.displayName } +
            getString(R.string.podcast_video_link_rule_custom)
        val spinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(spinner)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_video_link_rule_add)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedIndex = spinner.selectedItemPosition
                val preset = VideoLinkPresets.all.getOrNull(selectedIndex)
                if (preset != null) {
                    viewModel.addPresetRule(preset.id)
                } else {
                    showCustomRuleDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCustomRuleDialog() {
        val input = EditText(requireContext()).apply {
            setHint(R.string.podcast_video_link_rule_custom_hint)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.podcast_video_link_rule_custom)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val regex = input.text?.toString()?.trim()
                if (!regex.isNullOrBlank()) {
                    try {
                        Regex(regex)
                        viewModel.addCustomRule(regex)
                    } catch (e: Exception) {
                        requireContext().showToast(getString(R.string.podcast_video_link_rule_invalid_regex))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_video_link_rules, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_reapply_rules) {
            viewModel.reapplyRules { updatedCount ->
                requireContext().showToast(
                    resources.getQuantityString(
                        R.plurals.podcast_video_link_rules_reapplied,
                        updatedCount,
                        updatedCount
                    )
                )
            }
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
