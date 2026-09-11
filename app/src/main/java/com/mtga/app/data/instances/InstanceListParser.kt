package com.mtga.app.data.instances

/**
 * Reads the public instance table of the Nitter wiki.
 *
 * Pure, like every parser in MTGA: markdown in, entries out, no Android, no
 * network. The wiki is the list the Nitter project itself maintains, and the
 * status tracker at status.d420.de builds on it, so it is the upstream of
 * every other list.
 *
 * Only the "Official" and "Public" sections are read. Tor and I2P entries are
 * skipped, since MTGA speaks https only. The table layout is the one fetched
 * in September 2026:
 *
 * | [xcancel.com](https://xcancel.com) | :white_check_mark: | ✅ | :us: | ... |
 *
 * Column two is "online", column three "working". The official table has no
 * such columns, so its entries carry no verdict.
 */
object InstanceListParser {

    data class Listed(
        val host: String,
        val baseUrl: String,
        /** Null when the table gives no verdict, as for the official instance. */
        val listedWorking: Boolean?
    )

    fun parse(markdown: String): List<Listed> {
        val found = LinkedHashMap<String, Listed>()
        var section = Section.OTHER

        for (raw in markdown.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#")) {
                section = sectionOf(line)
                continue
            }
            if (section == Section.OTHER || !line.startsWith("|")) continue

            val cells = line.trim('|').split('|').map { it.trim() }
            val url = LINK.find(cells.firstOrNull() ?: continue)?.groupValues?.get(1) ?: continue
            val baseUrl = normalise(url) ?: continue
            val host = baseUrl.substringAfter("://")

            val working = if (section == Section.PUBLIC && cells.size >= 3) {
                verdict(cells[1]) == true && verdict(cells[2]) == true
            } else {
                null
            }
            // First mention wins, the wiki asks for new entries at the bottom.
            found.putIfAbsent(host, Listed(host, baseUrl, working))
        }
        return found.values.toList()
    }

    private enum class Section { OFFICIAL, PUBLIC, OTHER }

    private fun sectionOf(heading: String): Section {
        val title = heading.trimStart('#').trim().lowercase()
        return when {
            title == "official" -> Section.OFFICIAL
            title == "public" -> Section.PUBLIC
            else -> Section.OTHER
        }
    }

    /** True for a check mark, false for a cross, null when the cell says neither. */
    private fun verdict(cell: String): Boolean? = when {
        cell.contains(":x:") || cell.contains("❌") -> false
        cell.contains(":white_check_mark:") || cell.contains("✅") -> true
        else -> null
    }

    /** https only, host only, no path. Anything else is not a public web instance. */
    private fun normalise(url: String): String? {
        if (!url.startsWith("https://")) return null
        val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').lowercase()
        if (!HOST.matches(host)) return null
        if (host.endsWith(".onion") || host.endsWith(".i2p")) return null
        return "https://$host"
    }

    private val LINK = Regex("""\[[^\]]*]\((https?://[^)\s]+)\)""")
    private val HOST = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+""")
}
