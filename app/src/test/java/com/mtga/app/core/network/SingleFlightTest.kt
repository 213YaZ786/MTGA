/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mtga.app.core.network

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from OneBusAway's SingleFlightTest:
 * https://github.com/OneBusAway/onebusaway-android/blob/main/onebusaway-android/src/test/java/org/onebusaway/android/util/SingleFlightTest.kt
 * (Apache-2.0). This project has no kotlinx-coroutines-test, so the tests run on a real
 * scope inside runBlocking, with the same gates (CompletableDeferred) holding a flight open.
 * Added: a caller that leaves does not cancel the shared call, and the entry is gone after
 * completion even when a newer call took the key (Go's guarded removal).
 */
class SingleFlightTest {

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `concurrent calls for the same key share one execution`() = runBlocking {
        val singleFlight = SingleFlight<String, Int>(scope())
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger()

        val block: suspend () -> Int = {
            executions.incrementAndGet()
            firstEntered.complete(Unit)
            release.await() // hold the in-flight execution open so the second caller has to join it
            42
        }

        val a = async { singleFlight.run("k", block) }
        withTimeout(TIMEOUT_MS) { firstEntered.await() } // a is now running block and parked on release
        val b = async { singleFlight.run("k", block) }
        yield() // b runs on this thread until it parks on the shared call, only then is it released
        release.complete(Unit)

        assertEquals(42, withTimeout(TIMEOUT_MS) { a.await() })
        assertEquals(42, withTimeout(TIMEOUT_MS) { b.await() })
        assertEquals(1, executions.get()) // b joined a's flight rather than running the block again
    }

    @Test
    fun `a cancelled caller does not cancel the shared call`() = runBlocking {
        val singleFlight = SingleFlight<String, Int>(scope())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger()

        val block: suspend () -> Int = {
            executions.incrementAndGet()
            entered.complete(Unit)
            release.await()
            7
        }

        val leaving = async { singleFlight.run("k", block) }
        withTimeout(TIMEOUT_MS) { entered.await() }
        val staying = async { singleFlight.run("k", block) }
        yield() // staying has joined the call before the first caller leaves
        leaving.cancelAndJoin() // the first caller leaves while the call is still running
        release.complete(Unit)

        assertEquals(7, withTimeout(TIMEOUT_MS) { staying.await() })
        assertEquals(1, executions.get())
    }

    @Test
    fun `distinct keys run independently`() = runBlocking {
        val singleFlight = SingleFlight<String, Int>(scope())
        val aEntered = CompletableDeferred<Unit>()
        val bStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        // Hold key "a" in flight so "b" has to start while "a" is still running, a global lock
        // around execution would serialize them and "b" couldn't begin until "a" was released.
        val aBlock: suspend () -> Int = {
            aEntered.complete(Unit)
            release.await()
            1
        }
        val bBlock: suspend () -> Int = {
            bStarted.complete(Unit)
            2
        }

        val a = async { singleFlight.run("a", aBlock) }
        withTimeout(TIMEOUT_MS) { aEntered.await() }
        val b = async { singleFlight.run("b", bBlock) }

        withTimeout(TIMEOUT_MS) { bStarted.await() } // b ran while a was still in flight
        release.complete(Unit)
        assertEquals(1, withTimeout(TIMEOUT_MS) { a.await() })
        assertEquals(2, withTimeout(TIMEOUT_MS) { b.await() })
    }

    @Test
    fun `a later call after completion runs the block again`() = runBlocking {
        val singleFlight = SingleFlight<String, Int>(scope())
        var executions = 0

        // Entries clear on completion, so the second call is a fresh execution, not a joined one.
        assertEquals(1, singleFlight.run("k") { executions++; 1 })
        assertEquals(2, singleFlight.run("k") { executions++; 2 })
        assertEquals(2, executions)
    }

    @Test
    fun `an inline dispatcher with a non-suspending block clears the completed entry`() = runBlocking {
        // Dispatchers.Unconfined runs continuations inline on the current thread. Were the async
        // start eager rather than LAZY, the block would run inside computeIfAbsent's mapping
        // function and the remove would fire there, before the entry is stored. A stale entry
        // would make the second call join the first's completed Deferred instead of running fresh.
        val singleFlight = SingleFlight<String, Int>(CoroutineScope(Dispatchers.Unconfined))
        var executions = 0

        assertEquals(1, singleFlight.run("k") { ++executions })
        assertEquals(2, singleFlight.run("k") { ++executions })
        assertEquals(2, executions)
    }

    @Test
    fun `a cancelled block propagates instead of resolving to null`() = runBlocking {
        val singleFlight = SingleFlight<String, Int>(scope())

        // CancellationException is rethrown ahead of the catch-to-null, so a stopped flight surfaces
        // as cancellation to the caller rather than masquerading as a null (no-result) success.
        val thrown = runCatching {
            singleFlight.run("k") { throw CancellationException("stop") }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `the entry clears after a cancelled flight so a later call runs fresh`() = runBlocking {
        val singleFlight = SingleFlight<String, Int>(scope())
        var executions = 0

        // The finally/remove runs even when the block is cancelled, so the entry does not linger.
        runCatching {
            singleFlight.run("k") {
                executions++
                throw CancellationException("stop")
            }
        }

        assertEquals(7, singleFlight.run("k") { executions++; 7 })
        assertEquals(2, executions) // the second call ran fresh rather than joining a dead flight
    }

    // A non-cancellation exception resolves to null for every joined caller. That path logs
    // through android.util.Log.e, unmocked in plain JVM tests, so it is not covered here.

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
