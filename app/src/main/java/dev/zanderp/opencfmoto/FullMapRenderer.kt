// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Design #2 "Full map" (`mockups/dash-designs.html` #2): the map fills the screen; floating cards
 * over it: turn card + road (top-left, only with a turn ahead), status pill with GPS and the chosen
 * extras (top-right), speed (bottom-left), arrival / minutes / km left (bottom-right, only with a
 * route). Everything is drawn in [drawOver]; [draw] only paints the background under the map.
 */
class FullMapRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private var s = 1f

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
    }

    override fun signature(t: DashValues): Long = 0L

    override fun overSignature(t: DashValues): Long {
        var h = 11L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + k.pillSignature(t)
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

    override fun drawOver(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val w = 1024f * sx
        val h = 425f * sy
        if (t.routeActive && t.cardDir != 0 && t.cardMeters >= 0) drawTurnCard(c, t)
        k.cornerPill(c, t, w - 20f * s, 20f * s, 60f * s, s, 44f * s)
        drawSpeed(c, t, h)
        if (t.routeActive) drawEta(c, t, w, h)
    }

    private fun drawTurnCard(c: Canvas, t: DashValues) {
        val x0 = 20f * s
        val y0 = 20f * s
        val padL = 18f * s
        val padR = 26f * s
        val padV = 14f * s
        val icon = 84f * s
        val numSize = 88f * s
        // Distance split into number + unit.
        val unit = if (t.cardMeters < 1000) k.sM else k.sKm
        k.buf.setLength(0)
        if (t.cardMeters < 1000) k.buf.append(t.cardMeters)
        else { val tn = (t.cardMeters / 100f + 0.5f).toInt(); k.buf.append(tn / 10).append('.').append(tn % 10) }
        val rowW = icon + 16f * s + k.numUnitWidth(numSize, unit, 30f * s, 8f * s)
        val road = t.nextRoad
        k.pLabel.textSize = 24f * s
        val roadMax = MAX_CARD_W * s - padL - padR
        val roadW = if (road != null) minOf(roadMax, k.pLabel.measureText(road)) else 0f
        val cw = padL + maxOf(rowW, roadW) + padR
        val ch = padV + 88f * s + (if (road != null) 6f * s + 30f * s else 0f) + padV
        k.card(c, x0, y0, x0 + cw, y0 + ch, 18f * s)
        val rowTop = y0 + padV
        k.turnIcon(c, x0 + padL, rowTop + 2f * s, icon, t.cardDir, DashKit.C_AMBER, 2.6f)
        k.numUnit(c, x0 + padL + icon + 16f * s, rowTop + 76f * s, numSize, DashKit.C_TEXT, unit, 30f * s, 8f * s)
        if (road != null) {
            k.pLabel.textSize = 24f * s; k.pLabel.color = DashKit.C_TEXT; k.pLabel.textAlign = Paint.Align.LEFT
            k.drawFitted(c, road, x0 + padL, rowTop + 88f * s + 6f * s + 23f * s, roadMax, k.pLabel)
        }
    }

    private fun drawSpeed(c: Canvas, t: DashValues, h: Float) {
        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val numSize = 112f * s
        val inner = k.numUnitWidth(numSize, k.sKmh, 22f * s, 10f * s)
        val l = 20f * s
        val b = h - 20f * s
        val top = b - (10f + 112f + 12f) * s
        k.card(c, l, top, l + 22f * s + inner + 22f * s, b, 18f * s)
        k.numUnit(c, l + 22f * s, b - 22f * s, numSize, DashKit.C_TEXT, k.sKmh, 22f * s, 10f * s)
    }

    private fun drawEta(c: Canvas, t: DashValues, w: Float, h: Float) {
        val pad = 26f * s
        val gap = 26f * s
        val big = 40f * s
        val small = 18f * s
        val sp = 6f * s
        // Measure the three groups: "ถึง 14:32" · "18 นาที" · "12.4 กม."
        k.pLabel.textSize = small
        val arriveW = k.pLabel.measureText(k.sArrive) + sp
        k.buf.setLength(0); DashFmt.clock(k.buf, t.etaClockMinute, k.is24h)
        val g1 = arriveW + k.numUnitWidth(big, null, small, sp)
        fillMinutes(t)
        val g2 = k.numUnitWidth(big, k.sMin, small, sp)
        fillRemain(t)
        val g3 = k.numUnitWidth(big, k.sKm, small, sp)
        val cw = pad + g1 + gap + g2 + gap + g3 + pad
        val r = w - 20f * s
        val b = h - 20f * s
        val top = b - (12f + 40f + 12f) * s
        k.card(c, r - cw, top, r, b, 16f * s)
        val base = b - 19f * s
        var x = r - cw + pad
        k.pLabel.textSize = small; k.pLabel.color = DashKit.C_SEC; k.pLabel.textAlign = Paint.Align.LEFT
        c.drawText(k.sArrive, x, base, k.pLabel)
        x += arriveW
        k.buf.setLength(0); DashFmt.clock(k.buf, t.etaClockMinute, k.is24h)
        x = k.numUnit(c, x, base, big, DashKit.C_BATT, null, small, sp) + gap
        fillMinutes(t)
        x = k.numUnit(c, x, base, big, DashKit.C_TEXT, k.sMin, small, sp) + gap
        fillRemain(t)
        k.numUnit(c, x, base, big, DashKit.C_TEXT, k.sKm, small, sp)
    }

    private fun fillMinutes(t: DashValues) {
        k.buf.setLength(0)
        if (t.etaMinutes < 0) k.buf.append("--") else k.buf.append(t.etaMinutes)
    }

    private fun fillRemain(t: DashValues) {
        k.buf.setLength(0)
        if (t.remainKm.isNaN()) k.buf.append("--") else DashFmt.oneDecimal(k.buf, t.remainKm)
    }

    private companion object {
        const val C_BG = 0xFF12171C.toInt()
        /** Widest the turn card may grow (design px) before the road name is cut. */
        const val MAX_CARD_W = 560f
    }
}
