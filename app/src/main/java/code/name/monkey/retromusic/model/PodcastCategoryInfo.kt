/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package code.name.monkey.retromusic.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import code.name.monkey.retromusic.R
import kotlinx.parcelize.Parcelize

/**
 * The Podcasts world's own bottom-nav tabs -- kept as a separate model/preference from
 * [CategoryInfo] (the Music world's tabs) so the two worlds' bottom-nav settings stay
 * independent, per the podcast nav redesign. Only Subscriptions exists today; Downloads/Queue
 * are follow-up work.
 */
@Parcelize
data class PodcastCategoryInfo(
    val category: Category,
    var visible: Boolean
) : Parcelable {

    enum class Category(
        val id: Int,
        @StringRes val stringRes: Int,
        @DrawableRes val icon: Int
    ) {
        Home(R.id.podcasts_home_fragment, R.string.podcast_tab_home, R.drawable.ic_home),
        Subscriptions(R.id.podcasts_fragment, R.string.podcast_subscriptions_tab, R.drawable.ic_mic),
        Queue(R.id.podcasts_queue_fragment, R.string.podcast_tab_queue, R.drawable.ic_queue_music),
        Downloads(R.id.podcasts_downloads_fragment, R.string.podcast_tab_downloads, R.drawable.ic_download),
        Inbox(R.id.podcasts_inbox_fragment, R.string.podcast_tab_inbox, R.drawable.ic_inbox),
        Episodes(R.id.podcasts_episodes_fragment, R.string.podcast_tab_episodes, R.drawable.ic_audiotrack),
        History(R.id.podcasts_history_fragment, R.string.podcast_tab_history, R.drawable.ic_restore),
        Favorites(R.id.podcasts_favorites_fragment, R.string.podcast_tab_favorites, R.drawable.ic_favorite_border),
        Statistics(R.id.podcasts_statistics_fragment, R.string.podcast_tab_statistics, R.drawable.ic_trending_up);
    }
}
