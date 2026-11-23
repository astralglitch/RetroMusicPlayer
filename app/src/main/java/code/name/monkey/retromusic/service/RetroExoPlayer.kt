package code.name.monkey.retromusic.service

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.extensions.showToast
import code.name.monkey.retromusic.extensions.uri
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.service.playback.Playback.PlaybackCallbacks
import code.name.monkey.retromusic.util.PreferenceUtil.playbackPitch
import code.name.monkey.retromusic.util.PreferenceUtil.playbackSpeed
import code.name.monkey.retromusic.util.logE

class RetroExoPlayer(context: Context) : AudioManagerPlayback(context), Player.Listener {
    // Fix provided by : Zak (github: @arrhenius975, mail: zakariatalukdar123@gmail.com)
    // Definition and fix: Fix USB-C/Bluetooth audio routing by setting attributes at init
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private var player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(audioAttributes, false)
        .build()
    override var callbacks: PlaybackCallbacks? = null
    private var videoSizeListener: ((width: Int, height: Int) -> Unit)? = null

    /**
     * @return True if the player is ready to go, false otherwise
     */
    override var isInitialized = false
        private set

    init {
        player.setWakeMode(C.WAKE_MODE_LOCAL)
    }

    /**
     * @param song The song object you want to play
     * @return True if the `player` has been prepared and is ready to play, false otherwise
     */
    override fun setDataSource(
        song: Song,
        force: Boolean,
        completion: (success: Boolean) -> Unit,
    ) {
        isInitialized = false
        val mediaItem = MediaItem.fromUri(song.uri)
        try {
            Handler(Looper.getMainLooper()).post {
                player.setMediaItem(mediaItem)
                player.setAudioAttributes(audioAttributes, false)
                player.playbackParameters = PlaybackParameters(playbackSpeed, playbackPitch)

                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            player.removeListener(this)
                            isInitialized = true
                            completion(true)
                        }
                    }
                })
                player.addListener(this)
                player.prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            completion(false)
        }
    }

    /**
     * Queues [path] right after the current item so ExoPlayer transitions into it without
     * tearing down the audio pipeline (gapless). Replaces any previously queued next item; null
     * clears it, so playback ends (STATE_ENDED) after the current item.
     *
     * @param path The path of the file, or the http/rtsp URL of the stream you want to play
     */
    override fun setNextDataSource(path: Uri?) {
        Handler(Looper.getMainLooper()).post {
            val queuedAfterCurrent = player.mediaItemCount - (player.currentMediaItemIndex + 1)
            if (queuedAfterCurrent > 0) {
                player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
            }
            if (path != null && player.mediaItemCount > 0) {
                player.addMediaItem(MediaItem.fromUri(path))
            }
        }
    }

    /**
     * Starts or resumes playback.
     */
    override fun start(): Boolean {
        super.start()
        return try {
            player.play()
            true
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Resets the MediaPlayer to its uninitialized state.
     */
    override fun stop() {
        super.stop()
        player.stop()
        isInitialized = false
    }

    /**
     * Releases resources associated with this MediaPlayer object.
     */
    override fun release() {
        stop()
        player.release()
    }

    /**
     * Pauses playback. Call start() to resume.
     */
    override fun pause(): Boolean {
        super.pause()
        return try {
            player.pause()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * Checks whether the MultiPlayer is playing.
     */
    override val isPlaying: Boolean
        get() = isInitialized && (player.isPlaying || player.playbackState == Player.STATE_ENDED)

    /**
     * Gets the duration of the file.
     *
     * @return The duration in milliseconds
     */
    override fun duration(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            player.duration.toInt()
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Gets the current playback position.
     *
     * @return The current position in milliseconds
     */
    override fun position(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            player.currentPosition.toInt()
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Gets the current playback position.
     *
     * @param whereto The offset in milliseconds from the start to seek to
     * @return The offset in milliseconds from the start to seek to
     */
    override fun seek(whereto: Int, force: Boolean): Int {
        return try {
            player.seekTo(whereto.toLong())
            whereto
        } catch (e: Exception) {
            -1
        }
    }

    override fun setVolume(vol: Float): Boolean {
        return try {
            player.volume = vol
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId The audio session ID
     */
    @OptIn(UnstableApi::class)
    override fun setAudioSessionId(sessionId: Int): Boolean {
        return try {
            player.audioSessionId = sessionId
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the audio session ID.
     *
     * @return The current audio session ID.
     */
    override val audioSessionId: Int
        @OptIn(UnstableApi::class)
        get() = player.audioSessionId

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            callbacks?.onTrackEnded()
        } else {
            callbacks?.onPlayStateChanged()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        logE(error)
        isInitialized = false
        player.release()
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, false)
            .build()
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        context.showToast(R.string.unplayable_file)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            callbacks?.onTrackWentToNext()
            return
        }
    }

    override fun setCrossFadeDuration(duration: Int) {}

    override fun setVideoSurface(surface: Surface?) {
        Handler(Looper.getMainLooper()).post {
            player.setVideoSurface(surface)
        }
    }

    override fun swapMediaSource(uri: Uri, resumePositionMs: Long, isHls: Boolean) {
        Handler(Looper.getMainLooper()).post {
            val wasPlaying = player.isPlaying
            if (isHls) {
                val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                val mediaSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
                player.setMediaSource(mediaSource)
            } else {
                player.setMediaItem(MediaItem.fromUri(uri))
            }
            player.prepare()
            player.seekTo(resumePositionMs)
            if (wasPlaying) player.play()        }
    }

    override fun setVideoSizeListener(listener: ((width: Int, height: Int) -> Unit)?) {
        videoSizeListener = listener
    }

    override fun setVideoTrackEnabled(enabled: Boolean) {
        Handler(Looper.getMainLooper()).post {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
                .build()
        }
    }

    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            videoSizeListener?.invoke(videoSize.width, videoSize.height)
        }
    }

    override fun setPlaybackSpeedPitch(speed: Float, pitch: Float) {
        player.playbackParameters = PlaybackParameters(speed, pitch)
    }

    companion object {
        val TAG: String = RetroExoPlayer::class.java.simpleName
    }
}