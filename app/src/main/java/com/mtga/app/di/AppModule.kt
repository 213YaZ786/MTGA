package com.mtga.app.di

import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.core.network.HttpClientFactory
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.InstanceProbe
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.html.HtmlSource
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.instances.InstanceStore
import com.mtga.app.data.repository.FeedRepository
import com.mtga.app.data.rss.RssFeedParser
import com.mtga.app.data.rss.RssSource
import com.mtga.app.feature.accounts.AccountsViewModel
import com.mtga.app.feature.diagnostics.DiagnosticsViewModel
import com.mtga.app.feature.feed.FeedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Single composition root. Layers get added here as they land:
 * step 3 sources, step 4 database.
 */
val appModule = module {

    single(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { HttpClientFactory.create() }
    single { ConnectivityMonitor(androidContext()) }
    single { InstanceStore(androidContext()) }
    single { HtmlTimelineParser() }
    single { InstanceProbe(get(), get(), get()) }

    single {
        InstancePool(
            store = get(),
            probe = get(),
            connectivity = get(),
            scope = get(named("appScope"))
        )
    }

    single { AccountStore(androidContext()) }
    single { RssFeedParser() }
    single { RssSource(get(), get(), get()) }
    single { HtmlSource(get(), get()) }
    single { FeedRepository(get(), get(), get()) }

    viewModel { DiagnosticsViewModel(get()) }
    viewModel { AccountsViewModel(get()) }
    viewModel { FeedViewModel(get(), get()) }
}
