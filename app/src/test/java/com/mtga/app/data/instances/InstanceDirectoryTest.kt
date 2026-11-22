package com.mtga.app.data.instances

import org.junit.Assert.assertEquals
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The answer keeps the shape of
// status.d420.de/api/v1/instances, CacheData { hosts: [CacheHost] }
// (https://github.com/4cuck/nitter-status/blob/main/entities/src/state/mod.rs#CacheHost).
/** The tracker's hosts folded into the wiki's list. */
class InstanceDirectoryTest {

    private fun host(domain: String, healthy: Boolean = true, rss: Boolean = true, bad: Boolean = false, unreliable: Boolean = false) =
        """{"url": "https://$domain", "domain": "$domain", "healthy": $healthy, "rss": $rss, "is_bad_host": $bad,
            "unreliable": $unreliable, "points": 3, "country": "DE", "recent_checks": [["x", true]]}"""

    private val answer = """{"hosts": [
        ${host("nitter.mie.foundation")},
        ${host("down.example", healthy = false)},
        ${host("blocked.example", bad = true)},
        ${host("flaky.example", unreliable = true)},
        ${host("ruf.st", rss = false)},
        ${host("xcancel.com")},
        {"url": "http://plain.example", "domain": "plain.example", "healthy": true}
    ], "last_update": "2026-11-22T10:00:00Z", "latest_commit": "abc"}"""

    @Test
    fun `only healthy hosts that are neither bad nor unreliable are taken`() {
        val found = InstanceDirectory.parseCacheData(answer)

        assertEquals(listOf("nitter.mie.foundation", "ruf.st", "xcancel.com"), found.map { it.host })
        assertEquals(true, found.all { it.listedWorking == true })
    }

    @Test
    fun `a host the wiki holds stays once, as the wiki words it`() {
        val wiki = listOf(InstanceListParser.Listed("xcancel.com", "https://xcancel.com", listedWorking = false))

        val merged = InstanceDirectory.withInstances(wiki, InstanceDirectory.parseCacheData(answer))

        assertEquals(listOf("xcancel.com", "nitter.mie.foundation", "ruf.st"), merged.map { it.host })
        assertEquals(false, merged.first().listedWorking)
    }

    @Test
    fun `an answer that is not the tracker's gives nothing`() {
        assertEquals(emptyList<InstanceListParser.Listed>(), InstanceDirectory.parseCacheData("<html>429</html>"))
    }
}
