package com.mtga.app.data.accounts

import android.content.Context
import com.mtga.app.core.model.FollowedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The list of handles you follow. Local only, never transmitted anywhere.
 * Same plain JSON approach as the instance list, for the same reasons.
 */
class AccountStore(context: Context) {

    private val file = File(context.filesDir, "accounts.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<FollowedAccount>> = _accounts.asStateFlow()

    private fun load(): List<FollowedAccount> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FollowedAccount>>(file.readText())
        }.getOrDefault(emptyList())
    }

    /** Returns false when the handle is invalid or already followed. */
    fun add(rawHandle: String): Boolean {
        val handle = FollowedAccount.normalise(rawHandle) ?: return false
        if (_accounts.value.any { it.handle.equals(handle, ignoreCase = true) }) return false
        persist(
            _accounts.value + FollowedAccount(
                handle = handle,
                addedAtMillis = System.currentTimeMillis()
            )
        )
        return true
    }

    fun remove(handle: String) =
        persist(_accounts.value.filterNot { it.handle.equals(handle, ignoreCase = true) })

    fun updateDisplayName(handle: String, displayName: String) = persist(
        _accounts.value.map {
            if (it.handle.equals(handle, ignoreCase = true)) {
                it.copy(displayName = displayName)
            } else {
                it
            }
        }
    )

    private fun persist(updated: List<FollowedAccount>) {
        _accounts.value = updated
        runCatching { file.writeText(json.encodeToString(updated)) }
    }
}
