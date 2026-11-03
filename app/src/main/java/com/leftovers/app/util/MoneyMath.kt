package com.leftovers.app.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Safe conversions between decimal numbers and whole minor units (cents, paise). Plain `toLong()` wraps around on
 * overflow, and a value like "1e999999999" makes `setScale` try to build a billion-digit number, so a rate or an
 * amount arriving from a backup, a sync file, a CSV or an AI reply could silently corrupt balances or hang the app.
 */
object MoneyMath {
    /** The largest amount the app handles: 10 trillion in major units. Thousands of these can still be added in a Long. */
    const val MAX_MINOR = 1_000_000_000_000_000L

    private val MAX = BigDecimal(MAX_MINOR)
    private val MIN_RATE = BigDecimal("0.000000001")
    private val MAX_RATE = BigDecimal("1000000000")

    /** True when [value] has more whole digits than [MAX_MINOR] allows; checked from its size, without expanding it. */
    private fun outOfRange(value: BigDecimal) = value.signum() != 0 && value.precision() - value.scale() > 17

    /** [value] rounded to whole minor units, or null when it's beyond what the app can safely hold. */
    fun toMinorOrNull(value: BigDecimal): Long? {
        if (outOfRange(value)) return null
        val rounded = value.setScale(0, RoundingMode.HALF_UP)
        return if (rounded.abs() <= MAX) rounded.toLong() else null
    }

    /** The same, but an out-of-range result is pinned to the limit instead of wrapping around. */
    fun toMinorClamped(value: BigDecimal): Long {
        if (outOfRange(value)) return if (value.signum() > 0) MAX_MINOR else -MAX_MINOR
        val rounded = value.setScale(0, RoundingMode.HALF_UP)
        return when {
            rounded > MAX -> MAX_MINOR
            rounded < MAX.negate() -> -MAX_MINOR
            else -> rounded.toLong()
        }
    }

    /** An exchange rate typed or stored as text, if it's a plain positive number in a believable range; otherwise null. */
    fun rate(text: String?): BigDecimal? {
        val trimmed = text?.trim()?.takeIf { it.isNotEmpty() && it.length <= 32 } ?: return null
        val value = trimmed.toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() > 0 && it >= MIN_RATE && it <= MAX_RATE }
    }

    /** Whether a rate from a feed is believable, before it's formatted and saved. */
    fun plausibleRate(perBase: Double): Boolean = perBase.isFinite() && perBase >= 1e-9 && perBase <= 1e9
}
