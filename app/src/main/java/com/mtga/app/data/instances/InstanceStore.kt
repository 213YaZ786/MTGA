package com.mtga.app.data.instances

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the server list as a single JSON file in app storage, plus the
 * time the public list was last merged in.
 *
 * A plain file rather than DataStore or Room: this is one small list, written
 * rarely, and every dependency MTGA does not take is one that cannot break the
 * build or leak data.
 *
 * The list persists, health never does. The list is what the wiki said and
 * what you chose. Health is a live reading, and a stale one invites bad
 * decisions.
 */
class InstanceStore(context: Context) {

    private val file = File(context.filesDir, "instances.json")
    private val listedAtFile = File(context.filesDir, "instances.listed")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        // The 1.1.2 to 1.3.x revision marker. The compiled list it guarded
        // is gone, so is the marker.
        runCatching { File(context.filesDir, "instances.revision").delete() }
    }

    /** Empty on a fresh install, until the first list update lands. */
    fun load(): List<NitterInstance> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NitterInstance>>(file.readText())
        }.getOrElse { emptyList() }
    }

    fun save(instances: List<NitterInstance>) {
        runCatching { file.writeText(json.encodeToString(instances)) }
    }

    /** Null until the public list has been merged in once. */
    fun listUpdatedAt(): Long? =
        runCatching { listedAtFile.readText().trim().toLong() }.getOrNull()

    fun markListUpdated(atMillis: Long) {
        runCatching { listedAtFile.writeText(atMillis.toString()) }
    }
}
