package com.example.perfectoutfit.ui.components

import com.example.perfectoutfit.core.model.RainClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WornLineTest {

    @Test
    fun `dry on dry at the target degree stays plain`() {
        val line = wornLine(9, 9, RainClass.DRY, RainClass.DRY)
        assertEquals("Worn at 9°C", line.text)
        assertFalse(line.emphasized)
    }

    @Test
    fun `a matching wet class is named without emphasis`() {
        val line = wornLine(9, 9, RainClass.LIGHT, RainClass.LIGHT)
        assertEquals("Worn at 9°C in light rain", line.text)
        assertFalse(line.emphasized)
    }

    @Test
    fun `a neighbor temperature is called out as before`() {
        assertEquals("Worn at 12°C (2°C warmer)", wornLine(12, 10, RainClass.DRY, RainClass.DRY).text)
        assertEquals("Worn at 8°C (2°C colder)", wornLine(8, 10, RainClass.DRY, RainClass.DRY).text)
    }

    @Test
    fun `a dry outfit for expected rain is called out`() {
        val line = wornLine(9, 9, RainClass.DRY, RainClass.LIGHT)
        assertEquals("Worn at 9°C, dry (light rain expected)", line.text)
        assertTrue(line.rainDiffers)
        assertFalse(line.temperatureDiffers)
    }

    @Test
    fun `a wet outfit for a dry hour says no rain is expected`() {
        assertEquals("Worn at 9°C in moderate rain (no rain expected)", wornLine(9, 9, RainClass.MODERATE, RainClass.DRY).text)
    }

    @Test
    fun `temperature and rain callouts combine`() {
        assertEquals(
            "Worn at 10°C in light rain (2°C warmer, heavy rain expected)",
            wornLine(10, 8, RainClass.LIGHT, RainClass.HEAVY).text
        )
    }
}
