package dev.gstore.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconTest {
    @Test
    fun squircleUsesRoundedCornerFractionOfTileSize() {
        assertEquals(32.dp, squircleDp(72.dp))
        assertEquals(24.dp, squircleDp(56.dp))
    }
}
