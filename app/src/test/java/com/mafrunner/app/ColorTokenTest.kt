package com.mafrunner.app

import androidx.compose.ui.graphics.Color
import com.mafrunner.app.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTokenTest {
    @Test
    fun testNordicOledColors() {
        assertEquals(Color(0xFF000000), NordicBlack)
        assertEquals(Color(0xFF00E676), NordicEmerald)
        assertEquals(Color(0xFFFF3B30), NordicCrimson)
        assertEquals(Color(0xFF00D2FF), NordicCyan)
    }
}
