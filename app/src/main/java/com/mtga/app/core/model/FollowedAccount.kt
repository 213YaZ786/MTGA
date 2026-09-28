package com.mtga.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FollowedAccount(
    val handle: String,
    val displayName: String? = null,
    /**
     * The folder this account is filed in. Never blank: an account put
     * nowhere is in [MAIN], which is also how a file written before folders
     * existed loads. The list of folders, empty ones included, is kept by
     * [com.mtga.app.data.accounts.AccountStore].
     */
    val folder: String = MAIN,
    val addedAtMillis: Long = 0L
) {
    companion object {
        /** Where an account goes when it has been put nowhere. Cannot be renamed or deleted. */
        const val MAIN = "Main"

        /**
         * X handles are 1 to 15 characters, letters, digits and underscore.
         * Validated locally so a typo fails instantly instead of costing a
         * request to an instance that is already rate limiting us.
         */
        private val VALID = Regex("^[A-Za-z0-9_]{1,15}$")

        fun normalise(raw: String): String? {
            val cleaned = raw.trim()
                .removePrefix("@")
                .substringAfterLast('/')
                .substringBefore('?')
            return if (VALID.matches(cleaned)) cleaned else null
        }
    }
}
