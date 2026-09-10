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
    private val revisionFile = File(context.filesDir, "instances.revision")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): List<NitterInstance> {
        if (!file.exists()) {
            writeRevision()
            return NitterInstance.defaults
        }
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

        // A pool revision resets the enabled state of built ins once, because
        // the old choices were made against a pool that no longer works. Your
        // custom instances and anything you change afterwards are untouched.
        val resetting = readRevision() < NitterInstance.POOL_REVISION

        val builtIns = NitterInstance.defaults.map { fresh ->
            val previous = storedById[fresh.id]
            // Your enable and disable choices survive. Everything else, the
            // URL especially, comes from the new list.
            if (previous != null && !resetting) fresh.copy(enabled = previous.enabled) else fresh
        }

        val custom = stored.filterNot { it.builtIn }
            .filterNot { existing -> builtIns.any { it.id == existing.id } }

        // Preserve the order you set for instances that are still around,
        // then append anything newly added.
        // On a reset, the new default order wins as well, so the one working
        // instance is tried first instead of last.
        val orderedIds = if (resetting) NitterInstance.defaults.map { it.id } else stored.map { it.id }
        val merged = (builtIns + custom).sortedBy { instance ->
            orderedIds.indexOf(instance.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }

        if (resetting || merged.map { it.id } != stored.map { it.id }) save(merged)
        if (resetting) writeRevision()
        return merged
    }

    private fun readRevision(): Int =
        runCatching { revisionFile.readText().trim().toInt() }.getOrDefault(0)

    private fun writeRevision() {
        runCatching { revisionFile.writeText(NitterInstance.POOL_REVISION.toString()) }
    }

    fun save(instances: List<NitterInstance>) {
        runCatching { file.writeText(json.encodeToString(instances)) }
    }
}
