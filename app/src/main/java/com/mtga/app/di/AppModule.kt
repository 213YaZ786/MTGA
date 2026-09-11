package com.mtga.app.di

import com.mtga.app.core.debug.LogExporter
import com.mtga.app.core.debug.RequestLog
import com.mtga.app.core.media.MediaDownloader
import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.core.network.HostThrottle
import com.mtga.app.core.network.HttpClientFactory
import com.mtga.app.core.web.ChallengeGateway
import com.mtga.app.core.web.ChallengeSolver
import com.mtga.app.core.web.WebSession
import com.mtga.app.core.link.LinkRouter
import com.mtga.app.data.instances.InstanceDirectory
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.InstanceProbe
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.html.HtmlSource
import com.mtga.app.data.html.HtmlTimelineParser
import com.mtga.app.data.instances.InstanceStore
import com.mtga.app.data.repository.FeedRepository
import com.mtga.app.data.repository.TimelineRepository
import com.mtga.app.data.rss.RssFeedParser
import com.mtga.app.data.rss.RssSource
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.data.xcom.SyndicationSource
import com.mtga.app.data.xcom.XComSource
import com.mtga.app.data.twstalker.TwstalkerParser
import com.mtga.app.data.twstalker.TwstalkerSource
import com.mtga.app.feature.accounts.AccountsViewModel
import com.mtga.app.feature.post.PostDetailViewModel
import com.mtga.app.feature.search.SearchViewModel
import com.mtga.app.feature.diagnostics.DiagnosticsViewModel
import com.mtga.app.feature.settings.SettingsViewModel
import com.mtga.app.feature.feed.FeedViewModel
import com.mtga.app.feature.timeline.TimelineViewModel
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

    single { RequestLog() }
    single { LogExporter(androidContext()) }
    single { HostThrottle() }
    single { WebSession() }
    single { ChallengeSolver(get()) }
    single { HttpClientFactory.create(get()) }
    single { ChallengeGateway(get(), get(), get(), get(), get()) }
    single { ConnectivityMonitor(androidContext()) }
    single { MediaDownloader(androidContext()) }
    single { InstanceStore(androidContext()) }
    single { AccountStore(androidContext()) }
    single { HtmlTimelineParser() }
    single { InstanceProbe(get(), get(), get(), get()) }

    single { InstanceDirectory(get(), get()) }
    single { LinkRouter(get()) }

    single {
        InstancePool(
            store = get(),
            probe = get(),
            directory = get(),
            connectivity = get(),
            scope = get(named("appScope"))
        )
    }

    single { RssFeedParser() }
    single { RssSource(get(), get(), get()) }
    single { HtmlSource(get(), get(), get(), get()) }
    single { TwstalkerParser() }
    single { TwstalkerSource(get(), get(), get(), get(), get()) }
    single { FeedRepository(get(), get(), get(), get(), get(), get()) }
    single {
        val settings: SettingsStore = get()
        FeedCache(androidContext()) { settings.current.keepPostsDays }
    }
    single { SettingsStore(androidContext()) }
    single { SyndicationSource(get(), get()) }
    single { XComSource(get(), get(), get(), get()) }
    single { TimelineRepository(get(), get(), get(), get(), get()) }

    viewModel { DiagnosticsViewModel(get(), get(), get(), get()) }
    viewModel { AccountsViewModel(get(), get()) }
    viewModel { PostDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { FeedViewModel(get(), get(), get(), get()) }
    viewModel { TimelineViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), androidContext()) }
}
