package dev.forkly.data

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
        if (authenticated) return null

        val now = clockMillis()
        while (anonymousRequests.peekFirst()?.let { now - it >= WINDOW_MILLIS } == true) {
            anonymousRequests.removeFirst()
        }

        val cooldown = lastAcceptedAt?.let { MIN_INTERVAL_MILLIS - (now - it) } ?: 0
        if (cooldown > 0) return seconds(cooldown)

        if (anonymousRequests.size >= ANONYMOUS_REQUESTS_PER_MINUTE) {
            val oldest = requireNotNull(anonymousRequests.peekFirst())
            return seconds(WINDOW_MILLIS - (now - oldest))
        }

        lastAcceptedAt = now
        anonymousRequests.addLast(now)
        return null
    }

    private fun seconds(millis: Long) = ((millis + 999) / 1_000).toInt().coerceAtLeast(1)

    private companion object {
        const val MIN_INTERVAL_MILLIS = 10_000L
        const val WINDOW_MILLIS = 60_000L
        const val ANONYMOUS_REQUESTS_PER_MINUTE = 6
    }
}

@Synchronized
fun manualRequestWaitMessage(seconds: Int): String {
    val message = waitMessages[nextWaitMessage]
    nextWaitMessage = (nextWaitMessage + 1) % waitMessages.size
    return "$message Try again in ${seconds}s."
}

private var nextWaitMessage = 0
private val waitMessages = listOf(
    "Easy there, speedrunner!",
    "The GitHub octocat needs a tiny stretch.",
    "Your refresh button is asking for a coffee break.",
    "Whoa, turbo thumb!",
    "The catalog is catching its breath.",
    "Even robots need a snack break.",
    "That button has seen enough action for a moment.",
    "The octocat is putting on its running shoes.",
    "Refresh enthusiasm detected. Cooling the engines.",
    "The internet's hamster wheel needs a breather.",
    "One more second, captain refresh.",
    "Your thumb is faster than the satellites.",
    "The catalog is tying its shoelaces.",
    "GitHub's octocat is counting to ten.",
    "A tiny pause for maximum app-iness.",
    "The refresh button is doing a dramatic faint.",
    "Your request is in the tiny timeout lounge.",
    "The servers are hydrating. Very important.",
    "Hold your horses; they're downloading updates too.",
    "The catalog says: not so fast, cowboy.",
)
