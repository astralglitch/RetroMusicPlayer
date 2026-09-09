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

import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val PODCAST_HTTP_CLIENT = named("podcastHttpClient")

val podcastModule = module {
    // Deliberately separate from the last.fm OkHttpClient (network/RetrofitClient.kt), which is
    // tuned with 1s timeouts for that specific API — too aggressive for arbitrary feed servers.
    // Qualified so it doesn't collide with that unqualified OkHttpClient binding.
    single(PODCAST_HTTP_CLIENT) {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        PodcastRepository(get(PODCAST_HTTP_CLIENT), get(), get())
    }

    single {
        EpisodeDownloadManager(androidContext(), get())
    }

    single {
        EpisodePositionSaver(get())
    }

    viewModel {
        PodcastsViewModel(get())
    }

    viewModel {
        PodcastHomeViewModel(get())
    }

    viewModel { (podcastId: Long) ->
        PodcastDetailsViewModel(get(), get(), podcastId)
    }
}
