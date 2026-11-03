package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyMathTest {
    private fun fast(limitMs: Long, block: () -> Unit) {
        val start = System.nanoTime()
        block()
        assertTrue("took too long", (System.nanoTime() - start) / 1_000_000 < limitMs)
    }

    @Test
    fun ordinaryAmountsRoundAsBefore() {
        assertEquals(12_345L, MoneyMath.toMinorOrNull(BigDecimal("123.45").movePointRight(2)))
        assertEquals(13L, MoneyMath.toMinorOrNull(BigDecimal("12.5")))
        assertEquals(-13L, MoneyMath.toMinorOrNull(BigDecimal("-12.5")))
        assertEquals(0L, MoneyMath.toMinorOrNull(BigDecimal.ZERO))
    }

    @Test
    fun anAmountBeyondTheLimitIsRefusedNotWrapped() {
        // Long.MAX_VALUE + 1 used to wrap to a large negative number.
        assertEquals(null, MoneyMath.toMinorOrNull(BigDecimal("9223372036854775808")))
        assertEquals(null, MoneyMath.toMinorOrNull(BigDecimal("1000000000000001")))
        assertEquals(MoneyMath.MAX_MINOR, MoneyMath.toMinorOrNull(BigDecimal(MoneyMath.MAX_MINOR)))
    }

    @Test
    fun aHugeExponentIsRefusedQuicklyInsteadOfHangingTheApp() {
        fast(500) {
            assertEquals(null, MoneyMath.toMinorOrNull(BigDecimal("1e999999999")))
            assertEquals(null, MoneyMath.toMinorOrNull(BigDecimal("-1e999999999")))
            assertEquals(MoneyMath.MAX_MINOR, MoneyMath.toMinorClamped(BigDecimal("1e999999999")))
            assertEquals(-MoneyMath.MAX_MINOR, MoneyMath.toMinorClamped(BigDecimal("-1E+500000000")))
        }
    }

    @Test
    fun clampingPinsInsteadOfWrapping() {
        assertEquals(MoneyMath.MAX_MINOR, MoneyMath.toMinorClamped(BigDecimal("9223372036854775808")))
        assertEquals(250L, MoneyMath.toMinorClamped(BigDecimal("249.5")))
    }

    @Test
    fun ratesMustBePlainNumbersInABelievableRange() {
        assertEquals(BigDecimal("95.26"), MoneyMath.rate("95.26"))
        assertEquals(BigDecimal("0.0123"), MoneyMath.rate(" 0.0123 "))
        listOf(null, "", "abc", "0", "-3", "1e999999999", "1e12", "0.0000000001", "9".repeat(40), "NaN").forEach {
            assertTrue("$it should be refused", MoneyMath.rate(it) == null)
        }
    }

    @Test
    fun aFeedValueMustBeBelievable() {
        assertTrue(MoneyMath.plausibleRate(0.0105))
        assertTrue(MoneyMath.plausibleRate(95.26))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1e-30, 1e300).forEach { assertTrue("$it", !MoneyMath.plausibleRate(it)) }
    }
}
