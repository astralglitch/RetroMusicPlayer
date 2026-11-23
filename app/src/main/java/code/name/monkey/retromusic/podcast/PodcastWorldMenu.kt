package code.name.monkey.retromusic.podcast

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import code.name.monkey.retromusic.R

/** Adds the Podcasts-world toolbar actions (search) to a top-level podcast tab's menu. */
fun MenuInflater.inflatePodcastWorldActions(menu: Menu) {
    inflate(R.menu.menu_podcast_world, menu)
}

/** Handles the actions added by [inflatePodcastWorldActions]; true if [item] was one of them. */
fun Fragment.onPodcastWorldActionSelected(item: MenuItem): Boolean =
    if (item.itemId == R.id.action_search_toolbar) {
        findNavController().navigate(R.id.podcastSearchFragment)
        true
    } else {
        false
    }
