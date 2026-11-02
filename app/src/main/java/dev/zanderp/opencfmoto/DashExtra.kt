// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import androidx.annotation.StringRes

/**
 * Extra data a dash design can show in its slots (chips, tiles, list rows, corner readout). [id] is
 * stored in prefs: never change or reuse one. [optional] extras (fuel, sensors) are hidden entirely
 * when unknown, so they leave no gap; the others show "--" until known. [needsSensor] = no source on
 * the phone yet (TPMS / OBD); they can be pre-enabled and appear by themselves once a source writes
 * [DashValues.tyreFrontBar] … [DashValues.coolantC].
 */
enum class DashExtra(
    val id: String,
    @StringRes val labelRes: Int,
    val optional: Boolean = false,
    val needsSensor: Boolean = false,
) {
    TRIP_KM("trip_km", R.string.dash368_trip_distance),
    RIDE_TIME("ride_time", R.string.dash368_ride_time),
    MAX_SPEED("max_speed", R.string.dash368_max_speed),
    AVG_SPEED("avg_speed", R.string.dash_extra_avg_speed),
    CLOCK("clock", R.string.dash_extra_clock),
    ALTITUDE("altitude", R.string.dash368_altitude),
    HEADING("heading", R.string.dash_extra_heading),
    PHONE_BATTERY("phone_battery", R.string.dash_extra_phone_battery),
    ODOMETER("odometer", R.string.dash_extra_odometer),
    FUEL_ECONOMY("fuel_economy", R.string.dash_extra_fuel_economy, optional = true),
    KM_SINCE_REFUEL("km_since_refuel", R.string.dash_extra_km_since_refuel, optional = true),
    RANGE_EST("range_est", R.string.dash_extra_range, optional = true),
    TYRES("tyres", R.string.dash_extra_tyres, optional = true, needsSensor = true),
    COOLANT("coolant", R.string.dash_extra_coolant, optional = true, needsSensor = true),
    ;

    companion object {
        fun fromId(id: String): DashExtra? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Dash-design options, in their own prefs file `ocm_dash`:
 * `extras_<designId>` = string set of [DashExtra.id] (absent = the design's defaults),
 * `tank_liters` = float (absent = not set, no range estimate).
 */
object DashPrefs {
    private const val PREFS = "ocm_dash"
    private const val KEY_TANK = "tank_liters"
    private fun keyExtras(d: DashDesign) = "extras_${d.id}"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Chosen extras of [d] in the design's own order, unsupported ids dropped, at most [DashDesign.slots]. */
    fun extras(ctx: Context, d: DashDesign): Array<DashExtra> {
        val ids = prefs(ctx).getStringSet(keyExtras(d), null)
            ?: return d.defaultExtras.toTypedArray()
        return d.supportedExtras.filter { it.id in ids }.take(d.slots).toTypedArray()
    }

    fun setExtras(ctx: Context, d: DashDesign, extras: Collection<DashExtra>) {
        prefs(ctx).edit().putStringSet(keyExtras(d), extras.mapTo(HashSet()) { it.id }).apply()
    }

    fun tankLiters(ctx: Context): Float? {
        val p = prefs(ctx)
        if (!p.contains(KEY_TANK)) return null
        return p.getFloat(KEY_TANK, 0f).takeIf { it > 0f }
    }

    fun setTankLiters(ctx: Context, liters: Float?) {
        val e = prefs(ctx).edit()
        if (liters == null || liters <= 0f) e.remove(KEY_TANK) else e.putFloat(KEY_TANK, liters)
        e.apply()
    }
}

/**
 * Formats extras for a renderer: resolved strings once, values appended to a caller-owned buffer.
 * No allocation per call.
 */
class DashExtraText(ctx: Context) {
    private val labels = Array(DashExtra.entries.size) { ctx.getString(DashExtra.entries[it].labelRes) }
    private val sKm = ctx.getString(R.string.dash368_unit_km)
    private val sKmh = ctx.getString(R.string.dash368_unit_kmh)
    private val sM = ctx.getString(R.string.dash368_unit_m)
    private val sKmL = ctx.getString(R.string.dash_unit_km_per_l)
    private val sBar = ctx.getString(R.string.dash_unit_bar)
    private val sC = ctx.getString(R.string.dash_unit_celsius)
    private val sFront = ctx.getString(R.string.dash_tyre_front_short)
    private val sRear = ctx.getString(R.string.dash_tyre_rear_short)
    private val scratch = StringBuilder(24)

    fun label(e: DashExtra): String = labels[e.ordinal]

    /** Unit drawn after the value, or null. */
    fun unit(e: DashExtra): String? = when (e) {
        DashExtra.TRIP_KM, DashExtra.ODOMETER, DashExtra.KM_SINCE_REFUEL, DashExtra.RANGE_EST -> sKm
        DashExtra.MAX_SPEED, DashExtra.AVG_SPEED -> sKmh
        DashExtra.ALTITUDE -> sM
        DashExtra.FUEL_ECONOMY -> sKmL
        DashExtra.TYRES -> sBar
        DashExtra.COOLANT -> sC
        else -> null
    }

    /** False when [e] is [DashExtra.optional] and unknown: the caller skips it (no gap). */
    fun visible(e: DashExtra, t: DashValues): Boolean = when (e) {
        DashExtra.FUEL_ECONOMY -> !t.fuelKmPerL.isNaN()
        DashExtra.KM_SINCE_REFUEL -> !t.kmSinceRefuel.isNaN()
        DashExtra.RANGE_EST -> !t.rangeKm.isNaN()
        DashExtra.TYRES -> t.tyreFrontBar != null || t.tyreRearBar != null
        DashExtra.COOLANT -> t.coolantC != null
        else -> true
    }

    /** Append the value of [e] (no unit) to [b]; "--" when unknown. Call only when [visible]. */
    fun value(b: StringBuilder, e: DashExtra, t: DashValues, is24h: Boolean): StringBuilder = when (e) {
        DashExtra.TRIP_KM -> if (t.tripKm.isNaN()) b.append("--") else DashFmt.oneDecimal(b, t.tripKm)
        DashExtra.RIDE_TIME -> if (t.rideSeconds < 0) b.append("--") else {
            val m = t.rideSeconds / 60
            b.append(m / 60).append(':')
            if (m % 60 < 10) b.append('0')
            b.append(m % 60)
        }
        DashExtra.MAX_SPEED -> intOr(b, t.maxSpeedKmh)
        DashExtra.AVG_SPEED -> intOr(b, t.avgSpeedKmh)
        DashExtra.CLOCK -> DashFmt.clock(b, t.clockMinute, is24h)
        DashExtra.ALTITUDE -> if (t.altitudeM.isNaN()) b.append("--") else b.append(Math.round(t.altitudeM))
        DashExtra.HEADING -> if (t.headingDeg.isNaN()) b.append("--") else b.append(DashFmt.COMPASS[DashFmt.compassIndex(t.headingDeg)])
        DashExtra.PHONE_BATTERY -> if (t.phoneBatteryPct < 0) b.append("--") else b.append(t.phoneBatteryPct).append('%')
        DashExtra.ODOMETER -> if (t.odometerKm.isNaN() || t.odometerKm < 0.5) b.append("--")
            else b.append(t.odometerKm.toLong().coerceIn(0L, 999_999L))
        DashExtra.FUEL_ECONOMY -> if (t.fuelKmPerL.isNaN()) b.append("--") else DashFmt.oneDecimal(b, t.fuelKmPerL)
        DashExtra.KM_SINCE_REFUEL -> intOr(b, t.kmSinceRefuel)
        DashExtra.RANGE_EST -> intOr(b, t.rangeKm)
        DashExtra.TYRES -> {
            val f = t.tyreFrontBar
            val r = t.tyreRearBar
            when {
                f != null && r != null -> { DashFmt.oneDecimal(b, f).append('/'); DashFmt.oneDecimal(b, r) }
                f != null -> DashFmt.oneDecimal(b.append(sFront).append(' '), f)
                r != null -> DashFmt.oneDecimal(b.append(sRear).append(' '), r)
                else -> b.append("--")
            }
        }
        DashExtra.COOLANT -> { val c = t.coolantC; if (c == null) b.append("--") else b.append(Math.round(c)) }
    }

    /** Hash of what [extras] show (formatted text + visibility), for [DashRenderer.signature]. */
    fun signature(extras: Array<DashExtra>, t: DashValues, is24h: Boolean): Long {
        var h = 1L
        for (i in extras.indices) {
            val e = extras[i]
            if (!visible(e, t)) { h = h * 31 - 7; continue }
            scratch.setLength(0)
            value(scratch, e, t, is24h)
            for (k in 0 until scratch.length) h = h * 31 + scratch[k].code
            h = h * 31 + e.ordinal
        }
        return h
    }

    private fun intOr(b: StringBuilder, v: Float): StringBuilder =
        if (v.isNaN()) b.append("--") else b.append(v.toInt())
}
