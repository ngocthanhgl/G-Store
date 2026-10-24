package dev.forkly.data

import kotlin.math.min
import kotlin.random.Random

const val MAX_BACKOFF_MILLIS = 60_000L
const val MAX_RETRY_AFTER_MILLIS = 300_000L

/** Pure 1s·2^attempt capped at 60s — unit-tested, jitter applied by the caller. */
fun baseDelayMillis(attempt: Int): Long {
    require(attempt >= 0)
    var delay = 1_000L
    repeat(min(attempt, 20)) { delay = min(delay * 2, MAX_BACKOFF_MILLIS) }
    return min(delay, MAX_BACKOFF_MILLIS)
}

/** Server-directed wait wins over backoff, capped at 5 min. Null → use backoff. */
fun rateLimitDelayMillis(retryAfterSeconds: Long?): Long? =
    retryAfterSeconds?.let { min(it.coerceAtLeast(0) * 1_000L, MAX_RETRY_AFTER_MILLIS) }

/** ±20% jitter so many clients don't retry in lockstep. */
fun jittered(delayMillis: Long, random: Random = Random.Default): Long {
    val delta = (delayMillis * 0.2).toLong()
    return (delayMillis - delta + random.nextLong(delta * 2 + 1)).coerceAtLeast(0)
}
