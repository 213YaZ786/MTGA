package com.mtga.app.data.instances

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the instance list as a single JSON file in app storage.
 *
 * A plain file rather than DataStore or Room: this is one small list, written
 * rarely, and every dependency MTGA does not take is one that cannot break the
 * build or leak data.
 */
class InstanceStore(context: Context) {

    private val file = File(context.filesDir, "instances.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<NitterInstance> {
        if (!file.exists()) return NitterInstance.defaults
        return runCatching {
            json.decodeFromString<List<NitterInstance>>(file.readText())
        }.getOrElse { NitterInstance.defaults }
            .ifEmpty { NitterInstance.defaults }
    }

    fun save(instances: List<NitterInstance>) {
        runCatching { file.writeText(json.encodeToString(instances)) }
    }
}
