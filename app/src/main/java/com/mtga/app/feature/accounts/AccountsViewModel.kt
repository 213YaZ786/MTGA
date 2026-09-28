package com.mtga.app.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtga.app.core.link.XLink
import com.mtga.app.core.model.FollowedAccount
import com.mtga.app.data.accounts.AccountStore
import com.mtga.app.data.cache.FeedCache
import com.mtga.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One followed account as the list shows it, enriched from the local cache. */
data class AccountRow(
    val handle: String,
    val name: String?,
    val avatarUrl: String?,
    val lastPostMillis: Long?,
    val folder: String = FollowedAccount.MAIN
)

/**
 * Accounts and search in one place. The query filters the accounts you follow,
 * and when it is a valid handle you do not follow yet, the screen offers to
 * open that profile. Nothing is ever followed without an explicit tap.
 */
class AccountsViewModel(
    private val store: AccountStore,
    private val cache: FeedCache,
    private val settings: SettingsStore
) : ViewModel() {

    private data class Summary(val name: String?, val avatarUrl: String?, val lastPostMillis: Long?)

    private val summaries = MutableStateFlow<Map<String, Summary>>(emptyMap())

    /** Sorted by name, because this list is for finding an account, not for reading. */
    val rows: StateFlow<List<AccountRow>> = combine(store.accounts, summaries) { accounts, known ->
        accounts.map { account ->
            val summary = known[account.handle.lowercase()]
            AccountRow(
                handle = account.handle,
                name = summary?.name ?: account.displayName,
                avatarUrl = summary?.avatarUrl,
                lastPostMillis = summary?.lastPostMillis,
                folder = account.folder
            )
        }.sortedBy { (it.name ?: it.handle).lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        store.accounts.onEach { refresh() }.launchIn(viewModelScope)
    }

    /** Re-reads avatars and last post dates. Cheap: local files only, no network. */
    fun refresh() {
        viewModelScope.launch {
            summaries.value = store.accounts.value.associate { account ->
                val feed = cache.read(account.handle)
                account.handle.lowercase() to Summary(
                    name = feed?.displayName?.takeIf { it.isNotBlank() && it != account.handle },
                    avatarUrl = feed?.avatarUrl,
                    lastPostMillis = feed?.posts
                        ?.maxOfOrNull { it.publishedAtMillis }
                        ?.takeIf { it > 0L }
                )
            }
        }
    }

    fun isFollowed(handle: String): Boolean =
        store.accounts.value.any { it.handle.equals(handle, ignoreCase = true) }

    fun follow(handle: String) {
        store.add(handle)
    }

    /** Every folder, Main first. */
    val folders: StateFlow<List<String>> = store.folders

    /** Returns the folder to open, the existing one when the name is taken. */
    fun createFolder(name: String): String? = store.createFolder(name)

    fun setFolder(handle: String, folder: String) = store.setFolder(handle, folder)

    fun deleteFolder(name: String) = store.deleteFolder(name)

    /**
     * Returns the folder's new name. When Home was showing it, Home moves to
     * the new name with it rather than falling back to every account.
     */
    fun renameFolder(from: String, to: String): String? {
        val renamed = store.renameFolder(from, to) ?: return null
        if (settings.current.homeFolder == from) settings.update { it.copy(homeFolder = renamed) }
        return renamed
    }

    companion object {
        /**
         * The query as a handle, or null when it cannot be one.
         *
         * A pasted link is read as a link: a profile gives its handle, a post
         * gives its author. Before 2.9.6 the last path segment was taken, so
         * x.com/nasa/status/123 offered to follow an account called 123.
         * The scheme is optional, since a link copied from the address bar
         * of some browsers arrives without it.
         */
        fun asHandle(query: String): String? {
            val trimmed = query.trim()
            if ('/' !in trimmed && '.' !in trimmed) return FollowedAccount.normalise(trimmed)
            val url = if ("://" in trimmed) trimmed else "https://$trimmed"
            return when (val link = XLink.parse(url)) {
                is XLink.Profile -> link.handle
                is XLink.Post -> link.handle
                null -> null
            }
        }
    }
}
