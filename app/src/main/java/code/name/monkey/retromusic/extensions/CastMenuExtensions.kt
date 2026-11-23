package code.name.monkey.retromusic.extensions

import android.content.Context
import android.view.Menu
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.util.PreferenceUtil

/**
 * Wires up the toolbar cast button (`R.id.action_cast` from menu_main) when the
 * "Show cast button" setting is on, and removes it otherwise. Music-world tabs only; Podcasts
 * tabs drop the item outright.
 */
fun Context.setUpCastMenuItem(menu: Menu) {
    if (PreferenceUtil.showCastButton) {
        setUpMediaRouteButton(menu)
    } else {
        menu.removeItem(R.id.action_cast)
    }
}
