package dev.forkly.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {

    @Test fun `base delay doubles from one second`() {
        assertEquals(1_000L, baseDelayMillis(0))
        assertEquals(2_000L, baseDelayMillis(1))
        assertEquals(4_000L, baseDelayMillis(2))
        assertEquals(32_000L, baseDelayMillis(5))
    }

    @Test fun `base delay caps at sixty seconds`() {
        assertEquals(60_000L, baseDelayMillis(6))
        assertEquals(60_000L, baseDelayMillis(100))
    }

    @Test fun `retry-after wins and caps at five minutes`() {
        assertEquals(45_000L, rateLimitDelayMillis(45))
        assertEquals(300_000L, rateLimitDelayMillis(10_000))
        assertEquals(0L, rateLimitDelayMillis(-5))
        assertNull(rateLimitDelayMillis(null))
    }

    @Test fun `jitter stays within twenty percent`() {
        val r = Random(42)
        repeat(200) {
            val j = jittered(10_000L, r)
            assertTrue(j in 8_000L..12_000L)
        }
    }
}
