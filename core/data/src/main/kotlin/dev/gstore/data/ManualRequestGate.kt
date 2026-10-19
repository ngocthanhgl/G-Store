package dev.gstore.data

import java.util.ArrayDeque

/**
 * Throttles user-triggered GitHub refresh/search actions without slowing
 * scheduled or startup synchronization.
 */
class ManualRequestGate(
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastAcceptedAt: Long? = null
    private val anonymousRequests = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(authenticated: Boolean): Int? {
        val now = clockMillis()
        while (anonymousRequests.peekFirst()?.let { now - it >= WINDOW_MILLIS } == true) {
            anonymousRequests.removeFirst()
        }

        val cooldown = lastAcceptedAt?.let { MIN_INTERVAL_MILLIS - (now - it) } ?: 0
        if (cooldown > 0) return seconds(cooldown)

        if (!authenticated && anonymousRequests.size >= ANONYMOUS_REQUESTS_PER_MINUTE) {
            val oldest = requireNotNull(anonymousRequests.peekFirst())
            return seconds(WINDOW_MILLIS - (now - oldest))
        }

        lastAcceptedAt = now
        if (!authenticated) anonymousRequests.addLast(now)
        return null
    }

    private fun seconds(millis: Long) = ((millis + 999) / 1_000).toInt().coerceAtLeast(1)

    private companion object {
        const val MIN_INTERVAL_MILLIS = 10_000L
        const val WINDOW_MILLIS = 60_000L
        const val ANONYMOUS_REQUESTS_PER_MINUTE = 6
    }
}

fun manualRequestWaitMessage(seconds: Int) =
    "Whoa, turbo thumb! Try again in ${seconds}s — GitHub's servers need a sip of tea."
