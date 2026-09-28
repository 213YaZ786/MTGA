package com.mtga.app.data.accounts

import com.mtga.app.core.link.XLink
import com.mtga.app.core.model.FollowedAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Reads and writes the list of followed accounts. Pure, no Android.
 *
 * The file format is the one Fritter and Squawker share, checked against both
 * sources in September 2026 (lib/settings/_data.dart, lib/database/entities.dart):
 *
 * { "subscriptions": [ { "id": "...", "screen_name": "...", "name": "...",
 *   "profile_image_url_https": null, "verified": 0, "in_feed": 1,
 *   "created_at": "2026-09-11 10:12:00" } ],
 *   "subscriptionGroups": [ { "id": "...", "name": "...", "icon": "",
 *   "color": null, "created_at": "2026-09-11T10:12:00Z" } ],
 *   "subscriptionGroupMembers": [ { "group_id": "...", "profile_id": "..." } ] }
 *
 * Folders are those groups. Main is not written as one: an account in no
 * group is in Main, which is also how those apps read an ungrouped account.
 * They allow an account in several groups, MTGA files it in one, so an import
 * keeps the first group each account appears in.
 *
 * One honest gap: both apps key accounts by X's numeric user id, which MTGA
 * never learns. The export puts the handle in "id" and in "profile_id".
 * Fritter and Squawker read the file, but may need to look each account up
 * again.
 *
 * Import is forgiving: that JSON, from any of the three apps, or plain text
 * with handles, @handles or x.com links, one per line or separated by commas.
 */
object SubscriptionCodec {

    /** One account read from a file, and the folder it was in, when the file says. */
    data class Entry(val handle: String, val folder: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun export(
        accounts: List<FollowedAccount>,
        folders: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val groups = folders.filter { it != FollowedAccount.MAIN }
        val root = buildJsonObject {
            put("exported_by", "MTGA")
            put(
                "subscriptions",
                buildJsonArray {
                    accounts.forEach { account ->
                        add(
                            buildJsonObject {
                                put("id", account.handle)
                                put("screen_name", account.handle)
                                put("name", account.displayName ?: account.handle)
                                put("profile_image_url_https", null as String?)
                                put("verified", 0)
                                put("in_feed", 1)
                                put("created_at", DATE.format(Instant.ofEpochMilli(account.addedAtMillis.coerceAtLeast(0))))
                            }
                        )
                    }
                }
            )
            put(
                "subscriptionGroups",
                buildJsonArray {
                    groups.forEach { name ->
                        add(
                            buildJsonObject {
                                put("id", groupId(name))
                                put("name", name)
                                // Blank asks both apps for their default icon.
                                put("icon", "")
                                put("color", null as Int?)
                                put("created_at", Instant.ofEpochMilli(nowMillis).toString())
                            }
                        )
                    }
                }
            )
            put(
                "subscriptionGroupMembers",
                buildJsonArray {
                    accounts.filter { it.folder != FollowedAccount.MAIN && it.folder in groups }.forEach { account ->
                        add(
                            buildJsonObject {
                                put("group_id", groupId(account.folder))
                                put("profile_id", account.handle)
                            }
                        )
                    }
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Accounts found in [text], valid, deduplicated ignoring case, in file
     * order, each with its folder when the file has one.
     */
    fun import(text: String): List<Entry> {
        val trimmed = text.trim()
        val found = if (trimmed.startsWith("{") || trimmed.startsWith("[")) fromJson(trimmed) else fromText(trimmed)
        return found.mapNotNull { entry -> FollowedAccount.normalise(entry.handle)?.let { entry.copy(handle = it) } }
            .distinctBy { it.handle.lowercase() }
    }

    /**
     * Stable for a given name, so two exports of the same folders agree and
     * a file read back matches its members to its groups.
     */
    private fun groupId(name: String): String = UUID.nameUUIDFromBytes("mtga-folder:$name".toByteArray()).toString()

    private fun fromJson(text: String): List<Entry> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return fromText(text)
        val list = when (root) {
            is JsonObject -> root["subscriptions"] as? JsonArray
            is JsonArray -> root
            else -> null
        } ?: return emptyList()

        // Group names by id, then each account's first group, by the account's
        // id in the file. Fritter's ids are numeric user ids, MTGA's are
        // handles, and the member list refers to whichever the file used.
        val groupNames = ((root as? JsonObject)?.get("subscriptionGroups") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { group -> group.text("id")?.let { id -> group.text("name")?.let { id to it } } }
            .toMap()
        val folderOfProfile = LinkedHashMap<String, String>()
        ((root as? JsonObject)?.get("subscriptionGroupMembers") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .forEach { member ->
                val profile = member.text("profile_id") ?: return@forEach
                val name = member.text("group_id")?.let(groupNames::get) ?: return@forEach
                folderOfProfile.putIfAbsent(profile, name)
            }

        return list.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val handle = element.text("screen_name") ?: element.text("screenName") ?: element.text("handle")
                    handle?.let { Entry(it, folderOfProfile[element.text("id") ?: it]) }
                }
                is JsonPrimitive -> element.contentOrNull?.let { Entry(it) }
                else -> null
            }
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun fromText(text: String): List<Entry> =
        text.split('\n', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                when (val link = XLink.parse(token)) {
                    is XLink.Profile -> Entry(link.handle)
                    is XLink.Post -> link.handle?.let { Entry(it) }
                    null -> if (token.contains("://")) null else Entry(token)
                }
            }
}
