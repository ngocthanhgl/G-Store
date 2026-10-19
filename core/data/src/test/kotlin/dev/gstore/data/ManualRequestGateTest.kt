package dev.gstore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualRequestGateTest {
    @Test
    fun `enforces ten second cooldown for all users`() {
        var now = 0L
        val gate = ManualRequestGate { now }

        assertNull(gate.tryAcquire(authenticated = false))
        assertEquals(10, gate.tryAcquire(authenticated = true))
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
}
