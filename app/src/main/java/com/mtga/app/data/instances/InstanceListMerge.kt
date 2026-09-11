package com.mtga.app.data.instances

/**
 * Folds a freshly fetched public list into the stored one. Pure.
 *
 * Normal update:
 * - A listed server you already have keeps your switch, your order and any
 *   feed URL you set. Its URL and wiki verdict are refreshed.
 * - A newly listed server is appended, switched on only if the wiki marks it
 *   online and working.
 * - A listed server the wiki dropped leaves the pool.
 * - A server you added by hand is never touched.
 *
 * [reset] is used on the first update after 1.3.x, and by "Reset from list".
 * Every listed server takes the wiki's verdict and order, because earlier
 * switches were set against a compiled list, not by you. The one exception is
 * an old entry you switched on that the wiki no longer carries, nitter.space
 * for example. That was your choice, so it stays, as a server you added.
 */
object InstanceListMerge {

    fun merge(
        stored: List<NitterInstance>,
        listed: List<InstanceListParser.Listed>,
        reset: Boolean
    ): List<NitterInstance> {
        val storedByHost = stored.associateBy { it.host.lowercase() }
        val listedHosts = listed.map { it.host }.toSet()

        val fromList = listed.map { entry ->
            val existing = storedByHost[entry.host]
            if (existing != null && !reset) {
                existing.copy(
                    label = entry.host,
                    baseUrl = entry.baseUrl,
                    builtIn = true,
                    listedWorking = entry.listedWorking
                )
            } else {
                NitterInstance(
                    id = entry.host,
                    label = entry.host,
                    baseUrl = entry.baseUrl,
                    rssBaseUrl = existing?.rssBaseUrl,
                    enabled = entry.listedWorking == true,
                    builtIn = true,
                    listedWorking = entry.listedWorking
                )
            }
        }

        val byHand = stored.filter { !it.builtIn && it.host.lowercase() !in listedHosts }

        val kept = if (reset) {
            stored.filter { it.builtIn && it.enabled && it.host.lowercase() !in listedHosts }
                .map { old ->
                    old.copy(
                        id = "custom-" + old.host.filter { it.isLetterOrDigit() },
                        builtIn = false,
                        listedWorking = null
                    )
                }
                .filterNot { keptOne -> byHand.any { it.id == keptOne.id } }
        } else {
            emptyList()
        }

        // On a reset the servers the wiki vouches for go first, so the pool
        // does not start with one it marks as down.
        if (reset) return fromList.sortedBy { it.listedWorking != true } + kept + byHand

        // Your order for everything already there, new arrivals at the end
        // in the wiki's order.
        val order = stored.map { it.host.lowercase() }
        return (fromList + byHand).sortedBy { instance ->
            order.indexOf(instance.host.lowercase()).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }
}
