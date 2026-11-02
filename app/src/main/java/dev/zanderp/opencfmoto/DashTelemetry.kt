// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import java.util.TimeZone

/**
 * The values a dash design shows ([DashRenderer]), as plain mutable primitives so a redraw never
 * allocates. Unknown = NaN / -1 / null: the renderer shows "--" (or hides it, see each field).
 * [DashTelemetry] is the live instance; [DashSamples.preview] is a fixed sample for the picker.
 */
open class DashValues {
    // ---- shown values (NaN / -1 = unknown)
    @JvmField var speedKmh = Float.NaN
    @JvmField var maxSpeedKmh = Float.NaN
    @JvmField var tripKm = Float.NaN
    @JvmField var rideSeconds = -1
    @JvmField var headingDeg = Float.NaN
    @JvmField var altitudeM = Float.NaN
    @JvmField var phoneBatteryPct = -1
    @JvmField var headsetConnected = false
    @JvmField var gpsFix = false
    @JvmField var recording = false
    @JvmField var crashArmed = false
    /** 0 = no left/right turn ahead, 1 = left, 2 = right. */
    @JvmField var nextTurnDir = 0
    @JvmField var nextTurnMeters = -1
    /** Odometer from the maintenance book (or the app's own km) + the live trip; NaN until known. */
    @JvmField var odometerKm = Double.NaN
    /** False while the book odometer was never set: then it is just the app's own km, shown unpadded. */
    @JvmField var odometerFromBook = false
    // Route strip / turn card over the map (hidden when no route is active).
    @JvmField var routeActive = false
    @JvmField var etaClockMinute = -1
    @JvmField var etaMinutes = -1
    @JvmField var remainKm = Float.NaN
    /** Turn card: 0 = none, 1 = left, 2 = right, 3 = straight / other. */
    @JvmField var cardDir = 0
    @JvmField var cardMeters = -1
    /** Local time as minutes since midnight. */
    @JvmField var clockMinute = -1
    /** Road of the next turn (OSRM steps only); null = unknown, hide it. */
    @JvmField var nextRoad: String? = null

    // ---- vehicle sensors. No source exists yet (the bike sends none to the phone, see
    // docs/RE-VEHICLE-TELEMETRY.md); a future BLE TPMS sensor / OBD adapter will write them.
    // Null = no sensor: renderers hide the element entirely (no "--").
    @JvmField var tyreFrontBar: Float? = null
    @JvmField var tyreRearBar: Float? = null
    @JvmField var tyreFrontC: Float? = null
    @JvmField var tyreRearC: Float? = null
    @JvmField var coolantC: Float? = null

    // ---- derived extras (see DashExtra). NaN = unknown. Fuel figures need the maintenance book.
    /** Trip km ÷ moving time; NaN under a minute of riding. */
    @JvmField var avgSpeedKmh = Float.NaN
    /** Average km/L from full-to-full refuels; NaN with fewer than 2 usable fills. */
    @JvmField var fuelKmPerL = Float.NaN
    /** App km since the last logged refuel + the live trip; NaN when no refuel is logged. */
    @JvmField var kmSinceRefuel = Float.NaN
    /** Tank size × km/L − km since refuel; NaN unless a tank size is set and km/L is known. */
    @JvmField var rangeKm = Float.NaN

    /** True when at least one tyre or coolant value is known. */
    fun hasVehicleSensors(): Boolean =
        tyreFrontBar != null || tyreRearBar != null || tyreFrontC != null || tyreRearC != null || coolantC != null
}

/**
 * Everything the custom dash designs show, live. All of it comes from the phone (GPS, battery,
 * Bluetooth, the app's own trip log); a value that is unknown stays NaN / -1 and the view shows "--"
 * or hides it. Nothing here is read from the bike. Main thread only.
 *
 * [GpxDashUi] writes the "raw" fields (GPS, heading, turn cue); [refresh] derives the rest.
 */
object DashTelemetry : DashValues() {
    // ---- raw, written by GpxDashUi on each GPS fix / progress update
    @JvmField var liveSpeedKmh = Float.NaN
    @JvmField var fixAtElapsedMs = 0L
    @JvmField var sessionMaxKmh = 0f
    @JvmField var sessionKm = 0.0
    @JvmField var sessionMovingSec = 0

    private var bookKm = Double.NaN
    private var bookAt = 0L
    private var lastLiveKm = 0.0
    /** After a leg is saved the book catches up later: keep the ODO from dipping until it has. */
    private var holdOdoKm = Double.NaN
    private var holdUntil = 0L
    private var btAt = 0L
    private var receiverUsers = 0
    private var batteryReceiver: BroadcastReceiver? = null
    private var tz: TimeZone = TimeZone.getDefault()
    // Fuel: recomputed only when the book changed (bind, trip saved, refuel logged), not per tick.
    private var fuelSig = Long.MIN_VALUE
    private var bookAppKm = 0.0
    private var lastFuelAppKm = Double.NaN
    private var tankLiters = Float.NaN

    /** New dash session: forget the previous ride's maximum and cue. */
    fun reset() {
        liveSpeedKmh = Float.NaN
        fixAtElapsedMs = 0L
        sessionMaxKmh = 0f
        sessionKm = 0.0
        sessionMovingSec = 0
        headingDeg = Float.NaN
        altitudeM = Float.NaN
        nextTurnDir = 0
        nextTurnMeters = -1
        bookAt = 0L
        btAt = 0L
        odometerKm = Double.NaN
        holdOdoKm = Double.NaN
        lastLiveKm = 0.0
        routeActive = false
        cardDir = 0
        nextRoad = null
        fuelSig = Long.MIN_VALUE
        tz = TimeZone.getDefault()
    }

    /** Route strip: arrival time, minutes and km left. [etaSec] < 0 = unknown. */
    fun setRoute(etaSec: Long?, remainM: Double) {
        routeActive = true
        etaMinutes = if (etaSec != null && etaSec >= 0) ((etaSec + 30) / 60).toInt() else -1
        remainKm = (remainM / 1000.0).toFloat()
        if (etaSec != null && etaSec >= 0) {
            val now = System.currentTimeMillis()
            etaClockMinute = ((((now + tz.getOffset(now)) / 1000L + etaSec) / 60L) % 1440L).toInt()
        } else {
            etaClockMinute = -1
        }
    }

    fun clearRoute() {
        routeActive = false
        cardDir = 0
        nextRoad = null
    }

    /** Called by [GpxDashUi.applyProgress]: only a plain left/right turn lights an arrow. */
    fun setTurn(dir: GpxNav.TurnDir?, meters: Double, road: String? = null) {
        nextRoad = if (dir == null) null else road?.takeIf { it.isNotBlank() }
        cardDir = when (dir) {
            null -> 0
            GpxNav.TurnDir.LEFT, GpxNav.TurnDir.SHARP_LEFT, GpxNav.TurnDir.SLIGHT_LEFT -> 1
            GpxNav.TurnDir.RIGHT, GpxNav.TurnDir.SHARP_RIGHT, GpxNav.TurnDir.SLIGHT_RIGHT -> 2
            else -> 3
        }
        cardMeters = if (cardDir != 0) meters.toInt() else -1
        nextTurnDir = when (dir) {
            GpxNav.TurnDir.LEFT, GpxNav.TurnDir.SHARP_LEFT, GpxNav.TurnDir.SLIGHT_LEFT -> 1
            GpxNav.TurnDir.RIGHT, GpxNav.TurnDir.SHARP_RIGHT, GpxNav.TurnDir.SLIGHT_RIGHT -> 2
            else -> 0
        }
        nextTurnMeters = if (nextTurnDir != 0) meters.toInt() else -1
    }

    /** Start listening for the phone battery level (sticky broadcast; paired with [stopBattery]). */
    fun startBattery(ctx: Context) {
        if (receiverUsers++ > 0) return
        val app = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { readBattery(i) }
        }
        batteryReceiver = r
        try {
            readBattery(app.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        } catch (_: Exception) {
            batteryReceiver = null
        }
    }

    fun stopBattery(ctx: Context) {
        if (receiverUsers == 0) return
        if (--receiverUsers > 0) return
        batteryReceiver?.let { try { ctx.applicationContext.unregisterReceiver(it) } catch (_: Exception) {} }
        batteryReceiver = null
    }

    private fun readBattery(i: Intent?) {
        i ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        phoneBatteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private const val FIX_FRESH_MS = 5_000L

    /** Derive the shown values from the raw ones, the trip recorder and the phone. Cheap; ~4 Hz. */
    fun refresh(ctx: Context) {
        val nowEl = SystemClock.elapsedRealtime()
        val snap = TripLogger.current?.takeIf { it.recording }?.snapshot()
        recording = snap != null

        // GPS: the location listener skips updates under 1.5 m, so a stopped bike gets no callbacks.
        // Fix freshness is judged on our own last GPS fix, not TripRecorder.hasFix (it stays true
        // until recording stops, so a tunnel would freeze the speed).
        val age = if (fixAtElapsedMs == 0L) Long.MAX_VALUE else nowEl - fixAtElapsedMs
        val fresh = age < FIX_FRESH_MS
        gpsFix = fresh
        speedKmh = when {
            !fresh -> Float.NaN
            !liveSpeedKmh.isNaN() -> liveSpeedKmh
            snap != null -> snap.speedKmh.toFloat()
            else -> Float.NaN
        }
        if (snap != null) {
            tripKm = (snap.distanceMeters / 1000.0).toFloat()
            rideSeconds = (snap.movingTimeMs / 1000L).toInt()
            maxSpeedKmh = snap.maxSpeedMs * 3.6f
        } else if (fixAtElapsedMs != 0L) {
            tripKm = sessionKm.toFloat()
            rideSeconds = sessionMovingSec
            maxSpeedKmh = sessionMaxKmh
        } else {
            tripKm = Float.NaN
            rideSeconds = -1
            maxSpeedKmh = Float.NaN
        }

        crashArmed = CrashDetector.enabled(ctx)

        // Maintenance book: re-read every 30 s, or at once when the live leg was saved (km dropped).
        val liveKm = if (snap != null) snap.distanceMeters / 1000.0 else 0.0
        if (liveKm < lastLiveKm - 0.01 && !odometerKm.isNaN()) {
            holdOdoKm = odometerKm
            holdUntil = nowEl + 90_000L
        }
        val holding = !holdOdoKm.isNaN()
        if (bookAt == 0L || nowEl - bookAt > (if (holding) 1_000L else 30_000L)) {
            bookAt = nowEl
            val b = Maintenance.load(ctx)
            bookKm = b.odometer ?: b.appKm
            odometerFromBook = b.odometer != null
            tankLiters = DashPrefs.tankLiters(ctx) ?: Float.NaN
            val sig = b.log.size * 1_000_003L + java.lang.Double.doubleToLongBits(b.appKm)
            if (sig != fuelSig) {
                fuelSig = sig
                bookAppKm = b.appKm
                val l100 = Maintenance.consumption(b)?.first
                fuelKmPerL = if (l100 != null && l100 > 0.0) (100.0 / l100).toFloat() else Float.NaN
                lastFuelAppKm = b.log.filter { it.kind == "fuel" }.maxByOrNull { it.at }?.km ?: Double.NaN
            }
        }
        kmSinceRefuel = if (lastFuelAppKm.isNaN()) Float.NaN
        else (bookAppKm + liveKm - lastFuelAppKm).coerceAtLeast(0.0).toFloat()
        rangeKm = if (tankLiters.isNaN() || fuelKmPerL.isNaN() || kmSinceRefuel.isNaN()) Float.NaN
        else (tankLiters * fuelKmPerL - kmSinceRefuel).coerceAtLeast(0f)
        avgSpeedKmh = if (tripKm.isNaN() || rideSeconds < 60) Float.NaN else tripKm * 3600f / rideSeconds
        lastLiveKm = liveKm
        var odo = bookKm + liveKm
        if (holding) {
            // Leg saved but the book has not added it yet: never show less than before.
            if (odo >= holdOdoKm - 0.001 || nowEl > holdUntil) holdOdoKm = Double.NaN
            else odo = holdOdoKm
        }
        odometerKm = odo

        // Bluetooth profile state: polled every 10 s (it walks the bonded devices).
        if (btAt == 0L || nowEl - btAt > 10_000L) {
            btAt = nowEl
            headsetConnected = try { BluetoothHelper.status(ctx).connected } catch (_: Exception) { false }
        }

        val now = System.currentTimeMillis()
        clockMinute = (((now + tz.getOffset(now)) / 60_000L) % 1440L).toInt()
    }
}
