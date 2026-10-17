package dev.gstore.model

/**
 * Corner-radius scale shared by the preference layer and the design system.
 *
 * Lives in :core:model rather than :core:designsystem because the dependency
 * runs datastore -> designsystem: the AppearanceSettings default needs the same
 * clamp the Compose shapes use, and a designsystem import here would be a cycle.
 *
 * 0dp is a legitimate choice (true square), 24dp is the pill-like default, and
 * 28dp is the ceiling — past that, cards stop reading as cards.
 */
object AppRadius {
    const val MIN = 0
    const val MAX = 28
    const val DEFAULT = 24

    fun clamp(dp: Int): Int = dp.coerceIn(MIN, MAX)
}