package dev.gstore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualRequestGateTest {
    @Test
    fun `authenticated users bypass the app request limits`() {
        var now = 0L
        val gate = ManualRequestGate { now }

        assertNull(gate.tryAcquire(authenticated = false))
        repeat(10) { assertNull(gate.tryAcquire(authenticated = true)) }
        assertEquals(10, gate.tryAcquire(authenticated = false))
        now = 10_000L
        assertNull(gate.tryAcquire(authenticated = true))
    }

    @Test
    fun `anonymous requests are capped to six in rolling minute`() {
        var now = 0L
        val gate = ManualRequestGate { now }

        repeat(6) { index ->
            if (index > 0) now += 10_000L
            assertNull(gate.tryAcquire(authenticated = false))
        }
        assertEquals(10, gate.tryAcquire(authenticated = false))
        now = 60_000L
        assertNull(gate.tryAcquire(authenticated = false))
    }

    @Test
    fun `wait messages rotate through twenty distinct jokes`() {
        val messages = (0 until 20).map { manualRequestWaitMessage(10) }

        assertEquals(20, messages.toSet().size)
        assertEquals(messages.first(), manualRequestWaitMessage(10))
    }
}
