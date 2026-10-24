package dev.forkly.details

import dev.forkly.model.AppRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Radius regression guard.
 *
 * The card slider defaults to 24dp, which turned every full-width screenshot
 * and README image into a lens and clipped their content. Images are now capped
 * well below the slider; cards still follow it. These tests fail if that split
 * ever collapses back into one number for both.
 */
class AppRadiusImageCapTest {

    @Test
    fun `image ceiling sits below the card default`() {
        assertTrue(
            "images must cap below the card default or the lens bug returns",
            AppRadius.IMAGE_MAX < AppRadius.DEFAULT,
        )
    }

    @Test
    fun `image radius clamps to the image ceiling`() {
        // Mirrors imageShapeFor(): clamp() first, then coerce down.
        val sliderMax = AppRadius.MAX
        val effective = AppRadius.clamp(sliderMax).coerceAtMost(AppRadius.IMAGE_MAX)
        assertEquals(AppRadius.IMAGE_MAX, effective)
    }

    @Test
    fun `image radius honours a small slider value`() {
        val small = 4
        assertEquals(small, AppRadius.clamp(small).coerceAtMost(AppRadius.IMAGE_MAX))
    }

    @Test
    fun `card radius still reaches the slider maximum`() {
        assertEquals(AppRadius.MAX, AppRadius.clamp(AppRadius.MAX))
    }

    @Test
    fun `zero stays a legitimate square choice`() {
        assertEquals(0, AppRadius.clamp(AppRadius.MIN))
    }
}