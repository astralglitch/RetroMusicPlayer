package code.name.monkey.retromusic.extensions

import android.net.Uri
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import androidx.core.net.toUri
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.MusicUtil

/**
 * [Song.data] is a MediaStore-relative file path for scanned local music, but a podcast episode
 * (see `Episode.toSong()`) sets it to either a streaming URL or a downloaded file's `file://`
 * path — neither of which has a MediaStore row to resolve an id-based content:// Uri from. Any
 * other value keeps the original MediaStore lookup, so local-library playback is unaffected.
 */
val Song.uri: Uri
    get() = when {
        data.startsWith("http://") || data.startsWith("https://") -> data.toUri()
        data.startsWith("file://") -> data.toUri()
        else -> MusicUtil.getSongFileUri(songId = id)
    }

val Song.albumArtUri get() = MusicUtil.getMediaStoreAlbumCoverUri(albumId)

fun ArrayList<Song>.toMediaSessionQueue(): List<QueueItem> {
    return map { song ->
        val mediaDescription = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artistName)
            .setIconUri(song.albumArtUri)
            .build()
        QueueItem(mediaDescription, song.hashCode().toLong())
    }
}
