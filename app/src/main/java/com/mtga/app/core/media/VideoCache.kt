package com.mtga.app.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The one video cache of the app, so a video watched online plays again with
 * no network.
 *
 * Taken from androidx/media (Apache-2.0), the demo app's DemoUtil:
 * getDownloadCache and getDatabaseProvider build one SimpleCache over a
 * StandaloneDatabaseProvider, buildReadOnlyCacheDataSource wraps the network
 * source in a CacheDataSource.Factory with FLAG_IGNORE_CACHE_ON_ERROR
 * https://github.com/androidx/media/blob/release/demos/main/src/main/java/androidx/media3/demo/main/DemoUtil.java
 * and the guide https://developer.android.com/media/media3/exoplayer/downloading-media
 *
 * Two changes for a reader. The evictor is a least recently used one with a
 * ceiling, since nothing here is downloaded on purpose, and writes stay on so
 * what is watched is kept. In filesDir, not cacheDir, so Android's cache
 * cleaning does not wipe it. Never released: a SimpleCache must be the only
 * one on its folder, so it lives as long as the app.
 */
@OptIn(UnstableApi::class)
class VideoCache(private val context: Context) {

    /** Opened on first use. SimpleCache reads its folder on a thread of its own. */
    val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.filesDir, FOLDER),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context)
        )
    }

    /**
     * Network first through the cache, and the app's own files and content
     * addresses straight to disk: DefaultDataSource picks the source by the
     * address scheme and only http and https reach [cacheFactory]
     * (androidx/media, libraries/datasource/src/main/java/androidx/media3/datasource/DefaultDataSource.java,
     * Factory(Context, DataSource.Factory baseDataSourceFactory)).
     */
    fun dataSourceFactory(): DataSource.Factory {
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultDataSource.Factory(context, cacheFactory)
    }

    companion object {
        const val FOLDER = "video_cache"
        const val MAX_BYTES = 1024L * 1024 * 1024
    }
}
