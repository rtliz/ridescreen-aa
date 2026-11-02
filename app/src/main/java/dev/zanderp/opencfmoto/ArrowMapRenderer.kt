// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Design #3 "Big arrow + map" (`mockups/dash-designs.html` #3): a 396 px panel on the left with the
 * next turn (label, big arrow, distance), the road name and arrival / minutes / km left; the map on
 * the right (x 398–1024) with speed bottom-left and a status pill (GPS + chosen extras) top-right.
 * Without a route the panel says so and shows "--"; nothing is invented.
 */
class ArrowMapRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private var s = 1f

    private val sNext = context.getString(R.string.dash_next)
    private val sNoRoute = context.getString(R.string.dash_no_route)
    private val sLeft = context.getString(R.string.dash_dir_left)
    private val sRight = context.getString(R.string.dash_dir_right)
    private val sStraight = context.getString(R.string.dash_dir_straight)

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long {
        var h = 13L
        h = h * 31 + (if (t.routeActive) 1 else 0)
        if (t.routeActive) {
            h = h * 31 + t.cardDir
            h = h * 31 + DashFmt.turnBucket(t.cardMeters)
            h = h * 31 + (t.nextRoad?.hashCode() ?: 0)
            h = h * 31 + t.etaClockMinute
            h = h * 31 + t.etaMinutes
            h = h * 31 + (if (t.remainKm.isNaN()) -1 else DashFmt.tenths(t.remainKm))
        }
        return h
    }

    override fun overSignature(t: DashValues): Long {
        var h = 19L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + k.pillSignature(t)
        return h
    }

    // ------------------------------------------------------------------ left panel

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        k.pFill.color = C_RULE
        c.drawRect(PANEL_W * sx, 0f, MAP_X * sx, 425f * sy, k.pFill)
        val l = 24f * sx
        val r = (PANEL_W - 24f) * sx
        val turn = t.routeActive && t.cardDir != 0 && t.cardMeters >= 0

        // Label: "ถัดไป · เลี้ยวขวา" / "ไม่ได้นำทาง".
        k.buf.setLength(0)
        if (!t.routeActive) k.buf.append(sNoRoute)
        else {
            k.buf.append(sNext)
            if (turn) k.buf.append(" · ").append(if (t.cardDir == 1) sLeft else if (t.cardDir == 2) sRight else sStraight)
        }
        k.pLabel.textSize = 22f * s; k.pLabel.textAlign = Paint.Align.LEFT
        k.pLabel.color = if (t.routeActive) DashKit.C_AMBER else DashKit.C_SEC
        c.drawText(k.buf, 0, k.buf.length, l, 49f * sy, k.pLabel)

        // Arrow + distance.
        val icon = 128f * s
        val midY = 178f * sy
        k.turnIcon(c, l, midY - icon / 2f, icon, if (turn) t.cardDir else 3, if (turn) DashKit.C_AMBER else C_OFF, 2.8f)
        k.buf.setLength(0)
        val unit: String
        if (!turn) { k.buf.append("--"); unit = k.sM }
        else if (t.cardMeters < 1000) { k.buf.append(t.cardMeters); unit = k.sM }
        else {
            val tn = (t.cardMeters / 100f + 0.5f).toInt()
            k.buf.append(tn / 10).append('.').append(tn % 10); unit = k.sKm
        }
        val nx = l + icon + 14f * s
        var numSize = 132f * s
        val unitSize = 36f * s
        val avail = r - nx
        val need = k.numUnitWidth(numSize, unit, unitSize, 8f * s)
        if (need > avail) numSize *= ((avail - (need - k.numUnitWidth(numSize, null, 0f, 0f))) /
            k.numUnitWidth(numSize, null, 0f, 0f)).coerceIn(0.5f, 1f)
        k.numUnit(c, nx, midY + 0.36f * numSize, numSize, if (turn) DashKit.C_TEXT else C_OFF, unit, unitSize, 8f * s)

        // Road name, rule, arrival row.
        val road = t.nextRoad
        if (t.routeActive && road != null) {
            k.pLabel.textSize = 32f * s; k.pLabel.color = DashKit.C_TEXT; k.pLabel.textAlign = Paint.Align.LEFT
            k.drawFitted(c, road, l, 322f * sy, r - l, k.pLabel)
        }
        k.pFill.color = C_DIVIDER
        c.drawRect(l, 341f * sy, r, 343f * sy, k.pFill)
        drawEtaRow(c, t, l, r, 396f * sy)
    }

    private fun drawEtaRow(c: Canvas, t: DashValues, l: Float, r: Float, base: Float) {
        val big = 38f * s
        val small = 18f * s
        val sp = 6f * s
        val route = t.routeActive
        // Left: "ถึง 14:32".
        k.pLabel.textSize = small; k.pLabel.color = DashKit.C_SEC; k.pLabel.textAlign = Paint.Align.LEFT
        c.drawText(k.sArrive, l, base, k.pLabel)
        val ax = l + k.pLabel.measureText(k.sArrive) + sp
        k.buf.setLength(0)
        if (route) DashFmt.clock(k.buf, t.etaClockMinute, k.is24h) else k.buf.append("--")
        val leftEnd = k.numUnit(c, ax, base, big, DashKit.C_BATT, null, small, sp)
        // Right: "12.4 กม.".
        k.buf.setLength(0)
        if (!route || t.remainKm.isNaN()) k.buf.append("--") else DashFmt.oneDecimal(k.buf, t.remainKm)
        val rw = k.numUnitWidth(big, k.sKm, small, sp)
        val rightStart = r - rw
        k.numUnit(c, rightStart, base, big, DashKit.C_TEXT, k.sKm, small, sp)
        // Centre: "18 นาที", centred in the space between.
        k.buf.setLength(0)
        if (!route || t.etaMinutes < 0) k.buf.append("--") else k.buf.append(t.etaMinutes)
        val mw = k.numUnitWidth(big, k.sMin, small, sp)
        k.numUnit(c, (leftEnd + rightStart - mw) / 2f, base, big, DashKit.C_TEXT, k.sMin, small, sp)
    }

    // ------------------------------------------------------------------ over the map

    override fun drawOver(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val h = 425f * sy
        k.cornerPill(c, t, 1024f * sx - 18f * s, 18f * s, 50f * s, s, 38f * s)
        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val numSize = 84f * s
        val inner = k.numUnitWidth(numSize, k.sKmh, 20f * s, 8f * s)
        val l = MAP_X * sx + 18f * s
        val b = h - 18f * s
        val top = b - (8f + 84f + 10f) * s
        k.card(c, l, top, l + 36f * s + inner, b, 16f * s)
        k.numUnit(c, l + 18f * s, b - 18f * s, numSize, DashKit.C_TEXT, k.sKmh, 20f * s, 8f * s)
    }

    private companion object {
        const val PANEL_W = 396f
        const val MAP_X = 398f
        const val C_BG = 0xFF0B0E11.toInt()
        const val C_RULE = 0xFF1B2127.toInt()
        const val C_DIVIDER = 0xFF2A3138.toInt()
        const val C_OFF = 0xFF3A434C.toInt()
    }
}
