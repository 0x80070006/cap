package org.capnav.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Fails if any text/background token pair used by the UI drops below WCAG AA (4.5:1). */
class ContrastTest {
    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `all used token pairs meet WCAG AA`() {
        ContrastPairs.pairs.forEach { (name, fg, bg) ->
            val r = ratio(fg, bg)
            assertTrue("$name has contrast %.2f".format(r), r >= 4.5)
        }
    }

    @Test
    fun `white on bright brand blue is rejected as the prompt states`() {
        assertTrue(ratio(Color.White, Brand.blue) < 4.5)
    }
}
