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
package code.name.monkey.retromusic.fragments.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.launch
import code.name.monkey.appthemehelper.util.ColorUtil
import code.name.monkey.appthemehelper.util.MaterialValueHelper
import code.name.monkey.retromusic.LYRICS_TYPE
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.SHOW_LYRICS
import code.name.monkey.retromusic.adapter.album.AlbumCoverPagerAdapter
import code.name.monkey.retromusic.adapter.album.AlbumCoverPagerAdapter.AlbumCoverFragment
import code.name.monkey.retromusic.databinding.FragmentPlayerAlbumCoverBinding
import code.name.monkey.retromusic.extensions.isColorLight
import code.name.monkey.retromusic.extensions.surfaceColor
import code.name.monkey.retromusic.fragments.NowPlayingScreen.*
import code.name.monkey.retromusic.fragments.base.AbsMusicServiceFragment
import code.name.monkey.retromusic.fragments.base.goToLyrics
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.lyrics.CoverLrcView
import code.name.monkey.retromusic.model.lyrics.Lyrics
import code.name.monkey.retromusic.podcast.PodcastVideoFullscreen
import code.name.monkey.retromusic.podcast.PodcastVideoFullscreenDialogFragment
import code.name.monkey.retromusic.podcast.PodcastVideoToggle
import code.name.monkey.retromusic.podcast.VideoAspectMode
import code.name.monkey.retromusic.podcast.applyVideoAspect as applySharedVideoAspect
import code.name.monkey.retromusic.transform.CarousalPagerTransformer
import code.name.monkey.retromusic.transform.ParallaxPagerTransformer
import code.name.monkey.retromusic.util.CoverLyricsType
import code.name.monkey.retromusic.util.LyricUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerAlbumCoverFragment : AbsMusicServiceFragment(R.layout.fragment_player_album_cover),
    ViewPager.OnPageChangeListener, MusicProgressViewUpdateHelper.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentPlayerAlbumCoverBinding? = null
    private val binding get() = _binding!!
    private var callbacks: Callbacks? = null
    private var currentPosition: Int = 0
    val viewPager get() = binding.viewPager

    private val colorReceiver = object : AlbumCoverFragment.ColorReceiver {
        override fun onColorReady(color: MediaNotificationProcessor, request: Int) {
            if (currentPosition == request) {
                notifyColorChange(color)
            }
        }
    }
    private var progressViewUpdateHelper: MusicProgressViewUpdateHelper? = null

    private val lrcView: CoverLrcView get() = binding.lyricsView

    var lyrics: Lyrics? = null

    // Tracks whether the video source is actually attached right now, distinct from
    // PodcastVideoToggle.isActive (the user's on/off intent) -- lets applyVideoToggleState be
    // called repeatedly and idempotently (e.g. every onResume, to fight showLyrics() clobbering
    // viewPager visibility) without re-swapping the media source and causing a visible glitch
    // when nothing actually needs to change.
    private var isVideoAttached = false

    // Registered once and never removed -- see the surfaceCreated/surfaceDestroyed callbacks
    // below. A previous version of this feature called `holder.removeCallback(this)` from
    // surfaceDestroyed, which permanently unregistered the callback after the very first
    // toggle-off, so later toggles silently did nothing (symptom: a stuck black surface after a
    // couple of on/off cycles).
    private val videoSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            MusicPlayerRemote.setVideoSurface(holder.surface)
            MusicPlayerRemote.setVideoSizeListener(::applyVideoAspect)
            MusicPlayerRemote.toggleEpisodeVideo(true)
            isVideoAttached = true
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // Defensive cleanup only -- turning video off is driven explicitly by
            // applyVideoToggleState(false), not by this (SurfaceView's visibility-vs-surface-
            // lifecycle timing isn't reliable enough to drive the actual media swap from here).
            MusicPlayerRemote.setVideoSizeListener(null)
            MusicPlayerRemote.setVideoSurface(null)
            isVideoAttached = false
        }
    }

    /** Resizes the video surface to fit the available width, letterboxed to the video's own
     * aspect ratio (podcast thumbnails tend to be square/portrait, so "fit width" reads better
     * as a default than stretching to fill the whole square cover area). Always [VideoAspectMode.FIT]
     * here -- unlike the fullscreen dialog, there's no crop toggle inline, matching "consistent
     * with where the album art normally sits." */
    private fun applyVideoAspect(videoWidth: Int, videoHeight: Int) {
        val binding = _binding ?: return
        applySharedVideoAspect(binding.videoSurfaceView, videoWidth, videoHeight, VideoAspectMode.FIT)
    }

    private fun applyVideoToggleState(active: Boolean) {
        val binding = _binding ?: return
        binding.videoSurfaceView.isVisible = active
        binding.viewPager.isVisible = !active
        binding.videoFullscreenButton.isVisible = active

        // cover_lyrics/fading_edge_layout sit above the video surface in z-order (added later in
        // the XML) and can end up covering it -- e.g. the fullscreen button "disappearing" once
        // the async lyrics lookup in updateLyrics() finished and made one of them visible -- and
        // CoverLrcView's own click listener (goToLyrics) was swallowing taps meant for the video,
        // which is why tapping the video surface unexpectedly opened lyrics. Tapping the inline
        // video is meant to do nothing (fullscreen has its own dedicated button); force these off
        // while video is active, and let the normal lyrics logic decide again once it's not.
        if (active) {
            binding.coverLyrics.isVisible = false
            binding.fadingEdgeLayout.isVisible = false
        } else {
            maybeInitLyrics()
        }

        // While the fullscreen dialog is up, the video surface is targeted there instead (see
        // PodcastVideoFullscreenDialogFragment) -- don't fight it for the surface reference.
        if (PodcastVideoFullscreen.isFullscreen.value) return

        if (active) {
            if (isVideoAttached) return
            val surface = binding.videoSurfaceView.holder.surface
            if (surface != null && surface.isValid) {
                // Surface already exists from a previous toggle-on this fragment lifetime --
                // surfaceCreated won't fire again for it, so attach directly.
                MusicPlayerRemote.setVideoSurface(surface)
                MusicPlayerRemote.setVideoSizeListener(::applyVideoAspect)
                MusicPlayerRemote.toggleEpisodeVideo(true)
                isVideoAttached = true
            }
            // Otherwise videoSurfaceCallback.surfaceCreated (registered once, permanently) does
            // the same attach sequence once the surface actually exists.
        } else {
            if (!isVideoAttached) return
            MusicPlayerRemote.setVideoSizeListener(null)
            MusicPlayerRemote.setVideoSurface(null)
            isVideoAttached = false
            MusicPlayerRemote.toggleEpisodeVideo(false)
        }
    }

    private fun openFullscreenVideo() {
        if (parentFragmentManager.findFragmentByTag(PodcastVideoFullscreenDialogFragment.TAG) != null) return
        PodcastVideoFullscreenDialogFragment().show(parentFragmentManager, PodcastVideoFullscreenDialogFragment.TAG)
    }

    /** Checked from onResume rather than onConfigurationChanged -- the app doesn't declare
     * `configChanges` for orientation (removing that was itself a fix: it broke the bottom
     * nav's landscape-vs-portrait layout swap, which depends on the Activity actually
     * recreating on rotation), so the Activity/fragment gets recreated on rotation instead of
     * receiving a configuration-changed callback. onResume runs again after that recreation,
     * which is exactly the point recreation finishes and this fragment is back in the tree. */
    private fun maybeAutoFullscreenForLandscape() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            PodcastVideoToggle.isActive.value &&
            !PodcastVideoFullscreen.isFullscreen.value
        ) {
            openFullscreenVideo()
        }
    }

    fun removeSlideEffect() {
        val transformer = ParallaxPagerTransformer(R.id.player_image)
        transformer.setSpeed(0.3f)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewPager.setPageTransformer(false, transformer)
            }
        }
    }

    private fun updateLyrics() {
        val song = MusicPlayerRemote.currentSong
        lifecycleScope.launch(Dispatchers.IO) {
            val lrcFile = LyricUtil.getSyncedLyricsFile(song)
            if (lrcFile != null) {
                binding.lyricsView.loadLrc(lrcFile)
            } else {
                val embeddedLyrics = LyricUtil.getEmbeddedSyncedLyrics(song.data)
                if (embeddedLyrics != null) {
                    binding.lyricsView.loadLrc(embeddedLyrics)
                } else {
                    withContext(Dispatchers.Main) {
                        binding.lyricsView.reset()
                        binding.lyricsView.setLabel(context?.getString(R.string.no_lyrics_found))
                    }
                }
            }
        }

    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.lyricsView.updateTime(progress.toLong())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerAlbumCoverBinding.bind(view)
        setupViewPager()
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this, 500, 1000)
        maybeInitLyrics()
        lrcView.apply {
            setDraggable(true) { time ->
                MusicPlayerRemote.seekTo(time.toInt())
                MusicPlayerRemote.resumePlaying()
                true
            }
            setOnClickListener {
                goToLyrics(requireActivity())
            }
        }

        binding.videoSurfaceView.holder.addCallback(videoSurfaceCallback)
        binding.videoFullscreenButton.setOnClickListener { openFullscreenVideo() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PodcastVideoToggle.isActive.collect { active -> applyVideoToggleState(active) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasFullscreen = false
                PodcastVideoFullscreen.isFullscreen.collect { fullscreen ->
                    if (wasFullscreen && !fullscreen && PodcastVideoToggle.isActive.value) {
                        // The fullscreen dialog held the video surface reference while it was
                        // up (see applyVideoToggleState's early return above) -- isVideoAttached
                        // is stale here, so force a fresh reattach of the still-valid inline
                        // surface rather than trusting that flag.
                        isVideoAttached = false
                        applyVideoToggleState(true)
                    }
                    wasFullscreen = fullscreen
                }
            }
        }
        // Auto-fullscreen on rotate to landscape, only while video is actually active -- see
        // onConfigurationChanged below.
    }

    private fun setupViewPager() {
        binding.viewPager.addOnPageChangeListener(this)
        val nps = PreferenceUtil.nowPlayingScreen

        if (nps == Full || nps == Classic || nps == Fit || nps == Gradient) {
            binding.viewPager.offscreenPageLimit = 2
        } else if (PreferenceUtil.isCarouselEffect) {
            val metrics = resources.displayMetrics
            val ratio = metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()
            binding.viewPager.clipToPadding = false
            val padding =
                if (ratio >= 1.777f) {
                    40
                } else {
                    100
                }
            binding.viewPager.setPadding(padding, 0, padding, 0)
            binding.viewPager.pageMargin = 0
            binding.viewPager.setPageTransformer(false, CarousalPagerTransformer(requireContext()))
        } else {
            binding.viewPager.offscreenPageLimit = 2
            binding.viewPager.setPageTransformer(
                true,
                PreferenceUtil.albumCoverTransform
            )
        }
    }

    override fun onResume() {
        super.onResume()
        maybeInitLyrics()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
        // maybeInitLyrics() -> showLyrics() unconditionally sets viewPager visible again, which
        // clobbers video mode's hidden state (symptom: album art showing behind the video again
        // after backgrounding the app and reopening it, since onResume runs every time the app
        // comes back to the foreground). Reassert whichever state is actually current afterward.
        applyVideoToggleState(PodcastVideoToggle.isActive.value)
        maybeAutoFullscreenForLandscape()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        binding.viewPager.removeOnPageChangeListener(this)
        binding.videoSurfaceView.holder.removeCallback(videoSurfaceCallback)
        if (PodcastVideoToggle.isActive.value) {
            MusicPlayerRemote.setVideoSizeListener(null)
            MusicPlayerRemote.setVideoSurface(null)
        }
        progressViewUpdateHelper?.stop()
        _binding = null
    }

    override fun onServiceConnected() {
        updatePlayingQueue()
        updateLyrics()
    }

    override fun onPlayingMetaChanged() {
        if (viewPager.currentItem != MusicPlayerRemote.position) {
            viewPager.setCurrentItem(MusicPlayerRemote.position, true)
        }
        updateLyrics()
        // Reset per episode rather than carrying an on/off state across episode changes -- an
        // episode with no video source obviously can't stay in video mode, and defaulting every
        // new episode to audio-first (even a video-capable one) matches pre-toggle behavior.
        if (PodcastVideoToggle.isActive.value) {
            PodcastVideoToggle.setActive(false)
        }
    }

    override fun onQueueChanged() {
        updatePlayingQueue()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            SHOW_LYRICS -> {
                if (PreferenceUtil.showLyrics) {
                    maybeInitLyrics()
                } else {
                    showLyrics(false)
                    progressViewUpdateHelper?.stop()
                }
            }
            LYRICS_TYPE -> {
                maybeInitLyrics()
            }
        }
    }

    private fun setLRCViewColors(@ColorInt primaryColor: Int, @ColorInt secondaryColor: Int) {
        lrcView.apply {
            setCurrentColor(primaryColor)
            setTimeTextColor(primaryColor)
            setTimelineColor(primaryColor)
            setNormalColor(secondaryColor)
            setTimelineTextColor(primaryColor)
        }
    }

    private fun showLyrics(visible: Boolean) {
        binding.coverLyrics.isVisible = false
        binding.lyricsView.isVisible = false
        binding.viewPager.isVisible = true
        val lyrics: View = if (PreferenceUtil.lyricsType == CoverLyricsType.REPLACE_COVER) {
            ObjectAnimator.ofFloat(viewPager, View.ALPHA, if (visible) 0F else 1F).start()
            lrcView
        } else {
            ObjectAnimator.ofFloat(viewPager, View.ALPHA, 1F).start()
            binding.coverLyrics
        }
        ObjectAnimator.ofFloat(lyrics, View.ALPHA, if (visible) 1F else 0F).apply {
            doOnEnd {
                lyrics.isVisible = visible
            }
            start()
        }
    }

    private fun maybeInitLyrics() {
        val nps = PreferenceUtil.nowPlayingScreen
        // Don't show lyrics container for below conditions
        if (lyricViewNpsList.contains(nps) && PreferenceUtil.showLyrics) {
            showLyrics(true)
            if (PreferenceUtil.lyricsType == CoverLyricsType.REPLACE_COVER) {
                progressViewUpdateHelper?.start()
            }
        } else {
            showLyrics(false)
            progressViewUpdateHelper?.stop()
        }
    }

    private fun updatePlayingQueue() {
        val adapter = binding.viewPager.adapter
        if (adapter is AlbumCoverPagerAdapter) {
            adapter.updateData(MusicPlayerRemote.playingQueue)
        } else {
            binding.viewPager.adapter = AlbumCoverPagerAdapter(
                parentFragmentManager,
                MusicPlayerRemote.playingQueue
            )

        }
        binding.viewPager.setCurrentItem(MusicPlayerRemote.position, true)
        onPageSelected(MusicPlayerRemote.position)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        currentPosition = position
        if (binding.viewPager.adapter != null) {
            (binding.viewPager.adapter as AlbumCoverPagerAdapter).receiveColor(
                colorReceiver,
                position
            )
        }
        if (position != MusicPlayerRemote.position) {
            MusicPlayerRemote.playSongAt(position)
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
    }


    private fun notifyColorChange(color: MediaNotificationProcessor) {
        callbacks?.onColorChanged(color)
        val primaryColor = MaterialValueHelper.getPrimaryTextColor(
            requireContext(),
            surfaceColor().isColorLight
        )
        val secondaryColor = MaterialValueHelper.getSecondaryDisabledTextColor(
            requireContext(),
            surfaceColor().isColorLight
        )

        when (PreferenceUtil.nowPlayingScreen) {
            Flat, Normal, Material -> if (PreferenceUtil.isAdaptiveColor) {
                setLRCViewColors(color.primaryTextColor, color.secondaryTextColor)
            } else {
                setLRCViewColors(primaryColor, secondaryColor)
            }
            Color, Classic -> setLRCViewColors(color.primaryTextColor, color.secondaryTextColor)
            Blur -> setLRCViewColors(android.graphics.Color.WHITE, ColorUtil.withAlpha(android.graphics.Color.WHITE, 0.5f))
            else -> setLRCViewColors(primaryColor, secondaryColor)
        }
    }

    fun setCallbacks(listener: Callbacks) {
        callbacks = listener
    }

    interface Callbacks {

        fun onColorChanged(color: MediaNotificationProcessor)

        fun onFavoriteToggled()
    }

    companion object {
        val TAG: String = PlayerAlbumCoverFragment::class.java.simpleName
    }

    private val lyricViewNpsList =
        listOf(Blur, Classic, Color, Flat, Material, MD3, Normal, Plain, Simple)
}
