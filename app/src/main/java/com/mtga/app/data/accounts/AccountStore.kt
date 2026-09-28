package com.mtga.app.data.accounts

import android.content.Context
import com.mtga.app.core.common.writeTextAtomically
import com.mtga.app.core.model.FollowedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The list of handles you follow, and the folders they are filed in. Local
 * only, never transmitted anywhere. Same plain JSON approach as the instance
 * list, for the same reasons.
 *
 * Every change is a read, a transform and a write of the whole list, so they
 * are serialised: two made at once, a follow from a shared link while an
 * import runs for instance, would otherwise each start from the same list and
 * the second write would drop the first one's account.
 */
class AccountStore(context: Context) {

    private val file = File(context.filesDir, "accounts.json")
    private val foldersFile = File(context.filesDir, "folders.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<FollowedAccount>> = _accounts.asStateFlow()

    /**
     * Every folder that exists, Main first and the rest by name.
     *
     * Kept in its own file so an empty folder exists: it can be created first,
     * chosen in Home, and filled afterwards. Membership lives on the account,
     * so exactly one place says where an account is. Main is never written,
     * it exists whether or not anything is in it.
     */
    private val _folders = MutableStateFlow(loadFolders())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private fun load(): List<FollowedAccount> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FollowedAccount>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun loadFolders(): List<String> {
        val stored = if (!foldersFile.exists()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<String>>(foldersFile.readText()) }.getOrDefault(emptyList())
        }
        // Names the accounts carry are folded in, so a folder can never be
        // missing from the list while an account is filed in it.
        return FolderNames.ordered(stored + _accounts.value.map { it.folder })
    }

    /** Returns false when the handle is invalid or already followed. */
    @Synchronized
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

    /**
     * Follows every valid handle not already followed, in one write, and
     * files each where the file says. Returns how many were new. Used by
     * import, where fifty separate writes would also mean fifty separate list
     * updates for Home to react to.
     *
     * An account already followed keeps its folder, unless it sits in Main:
     * restoring a backup then puts back the folders the backup knew about
     * without undoing a filing made on this phone since.
     */
    @Synchronized
    fun addAll(entries: List<SubscriptionCodec.Entry>): Int {
        val folders = _folders.value.toMutableList()
        fun folderOf(entry: SubscriptionCodec.Entry): String =
            FolderNames.resolve(entry.folder.orEmpty(), folders).also { name ->
                if (name != FollowedAccount.MAIN && folders.none { it == name }) folders += name
            }

        val byHandle = LinkedHashMap<String, FollowedAccount>()
        _accounts.value.forEach { byHandle[it.handle.lowercase()] = it }
        val now = System.currentTimeMillis()
        var added = 0
        entries.forEach { entry ->
            val handle = FollowedAccount.normalise(entry.handle) ?: return@forEach
            val key = handle.lowercase()
            val known = byHandle[key]
            when {
                known == null -> {
                    byHandle[key] = FollowedAccount(handle = handle, folder = folderOf(entry), addedAtMillis = now)
                    added++
                }
                known.folder == FollowedAccount.MAIN && entry.folder != null ->
                    byHandle[key] = known.copy(folder = folderOf(entry))
            }
        }
        persistFolders(folders)
        val updated = byHandle.values.toList()
        if (updated != _accounts.value) persist(updated)
        return added
    }

    @Synchronized
    fun remove(handle: String) =
        persist(_accounts.value.filterNot { it.handle.equals(handle, ignoreCase = true) })

    @Synchronized
    fun updateDisplayName(handle: String, displayName: String) {
        // Sources that cannot tell send a blank or the handle itself. Neither
        // should overwrite a real name learned earlier.
        if (displayName.isBlank() || displayName.equals(handle, ignoreCase = true)) return
        val current = _accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) } ?: return
        if (current.displayName == displayName) return
        persist(
            _accounts.value.map {
                if (it.handle.equals(handle, ignoreCase = true)) it.copy(displayName = displayName) else it
            }
        )
    }

    /**
     * Creates a folder and returns its name. A name already taken, whatever
     * its case, is not created twice: the existing one is returned so the
     * caller opens the folder the reader meant. Null for a blank name.
     */
    @Synchronized
    fun createFolder(name: String): String? {
        if (name.isBlank()) return null
        val resolved = FolderNames.resolve(name, _folders.value)
        if (_folders.value.none { it == resolved }) persistFolders(_folders.value + resolved)
        return resolved
    }

    /** Files an account, moving it out of wherever it was. A blank name means Main. */
    @Synchronized
    fun setFolder(handle: String, folder: String) {
        val resolved = FolderNames.resolve(folder, _folders.value)
        val current = _accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) } ?: return
        if (_folders.value.none { it == resolved }) persistFolders(_folders.value + resolved)
        if (current.folder == resolved) return
        persist(
            _accounts.value.map {
                if (it.handle.equals(handle, ignoreCase = true)) it.copy(folder = resolved) else it
            }
        )
    }

    /** Deletes a folder. Its accounts go back to Main, nothing is unfollowed. */
    @Synchronized
    fun deleteFolder(name: String) {
        if (name == FollowedAccount.MAIN || _folders.value.none { it == name }) return
        persistFolders(_folders.value.filterNot { it == name })
        if (_accounts.value.any { it.folder == name }) {
            persist(_accounts.value.map { if (it.folder == name) it.copy(folder = FollowedAccount.MAIN) else it })
        }
    }

    /**
     * Renames a folder, in the list and on every account in it, and returns
     * the name it now has, or null when nothing changed. Renaming onto a name
     * already in use merges the two under that name's spelling, since two
     * folders with one name could not be told apart. Main is neither renamed
     * nor a target: sending accounts back there is what delete is for.
     */
    @Synchronized
    fun renameFolder(from: String, to: String): String? {
        if (from == FollowedAccount.MAIN || to.isBlank() || _folders.value.none { it == from }) return null
        val others = _folders.value.filterNot { it == from }
        val target = FolderNames.resolve(to, others)
        if (target == FollowedAccount.MAIN || target == from) return null
        persistFolders(others + target)
        if (_accounts.value.any { it.folder == from }) {
            persist(_accounts.value.map { if (it.folder == from) it.copy(folder = target) else it })
        }
        return target
    }

    private fun persist(updated: List<FollowedAccount>) {
        _accounts.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }

    private fun persistFolders(names: List<String>) {
        val list = FolderNames.ordered(names)
        if (list == _folders.value) return
        _folders.value = list
        runCatching { foldersFile.writeTextAtomically(json.encodeToString(list.drop(1))) }
    }
}
