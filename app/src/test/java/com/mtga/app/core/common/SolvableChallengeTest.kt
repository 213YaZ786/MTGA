package com.mtga.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolvableChallengeTest {

    @Test
    fun `picks the first check a person can pass`() {
        val errors = listOf(
            AppError.RateLimited("a.example", null),
            check("b.example", ChallengeKind.PROOF_OF_WORK),
            check("c.example", ChallengeKind.JS_INTERSTITIAL)
        )

        assertEquals("b.example", errors.solvableChallenge()?.host)
    }

    @Test
    fun `skips a block the browser cannot pass either`() {
        val errors = listOf(
            check("a.example", ChallengeKind.WAF_BLOCK),
            check("b.example", ChallengeKind.JS_INTERSTITIAL)
        )

        assertEquals("b.example", errors.solvableChallenge()?.host)
    }

    @Test
    fun `a block on its own offers nothing to tap`() {
        assertNull(listOf(check("a.example", ChallengeKind.WAF_BLOCK)).solvableChallenge())
    }

    @Test
    fun `no error means no pill`() {
        assertNull(emptyList<AppError>().solvableChallenge())
        assertNull(listOf(AppError.Offline).solvableChallenge())
    }

    private fun check(host: String, kind: ChallengeKind) =
        AppError.ChallengeRequired(host = host, url = "https://$host/x", kind = kind, status = 403)
}
