package com.mtga.app.di

import com.mtga.app.core.network.ConnectivityMonitor
import com.mtga.app.core.network.HttpClientFactory
import com.mtga.app.data.instances.InstancePool
import com.mtga.app.data.instances.InstanceProbe
import com.mtga.app.data.instances.InstanceStore
import com.mtga.app.feature.diagnostics.DiagnosticsViewModel
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
    single { InstanceProbe(get(), get()) }

    single {
        InstancePool(
            store = get(),
            probe = get(),
            connectivity = get(),
            scope = get(named("appScope"))
        )
    }

    viewModel { DiagnosticsViewModel(get()) }
}
