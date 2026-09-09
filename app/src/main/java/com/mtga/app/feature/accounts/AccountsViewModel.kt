package com.mtga.app.feature.accounts

import androidx.lifecycle.ViewModel
import com.mtga.app.core.model.FollowedAccount
import com.mtga.app.data.accounts.AccountStore
import kotlinx.coroutines.flow.StateFlow

class AccountsViewModel(private val store: AccountStore) : ViewModel() {

    val accounts: StateFlow<List<FollowedAccount>> = store.accounts

    /** Returns an error message, or null when the handle was added. */
    fun add(raw: String): String? {
        if (FollowedAccount.normalise(raw) == null) {
            return "Not a valid X handle. Letters, digits and underscore, up to 15 characters."
        }
        return if (store.add(raw)) null else "You already follow that account."
    }

    fun remove(handle: String) = store.remove(handle)
}
