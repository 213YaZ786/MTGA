package com.mtga.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FollowedAccount(
    val handle: String,
    val displayName: String? = null,
    val addedAtMillis: Long = 0L
) {
    companion object {
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
