package com.mtga.app

import android.app.Application
import com.mtga.app.data.settings.SettingsStore
import com.mtga.app.di.appModule
import com.mtga.app.sync.SyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MtgaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@MtgaApplication)
            modules(appModule)
        }

        // WorkManager survives reboots, but re-applying on launch keeps the
        // schedule honest after an app update or a settings change made while
        // the worker was cancelled.
        val settings = org.koin.core.context.GlobalContext.get().get<SettingsStore>()
        if (settings.current.backgroundSync) {
            SyncWorker.schedule(
                this,
                settings.current.syncIntervalMinutes,
                settings.current.syncOnWifiOnly
            )
        }
    }
}
