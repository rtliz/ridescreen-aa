// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import androidx.annotation.StringRes

/**
 * Draws one bike-dash design on a Canvas. All coordinates are in the 1024×425 design canvas of
 * `mockups/dash-designs.html`: x scales by [sx] (= width/1024), y by [sy] (= height/425), sizes by
 * min(sx, sy). One instance belongs to one screen (or one picker preview) and is used on the main
 * thread only. See docs/DASH-DESIGNS.md.
 *
 * Rules: no allocation in [draw]/[drawOver]/[signature] (Paints, Paths, RectF and a StringBuilder
 * are fields), unknown values show "--", vehicle-sensor values (tyres/coolant) are hidden when null.
 */
interface DashRenderer {
    /** The view got a new size: rebuild anything scaled (paths, rounded panels). */
    fun onScale(sx: Float, sy: Float) {}

    /** Everything under the map slot (the whole screen for a design without a map). */
    fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float)

    /** Cards drawn over the map (turn card, speed, ETA). Only called when the design has a map. */
    fun drawOver(c: Canvas, t: DashValues, sx: Float, sy: Float) {}

    /** Hash of what [draw] shows at the precision it shows it; a change triggers a redraw. */
    fun signature(t: DashValues): Long

    /** Same for [drawOver]. */
    fun overSignature(t: DashValues): Long = 0L
}

/**
 * The choosable bike-dash designs. [id] is what is stored in prefs (`dash_design`): never change an
 * id or reuse one, and never store [ordinal]. [mapRect] is the map slot in 1024×425 design px (null
 * = no map: the map still runs for location/nav but is hidden). [factory] null = not built yet; the
 * picker shows it as "coming soon" and [DashScreen] falls back to [STOCK_368].
 *
 * Extras: [supportedExtras] in the order the design draws them, at most [slots] chosen at once,
 * [defaultExtras] = what the mockup shows (used until the rider changes it, see [DashPrefs]).
 */
enum class DashDesign(
    val id: String,
    val number: Int,
    @StringRes val labelRes: Int,
    val mapRect: RectF?,
    /** Corner radius of the map slot, design px. */
    val mapRadius: Float,
    val slots: Int,
    val supportedExtras: List<DashExtra>,
    val defaultExtras: List<DashExtra>,
    val factory: ((Context, Array<DashExtra>) -> DashRenderer)?,
) {
    /** #1: the three left panels. */
    STOCK_368(
        "stock_368", 1, R.string.dash_design_1, RectF(312f, 46f, 788f, 379f), 10f,
        3, PANEL_EXTRAS, listOf(DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED),
        { c, e -> Stock368Renderer(c, e) },
    ),
    /** #2: readouts in the top-right status pill, before the clock. */
    FULL_MAP(
        "full_map", 2, R.string.dash_design_2, RectF(0f, 0f, 1024f, 425f), 0f,
        3, CORNER_EXTRAS, listOf(DashExtra.PHONE_BATTERY, DashExtra.CLOCK),
        { c, e -> FullMapRenderer(c, e) },
    ),
    /** #3: readouts in the top-right pill over the map. */
    ARROW_MAP(
        "arrow_map", 3, R.string.dash_design_3, RectF(398f, 0f, 1024f, 425f), 0f,
        2, CORNER_EXTRAS, listOf(DashExtra.CLOCK),
        { c, e -> ArrowMapRenderer(c, e) },
    ),
    /** #4: clock and status in the top row; the other extras join the right-hand group. */
    SPEED_ONLY(
        "speed_only", 4, R.string.dash_design_4, null, 0f,
        3, ALL_EXTRAS_CLOCK_FIRST, listOf(DashExtra.CLOCK, DashExtra.PHONE_BATTERY),
        { c, e -> SpeedOnlyRenderer(c, e) },
    ),
    /** #5: a 2×3 grid of tiles beside the speed card. */
    TRIP_COMPUTER(
        "trip_computer", 5, R.string.dash_design_5, null, 0f,
        6, TRIP_EXTRAS,
        listOf(
            DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED,
            DashExtra.CLOCK, DashExtra.ALTITUDE, DashExtra.ODOMETER,
        ),
        { c, e -> TripComputerRenderer(c, e) },
    ),
    /** #6: up to three slanted tiles on the right. */
    SPORT(
        "sport", 6, R.string.dash_design_6, null, 0f,
        3, SPORT_EXTRAS, listOf(DashExtra.MAX_SPEED, DashExtra.RIDE_TIME, DashExtra.TRIP_KM),
        { c, e -> SportRenderer(c, e) },
    ),
    /** #7: clock + battery top right, the rest as readouts bottom right. */
    NIGHT(
        "night", 7, R.string.dash_design_7, null, 0f,
        4, NIGHT_EXTRAS,
        listOf(DashExtra.PHONE_BATTERY, DashExtra.CLOCK, DashExtra.TRIP_KM, DashExtra.RIDE_TIME),
        { c, e -> NightRenderer(c, e) },
    ),
    /** #8: rows under the route rows (arrival, minutes, km left) in the right column. */
    TOURING(
        "touring", 8, R.string.dash_design_8, null, 0f,
        2, TOURING_EXTRAS, listOf(DashExtra.ALTITUDE),
        { c, e -> TouringRenderer(c, e) },
    ),
    /** #9: panels on the right; the odometer is drawn as a footer line. */
    VINTAGE(
        "vintage", 9, R.string.dash_design_9, null, 0f,
        4, VINTAGE_EXTRAS,
        listOf(DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.ODOMETER),
        { c, e -> VintageRenderer(c, e) },
    ),
    /** #10: one list row per extra, after the speed row. */
    INFO_LIST(
        "info_list", 10, R.string.dash_design_10, null, 0f,
        7, LIST_EXTRAS,
        listOf(
            DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.CLOCK,
            DashExtra.ALTITUDE, DashExtra.HEADING, DashExtra.PHONE_BATTERY,
        ),
        { c, e -> InfoListRenderer(c, e) },
    ),
    // 11–20 ("เรือนไมล์ล้วน", speedometer only): use the ids in RESERVED_IDS, in order.
    ;

    val showsMap: Boolean get() = mapRect != null
    val implemented: Boolean get() = factory != null

    /** A renderer of this design showing [extras] (precomputed list, see [DashPrefs.extras]). */
    fun newRenderer(ctx: Context, extras: Array<DashExtra>): DashRenderer? = factory?.invoke(ctx, extras)

    companion object {
        val DEFAULT = STOCK_368

        /** Ids kept for designs #11–#20; do not use them for anything else. */
        val RESERVED_IDS = listOf(
            "speedo_11", "speedo_12", "speedo_13", "speedo_14", "speedo_15",
            "speedo_16", "speedo_17", "speedo_18", "speedo_19", "speedo_20",
        )

        /** Unknown or removed ids fall back to [DEFAULT] so an old install never breaks. */
        fun fromId(id: String?): DashDesign = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Every extra except the clock / battery (#1 already shows those in its top bar). */
private val PANEL_EXTRAS = listOf(
    DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.AVG_SPEED,
    DashExtra.ALTITUDE, DashExtra.ODOMETER, DashExtra.FUEL_ECONOMY, DashExtra.KM_SINCE_REFUEL,
    DashExtra.RANGE_EST, DashExtra.TYRES, DashExtra.COOLANT,
)

/** Short readouts that fit in a corner pill. */
private val CORNER_EXTRAS = listOf(
    DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.AVG_SPEED,
    DashExtra.ALTITUDE, DashExtra.HEADING, DashExtra.ODOMETER, DashExtra.FUEL_ECONOMY,
    DashExtra.KM_SINCE_REFUEL, DashExtra.RANGE_EST, DashExtra.TYRES, DashExtra.COOLANT,
    DashExtra.PHONE_BATTERY, DashExtra.CLOCK,
)

/** The optional extras (fuel, tyres, coolant) that every non-map design lists last. */
private val OPTIONAL_EXTRAS = listOf(
    DashExtra.FUEL_ECONOMY, DashExtra.KM_SINCE_REFUEL, DashExtra.RANGE_EST,
    DashExtra.TYRES, DashExtra.COOLANT,
)

private val ALL_EXTRAS_CLOCK_FIRST = listOf(
    DashExtra.CLOCK, DashExtra.PHONE_BATTERY, DashExtra.TRIP_KM, DashExtra.RIDE_TIME,
    DashExtra.MAX_SPEED, DashExtra.AVG_SPEED, DashExtra.ALTITUDE, DashExtra.HEADING,
    DashExtra.ODOMETER,
) + OPTIONAL_EXTRAS

private val TRIP_EXTRAS = listOf(
    DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.CLOCK,
    DashExtra.ALTITUDE, DashExtra.ODOMETER, DashExtra.AVG_SPEED, DashExtra.HEADING,
    DashExtra.PHONE_BATTERY,
) + OPTIONAL_EXTRAS

private val SPORT_EXTRAS = listOf(
    DashExtra.MAX_SPEED, DashExtra.RIDE_TIME, DashExtra.TRIP_KM, DashExtra.AVG_SPEED,
    DashExtra.ALTITUDE, DashExtra.HEADING, DashExtra.ODOMETER, DashExtra.CLOCK,
    DashExtra.PHONE_BATTERY,
) + OPTIONAL_EXTRAS

private val NIGHT_EXTRAS = listOf(
    DashExtra.PHONE_BATTERY, DashExtra.CLOCK, DashExtra.TRIP_KM, DashExtra.RIDE_TIME,
    DashExtra.MAX_SPEED, DashExtra.AVG_SPEED, DashExtra.ALTITUDE, DashExtra.HEADING,
    DashExtra.ODOMETER,
) + OPTIONAL_EXTRAS

/** The compass already shows the heading, so #8 does not offer it as a row. */
private val TOURING_EXTRAS = listOf(
    DashExtra.ALTITUDE, DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED,
    DashExtra.AVG_SPEED, DashExtra.CLOCK, DashExtra.PHONE_BATTERY, DashExtra.ODOMETER,
) + OPTIONAL_EXTRAS

/** The clock is a fixed dial in #9, so the CLOCK extra is not offered. */
private val VINTAGE_EXTRAS = listOf(
    DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.ODOMETER,
    DashExtra.AVG_SPEED, DashExtra.ALTITUDE, DashExtra.HEADING, DashExtra.PHONE_BATTERY,
) + OPTIONAL_EXTRAS

private val LIST_EXTRAS = listOf(
    DashExtra.TRIP_KM, DashExtra.RIDE_TIME, DashExtra.MAX_SPEED, DashExtra.CLOCK,
    DashExtra.ALTITUDE, DashExtra.HEADING, DashExtra.PHONE_BATTERY, DashExtra.AVG_SPEED,
    DashExtra.ODOMETER,
) + OPTIONAL_EXTRAS

/** Fixed sample values for the picker previews (never shown on the bike). */
object DashSamples {
    fun preview(ctx: Context): DashValues = DashValues().apply {
        speedKmh = 62f
        maxSpeedKmh = 96f
        tripKm = 48.6f
        rideSeconds = 72 * 60
        headingDeg = 45f
        altitudeM = 12f
        phoneBatteryPct = 78
        headsetConnected = true
        gpsFix = true
        recording = true
        crashArmed = true
        nextTurnDir = 2
        nextTurnMeters = 300
        odometerKm = 12345.0
        odometerFromBook = true
        routeActive = true
        etaClockMinute = 14 * 60 + 32
        etaMinutes = 18
        remainKm = 12.4f
        cardDir = 2
        cardMeters = 300
        clockMinute = 7 * 60 + 45
        nextRoad = ctx.getString(R.string.dash_sample_road)
        avgSpeedKmh = 41f
        fuelKmPerL = 32.5f
        kmSinceRefuel = 120f
        rangeKm = 270f
        // Tyres / coolant stay null: no sensor source exists yet.
    }
}

/** Number formatting shared by the renderers; everything appends to a caller-owned buffer. */
object DashFmt {
    val COMPASS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** Tenths exactly as [oneDecimal] rounds them (never negative). */
    fun tenths(v: Float): Int = (v * 10f + 0.5f).toInt().coerceAtLeast(0)

    fun oneDecimal(b: StringBuilder, v: Float): StringBuilder {
        val tn = tenths(v)
        return b.append(tn / 10).append('.').append(tn % 10)
    }

    /** "h:mm" from minutes since midnight, 12/24 h; "--" when negative. */
    fun clock(b: StringBuilder, minuteOfDay: Int, is24h: Boolean): StringBuilder {
        if (minuteOfDay < 0) return b.append("--")
        var hh = minuteOfDay / 60
        val mm = minuteOfDay % 60
        if (!is24h) { hh %= 12; if (hh == 0) hh = 12 }
        b.append(hh).append(':')
        if (mm < 10) b.append('0')
        return b.append(mm)
    }

    /** Turn distance: "300 m" under 1 km, else "1.2 km" (units from the caller). */
    fun turnDistance(b: StringBuilder, meters: Int, m: String, km: String): StringBuilder {
        if (meters < 1000) return b.append(meters).append(' ').append(m)
        val tn = (meters / 100f + 0.5f).toInt()
        return b.append(tn / 10).append('.').append(tn % 10).append(' ').append(km)
    }

    /** Bucket used in signatures so a turn distance repaints at the precision it is shown. */
    fun turnBucket(meters: Int): Int = if (meters < 0) -1 else if (meters < 1000) meters / 10 else 1000 + meters / 100

    fun compassIndex(deg: Float): Int {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return ((d + 22.5f) / 45f).toInt() % 8
    }

    /** Signature part for a nullable sensor value, at one-decimal precision. */
    fun sensorSig(v: Float?): Int = if (v == null) Int.MIN_VALUE else Math.round(v * 10f)
}
