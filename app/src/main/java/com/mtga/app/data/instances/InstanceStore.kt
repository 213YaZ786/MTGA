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
        val stored = runCatching {
            json.decodeFromString<List<NitterInstance>>(file.readText())
        }.getOrElse { emptyList() }

        return if (stored.isEmpty()) NitterInstance.defaults else migrate(stored)
    }

    /**
     * Public instances appear and vanish, so the built in list changes between
     * app versions. Merging rather than replacing keeps anything you added or
     * disabled, while still delivering new instances and dropping built ins
     * that no longer exist.
     */
    private fun migrate(stored: List<NitterInstance>): List<NitterInstance> {
        val storedById = stored.associateBy { it.id }

        val builtIns = NitterInstance.defaults.map { fresh ->
            val previous = storedById[fresh.id]
            // Your enable and disable choices survive. Everything else, the
            // URL especially, comes from the new list.
            if (previous != null) fresh.copy(enabled = previous.enabled) else fresh
        }

        val custom = stored.filterNot { it.builtIn }
            .filterNot { existing -> builtIns.any { it.id == existing.id } }

        // Preserve the order you set for instances that are still around,
        // then append anything newly added.
        val orderedIds = stored.map { it.id }
        val merged = (builtIns + custom).sortedBy { instance ->
            orderedIds.indexOf(instance.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }

        if (merged.map { it.id } != stored.map { it.id }) save(merged)
        return merged
    }

    fun save(instances: List<NitterInstance>) {
        runCatching { file.writeText(json.encodeToString(instances)) }
    }
}
