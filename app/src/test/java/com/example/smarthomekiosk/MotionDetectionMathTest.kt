package com.example.smarthomekiosk

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDetectionMathTest {

    private fun calculateThreshold(sensitivity: Int): Int {
        val raw = 105 - sensitivity
        val coerced = if (raw < 5) 5 else if (raw > 100) 100 else raw
        return coerced / 2
    }

    @Test
    fun testThresholdMapping() {
        // High sensitivity should yield a low threshold (easier to trigger)
        assertEquals(5, calculateThreshold(95))
        assertEquals(5, calculateThreshold(100)) // Max out boundary

        // Medium sensitivity should yield a medium threshold
        assertEquals(27, calculateThreshold(50))

        // Low sensitivity should yield a high threshold (harder to trigger)
        assertEquals(47, calculateThreshold(10))
        assertEquals(50, calculateThreshold(0)) // Min out boundary
    }
}
