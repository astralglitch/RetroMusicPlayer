package code.name.monkey.retromusic.helper

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.LayoutQueueModeToggleBinding
import code.name.monkey.retromusic.model.Song
import com.google.android.material.button.MaterialButton

/**
 * Wires a [LayoutQueueModeToggleBinding] (New queue / Play next / Play last) to a pair of
 * Play / Shuffle buttons on a detail screen.
 *
 * - New queue: replaces the queue (the old Play / Shuffle behaviour).
 * - Play next: inserts the songs right after the current song.
 * - Play last: appends the songs to the end of the queue.
 *
 * Shuffle in Play next / Play last mode shuffles only the inserted songs; it doesn't
 * change the player's shuffle mode. The mode always starts at New queue when a screen opens.
 */
class QueueModeActions(
    private val toggle: LayoutQueueModeToggleBinding,
    private val playButton: MaterialButton,
    private val shuffleButton: MaterialButton,
    private val songsToPlay: () -> List<Song>,
    private val songsToShuffle: () -> List<Song> = songsToPlay,
) {

    private enum class Mode(
        @StringRes val playLabel: Int,
        @StringRes val shuffleLabel: Int,
        @DrawableRes val playIcon: Int,
    ) {
        NEW(R.string.action_play_all, R.string.shuffle, R.drawable.ic_play_arrow),
        NEXT(R.string.action_play_next, R.string.action_shuffle_next, R.drawable.ic_playlist_play),
        LAST(R.string.action_play_last, R.string.action_shuffle_last, R.drawable.ic_queue_music),
    }

    private var mode = Mode.NEW

    // Icon-only buttons (playlist header) keep no text; they only get content descriptions.
    private val playShowsText = !playButton.text.isNullOrEmpty()
    private val shuffleShowsText = !shuffleButton.text.isNullOrEmpty()

    init {
        toggle.root.check(R.id.queueModeNew)
        toggle.root.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mode = when (checkedId) {
                R.id.queueModeNext -> Mode.NEXT
                R.id.queueModeLast -> Mode.LAST
                else -> Mode.NEW
            }
            updateButtons()
        }
        playButton.setOnClickListener { run(shuffle = false) }
        shuffleButton.setOnClickListener { run(shuffle = true) }
        updateButtons()
    }

    private fun updateButtons() {
        val context = playButton.context
        val playLabel = context.getString(mode.playLabel)
        val shuffleLabel = context.getString(mode.shuffleLabel)
        if (playShowsText) playButton.text = playLabel
        if (shuffleShowsText) shuffleButton.text = shuffleLabel
        playButton.contentDescription = playLabel
        shuffleButton.contentDescription = shuffleLabel
        playButton.setIconResource(mode.playIcon)
    }

    private fun run(shuffle: Boolean) {
        val songs = if (shuffle) songsToShuffle() else songsToPlay()
        if (songs.isEmpty()) return
        when (mode) {
            Mode.NEW ->
                if (shuffle) MusicPlayerRemote.openAndShuffleQueue(songs, true)
                else MusicPlayerRemote.openQueue(songs, 0, true)

            Mode.NEXT -> MusicPlayerRemote.playNext(if (shuffle) songs.shuffled() else songs)
            Mode.LAST -> MusicPlayerRemote.enqueue(if (shuffle) songs.shuffled() else songs)
        }
    }
}
