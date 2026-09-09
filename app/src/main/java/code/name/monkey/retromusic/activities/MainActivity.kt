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
package code.name.monkey.retromusic.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.contains
import androidx.navigation.ui.setupWithNavController
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.base.AbsCastActivity
import code.name.monkey.retromusic.extensions.*
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.SearchQueryHelper.getSongs
import code.name.monkey.retromusic.interfaces.IScrollHelper
import code.name.monkey.retromusic.model.CategoryInfo
import code.name.monkey.retromusic.model.PodcastCategoryInfo
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.PlaylistSongsLoader
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.AppRater
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.logE
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MainActivity : AbsCastActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val EXPAND_PANEL = "expand_panel"
    }

    /** Every destination id that belongs to the Podcasts world's bottom nav -- see
     * PodcastCategoryInfo.Category. Whichever of these is currently visible/reachable, landing
     * on it should look and behave like the Podcasts world (drawer checked state, bottom-nav
     * tabs), the same way the Music tab ids are matched below. */
    private val podcastWorldDestinationIds: Set<Int> by lazy {
        PodcastCategoryInfo.Category.values().map { it.id }.toSet()
    }

    private val musicWorldDestinationIds: Set<Int> = setOf(
        R.id.action_home, R.id.action_song, R.id.action_album, R.id.action_artist,
        R.id.action_folder, R.id.action_playlist, R.id.action_genre, R.id.action_search
    )

    /** Only these ids are legitimate `PreferenceUtil.lastTab` values -- a raw resource int
     * persisted across builds can drift onto an unrelated (and possibly argument-requiring)
     * destination once new resources shift Android's auto-assigned ids, which crashed the app
     * on startup once already (lastTab silently became podcastDetailsFragment's id). */
    private val topLevelDestinationIds: Set<Int> by lazy {
        musicWorldDestinationIds + podcastWorldDestinationIds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTaskDescriptionColorAuto()
        hideStatusBar()
        updateTabs()
        AppRater.appLaunched(this)

        setupNavigationController()
        setupWorldDrawer()

        WhatsNewFragment.showChangeLog(this)
    }

    /** The "world switcher" drawer -- for now just Music vs. Podcasts, reusing the existing
     * bottom-nav tabs for Music and the existing podcasts_fragment destination as-is. Only
     * openable from the top-level tabs (see the destination-changed listener below); locked
     * closed elsewhere so it doesn't fight detail screens' back-arrow/swipe-back gestures. */
    private fun setupWorldDrawer() {
        worldDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_world_music -> findNavController(R.id.fragment_container).navigate(R.id.action_home)
                R.id.nav_world_podcasts -> {
                    val firstVisible = PreferenceUtil.podcastCategory.firstOrNull { it.visible }
                        ?.category?.id ?: R.id.podcasts_fragment
                    findNavController(R.id.fragment_container).navigate(firstVisible)
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    private fun setupNavigationController() {
        val navController = findNavController(R.id.fragment_container)
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.main_graph)

        val categoryInfo: CategoryInfo = PreferenceUtil.libraryCategory.first { it.visible }
        if (categoryInfo.visible) {
            val lastTabIsValid = navGraph.contains(PreferenceUtil.lastTab) &&
                PreferenceUtil.lastTab in topLevelDestinationIds
            if (!lastTabIsValid) PreferenceUtil.lastTab = categoryInfo.category.id
            navGraph.setStartDestination(
                if (PreferenceUtil.rememberLastTab) {
                    PreferenceUtil.lastTab.let {
                        if (it == 0) {
                            categoryInfo.category.id
                        } else {
                            it
                        }
                    }
                } else categoryInfo.category.id
            )
        }
        navController.graph = navGraph
        navigationView.setupWithNavController(navController)
        // Scroll Fragment to top
        navigationView.setOnItemReselectedListener {
            currentFragment(R.id.fragment_container).apply {
                if (this is IScrollHelper) {
                    scrollToTop()
                }
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == navGraph.startDestinationId) {
                currentFragment(R.id.fragment_container)?.enterTransition = null
            }
            when (destination.id) {
                in musicWorldDestinationIds -> {
                    // Save the last tab
                    if (PreferenceUtil.rememberLastTab) {
                        saveTab(destination.id)
                    }
                    // Show Bottom Navigation Bar
                    setBottomNavVisibility(visible = true, animate = true)
                    // Only the top-level tabs get the world-switcher drawer -- detail screens
                    // keep their back-arrow/swipe-back untouched.
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    worldDrawer.setCheckedItem(R.id.nav_world_music)
                    // The bottom nav may still be showing the Podcasts world's tabs (e.g.
                    // returning here via the drawer) -- rebuild it from the Music tab prefs.
                    updateTabs()
                }
                in podcastWorldDestinationIds -> {
                    if (PreferenceUtil.rememberLastTab) {
                        saveTab(destination.id)
                    }
                    setBottomNavVisibility(visible = true, animate = true)
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    worldDrawer.setCheckedItem(R.id.nav_world_podcasts)
                    // Same idea as updateTabs() above -- rebuild in case the bottom nav is still
                    // showing Music tabs, or a category got hidden/reordered since last shown.
                    showPodcastsWorldTabs()
                }
                R.id.playing_queue_fragment -> {
                    setBottomNavVisibility(visible = false, hideBottomSheet = true)
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                else -> {
                    setBottomNavVisibility(
                        visible = false,
                        animate = true
                    ) // Hide Bottom Navigation Bar
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
        }
    }

    /** The Podcasts world's own bottom-nav tabs, customizable the same way as the Music tabs
     * (see PersonalizeSettingsFragment's Music equivalent) via the Podcasts settings screen. */
    private fun showPodcastsWorldTabs() {
        navigationView.menu.clear()
        for (tab in PreferenceUtil.podcastCategory) {
            if (tab.visible) {
                val category = tab.category
                navigationView.menu.add(0, category.id, 0, category.stringRes)
                    .setIcon(category.icon)
            }
        }
    }

    private fun saveTab(id: Int) {
        // Podcasts-world tabs are a separate preference list (PreferenceUtil.podcastCategory),
        // not part of the Music libraryCategory list saveTab() otherwise checks against.
        val isMusicTabVisible =
            PreferenceUtil.libraryCategory.firstOrNull { it.category.id == id }?.visible == true
        val isPodcastTabVisible = id in podcastWorldDestinationIds
        if (isMusicTabVisible || isPodcastTabVisible) {
            PreferenceUtil.lastTab = id
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val expand = intent?.extra<Boolean>(EXPAND_PANEL)?.value ?: false
        if (expand && PreferenceUtil.isExpandPanel) {
            fromNotification = true
            slidingPanel.bringToFront()
            expandPanel()
            intent?.removeExtra(EXPAND_PANEL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        intent ?: return
        handlePlaybackIntent(intent)
    }

    @Suppress("deprecation")
    private fun handlePlaybackIntent(intent: Intent) {
        lifecycleScope.launch(IO) {
            val uri: Uri? = intent.data
            val mimeType: String? = intent.type
            var handled = false
            if (intent.action != null &&
                intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
            ) {
                val songs: List<Song> = getSongs(intent.extras!!)
                if (MusicPlayerRemote.shuffleMode == MusicService.SHUFFLE_MODE_SHUFFLE) {
                    MusicPlayerRemote.openAndShuffleQueue(songs, true)
                } else {
                    MusicPlayerRemote.openQueue(songs, 0, true)
                }
                handled = true
            }
            if (uri != null && uri.toString().isNotEmpty()) {
                MusicPlayerRemote.playFromUri(this@MainActivity, uri)
                handled = true
            } else if (MediaStore.Audio.Playlists.CONTENT_TYPE == mimeType) {
                val id = parseLongFromIntent(intent, "playlistId", "playlist")
                if (id >= 0L) {
                    val position: Int = intent.getIntExtra("position", 0)
                    val songs: List<Song> = PlaylistSongsLoader.getPlaylistSongList(get(), id)
                    MusicPlayerRemote.openQueue(songs, position, true)
                    handled = true
                }
            } else if (MediaStore.Audio.Albums.CONTENT_TYPE == mimeType) {
                val id = parseLongFromIntent(intent, "albumId", "album")
                if (id >= 0L) {
                    val position: Int = intent.getIntExtra("position", 0)
                    val songs = libraryViewModel.albumById(id).songs
                    MusicPlayerRemote.openQueue(
                        songs,
                        position,
                        true
                    )
                    handled = true
                }
            } else if (MediaStore.Audio.Artists.CONTENT_TYPE == mimeType) {
                val id = parseLongFromIntent(intent, "artistId", "artist")
                if (id >= 0L) {
                    val position: Int = intent.getIntExtra("position", 0)
                    val songs: List<Song> = libraryViewModel.artistById(id).songs
                    MusicPlayerRemote.openQueue(
                        songs,
                        position,
                        true
                    )
                    handled = true
                }
            }
            if (handled) {
                setIntent(Intent())
            }
        }
    }

    private fun parseLongFromIntent(
        intent: Intent,
        longKey: String,
        stringKey: String,
    ): Long {
        var id = intent.getLongExtra(longKey, -1)
        if (id < 0) {
            val idString = intent.getStringExtra(stringKey)
            if (idString != null) {
                try {
                    id = idString.toLong()
                } catch (e: NumberFormatException) {
                    logE(e)
                }
            }
        }
        return id
    }
}
