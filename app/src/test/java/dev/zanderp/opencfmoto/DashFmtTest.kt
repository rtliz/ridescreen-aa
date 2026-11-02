// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure formatting / data helpers of the dash designs (DashFmt, DashExtra, DashValues, Maintenance.consumption). */
class DashFmtTest {
    private fun one(v: Float) = DashFmt.oneDecimal(StringBuilder(), v).toString()
    private fun clk(m: Int, h24: Boolean) = DashFmt.clock(StringBuilder(), m, h24).toString()

    @Test fun oneDecimal_roundsAndNeverNegative() {
        assertEquals("0.0", one(0f))
        assertEquals("48.6", one(48.6f))
        assertEquals("0.0", one(-5f))
        assertEquals("0.0", one(Float.NaN))
        assertEquals("1000.0", one(999.96f))
        one(Float.MAX_VALUE); one(Float.POSITIVE_INFINITY) // must not throw
    }

    @Test fun clock_12h24h_andUnknown() {
        assertEquals("--", clk(-1, true))
        assertEquals("0:00", clk(0, true))
        assertEquals("12:00", clk(0, false))
        assertEquals("7:45", clk(7 * 60 + 45, false))
        assertEquals("12:05", clk(12 * 60 + 5, false))
        assertEquals("1:05", clk(13 * 60 + 5, false))
        assertEquals("23:59", clk(1439, true))
    }

    @Test fun turnDistance_underAndOverKm() {
        assertEquals("300 m", DashFmt.turnDistance(StringBuilder(), 300, "m", "km").toString())
        assertEquals("999 m", DashFmt.turnDistance(StringBuilder(), 999, "m", "km").toString())
        assertEquals("1.0 km", DashFmt.turnDistance(StringBuilder(), 1000, "m", "km").toString())
        assertEquals("1.2 km", DashFmt.turnDistance(StringBuilder(), 1234, "m", "km").toString())
        assertEquals("0 m", DashFmt.turnDistance(StringBuilder(), 0, "m", "km").toString())
    }

    @Test fun turnBucket_monotonicAndUnknown() {
        assertEquals(-1, DashFmt.turnBucket(-1))
        assertTrue(DashFmt.turnBucket(999) < DashFmt.turnBucket(1000))
    }

    @Test fun compassIndex_cardinalsWrapAndOddInputs() {
        val exp = mapOf(0f to "N", 45f to "NE", 90f to "E", 135f to "SE", 180f to "S", 225f to "SW", 270f to "W", 315f to "NW",
            359f to "N", 22.4f to "N", 22.5f to "NE", 360f to "N", 720f to "N", -45f to "NW", -90f to "W", -1f to "N", -360f to "N")
        for ((deg, name) in exp) assertEquals("deg=$deg", name, DashFmt.COMPASS[DashFmt.compassIndex(deg)])
        for (d in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1e30f, 1e30f, -0f, Float.MIN_VALUE)) {
            val i = DashFmt.compassIndex(d)
            assertTrue("deg=$d idx=$i", i in 0..7)
        }
    }

    @Test fun sensorSig_nullDiffersFromZero() {
        assertTrue(DashFmt.sensorSig(null) != DashFmt.sensorSig(0f))
        assertEquals(DashFmt.sensorSig(2.34f), DashFmt.sensorSig(2.3401f))
    }

    // ---- DashValues defaults: everything unknown, no sensors
    @Test fun dashValues_defaultsUnknown() {
        val v = DashValues()
        assertTrue(v.speedKmh.isNaN() && v.maxSpeedKmh.isNaN() && v.avgSpeedKmh.isNaN() && v.rangeKm.isNaN())
        assertEquals(-1, v.rideSeconds); assertEquals(-1, v.clockMinute)
        assertFalse(v.hasVehicleSensors())
        v.coolantC = 90f
        assertTrue(v.hasVehicleSensors())
    }

    // ---- DashExtra enum
    @Test fun dashExtra_idsUniqueAndFromId() {
        val ids = DashExtra.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (e in DashExtra.entries) assertEquals(e, DashExtra.fromId(e.id))
        assertEquals(null, DashExtra.fromId("nope"))
        assertEquals(null, DashExtra.fromId(""))
    }

    @Test fun dashExtra_storedIdsAreFrozen() {
        // These ids are persisted in prefs (extras_<design>): renaming one drops riders' choices.
        assertEquals(
            listOf("trip_km", "ride_time", "max_speed", "avg_speed", "clock", "altitude", "heading", "phone_battery",
                "odometer", "fuel_economy", "km_since_refuel", "range_est", "tyres", "coolant"),
            DashExtra.entries.map { it.id },
        )
    }

    @Test fun dashExtra_optionalAndSensorFlags() {
        assertTrue(DashExtra.TYRES.needsSensor && DashExtra.COOLANT.needsSensor)
        assertFalse(DashExtra.FUEL_ECONOMY.needsSensor)
        assertTrue(DashExtra.entries.filter { it.needsSensor }.all { it.optional })
    }

    // ---- Refuel maths (Maintenance.consumption)
    private fun fuel(at: Long, km: Double, l: Double?) = Maintenance.Entry(at, km, "fuel", null, l, null)
    private fun book(vararg e: Maintenance.Entry) = Maintenance.Book("k", null, 0.0, emptyList(), e.toList(), 0.0)

    @Test fun consumption_fewerThanTwoFills_null() {
        assertEquals(null, Maintenance.consumption(book()))
        assertEquals(null, Maintenance.consumption(book(fuel(1, 0.0, 10.0))))
        // a fill without litres does not count
        assertEquals(null, Maintenance.consumption(book(fuel(1, 0.0, 10.0), fuel(2, 300.0, null))))
    }

    @Test fun consumption_l100_andKmPerL() {
        val r = Maintenance.consumption(book(fuel(1, 0.0, 12.0), fuel(2, 200.0, 5.0), fuel(3, 400.0, 5.0)))!!
        assertEquals(2.5, r.first, 1e-9)               // 10 L over 400 km = 2.5 L/100km
        assertEquals(40f, (100.0 / r.first).toFloat(), 1e-4f)  // km/L conversion used by DashTelemetry
        assertEquals(3, r.third)
    }

    @Test fun consumption_unsortedLogAndShortDistance() {
        val r = Maintenance.consumption(book(fuel(3, 300.0, 6.0), fuel(1, 0.0, 12.0)))!!
        assertEquals(2.0, r.first, 1e-9)
        assertEquals(null, Maintenance.consumption(book(fuel(1, 0.0, 12.0), fuel(2, 19.0, 6.0)))) // < 20 km
    }

    @Test fun consumption_zeroLitres_givesZeroL100_whichTelemetryTreatsAsUnknown() {
        val r = Maintenance.consumption(book(fuel(1, 0.0, 10.0), fuel(2, 100.0, 0.0)))!!
        assertEquals(0.0, r.first, 0.0) // DashTelemetry guards l100 > 0 before 100 / l100
    }

    // ---- 7-segment table and Thai digits are private: read them reflectively (no Android types touched).
    @Test fun lcd_sevenSegmentTable_0to9() {
        val f = Class.forName("dev.zanderp.opencfmoto.LcdRenderer").getDeclaredField("DIGITS").apply { isAccessible = true }
        val t = f.get(null) as IntArray
        // a=1 b=2 c=4 d=8 e=16 f=32 g=64
        val seg = mapOf('a' to 1, 'b' to 2, 'c' to 4, 'd' to 8, 'e' to 16, 'f' to 32, 'g' to 64)
        val want = arrayOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")
        assertEquals(10, t.size)
        for (d in 0..9) assertEquals("digit $d", want[d].sumOf { seg[it]!! }, t[d])
        assertEquals(10, t.toSet().size) // all distinct
    }

    @Test fun thai_digitMapping() {
        val f = Class.forName("dev.zanderp.opencfmoto.ThaiNumeralRenderer").getDeclaredField("THAI_DIGITS").apply { isAccessible = true }
        val t = f.get(null) as CharArray
        assertEquals(10, t.size)
        for (d in 0..9) assertEquals('๐' + d, t[d])
        assertEquals("๐๑๒๓๔๕๖๗๘๙", String(t))
    }
}
