package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPasscodeTest {
    @Test fun waitsLongerAfterRepeatedWrongTries() {
        // The first four wrong tries cost nothing; then 30 s, doubling, up to 15 minutes.
        assertEquals(0L, AppPasscode.waitAfter(4))
        assertEquals(30_000L, AppPasscode.waitAfter(5))
        assertEquals(60_000L, AppPasscode.waitAfter(6))
        assertEquals(480_000L, AppPasscode.waitAfter(9))
        assertEquals(900_000L, AppPasscode.waitAfter(10))
        assertEquals(900_000L, AppPasscode.waitAfter(40))
    }

    @Test
    fun aWaitCountsDownOnTheSameBoot() {
        assertEquals(20_000L, AppPasscode.remainingWait(until = 50_000, len = 30_000, now = 30_000, rebooted = false))
        assertEquals(0L, AppPasscode.remainingWait(until = 50_000, len = 30_000, now = 60_000, rebooted = false))
    }

    @Test
    fun aRestartNeverLeavesAWaitLongerThanItWas() {
        // Before the fix: stored at 5 days of uptime, read at 1 minute of uptime after a restart = a 5 day lockout.
        val fiveDays = 5 * 24 * 3_600_000L
        val until = fiveDays + 900_000
        assertEquals(900_000L, AppPasscode.remainingWait(until = until, len = 900_000, now = 60_000, rebooted = true))
        // Even a damaged or tampered stored value can't exceed the wait's own length.
        assertEquals(30_000L, AppPasscode.remainingWait(until = Long.MAX_VALUE, len = 30_000, now = 0, rebooted = false))
    }
}
