package com.mtga.app.di

import org.koin.dsl.module

/**
 * Single composition root. Layers are added here as they land:
 * step 2 network and instance pool, step 3 sources, step 4 database.
 */
val appModule = module {
    // intentionally empty at step 1
}
