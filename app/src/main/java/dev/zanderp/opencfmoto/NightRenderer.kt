// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Design #7 "Night" (`mockups/dash-designs.html` #7): near-black background, everything in one soft
 * red so it does not dazzle. Speed at 270 px with a 20-segment bar under it; top right shows GPS,
 * the phone battery and the clock (when chosen); a turn card sits on the right only while a route
 * gives a turn cue; the other chosen extras are labelled readouts at the bottom right.
 */
class NightRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    private fun hasTurn(t: DashValues) = t.routeActive && t.cardDir != 0 && t.cardMeters >= 0

    override fun signature(t: DashValues): Long {
        var h = 53L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + (if (t.gpsFix) 1 else 0)
        h = h * 31 + sl.signature(t)
        if (hasTurn(t)) {
            h = h * 31 + t.cardDir
            h = h * 31 + DashFmt.turnBucket(t.cardMeters)
        } else h = h * 31 - 3
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        drawSpeed(c, t, sx, sy)
        drawBar(c, t, sx, sy)
        drawTopRight(c, t, sx, sy)
        if (hasTurn(t)) drawTurn(c, t, sx, sy)
        drawReadouts(c, t, sx, sy)
    }

    private fun drawSpeed(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val gap = 14f * s
        val maxW = 570f * sx
        sl.numUnitFit(c, k.pNum, 34f * sx, 236f * sy, 270f * s, C_BRIGHT, k.sKmh, 30f * s, C_RED, gap, maxW)
    }

    private fun drawBar(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val lit = sl.litSegments(t.speedKmh, SEGS)
        val left = 34f * sx
        val gap = 4f * sx
        val w = (480f * sx - gap * (SEGS - 1)) / SEGS
        val top = (425f - 26f - 10f) * sy
        val bot = (425f - 26f) * sy
        val r = 2f * s
        for (i in 0 until SEGS) {
            k.pFill.color = if (i < lit) C_RED else C_OFF
            val l = left + i * (w + gap)
            c.drawRoundRect(l, top, l + w, bot, r, r, k.pFill)
        }
    }

    /** GPS, then the battery and the clock in the order of the extras. Laid out right to left from the right edge. */
    private fun drawTopRight(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val cy = 60f * sy
        val gap = 18f * s
        val ic = 30f * s
        val n = sl.collect(t)
        var total = ic
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e != DashExtra.PHONE_BATTERY && e != DashExtra.CLOCK) continue
            total += gap + itemWidth(e, t)
        }
        var x = 1024f * sx - 34f * sx - total
        sl.gps(c, x, cy - ic / 2f, ic, if (t.gpsFix) C_RED else C_DIM)
        x += ic
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e != DashExtra.PHONE_BATTERY && e != DashExtra.CLOCK) continue
            x += gap
            sl.value(e, t)
            if (e == DashExtra.CLOCK) {
                x = sl.numUnit(c, k.pNum, x, cy + 23f * s, 64f * s, C_BRIGHT, null, 0f, 0, 0f)
            } else {
                val ih = 30f * s
                if (t.phoneBatteryPct >= 0) sl.battery(c, x, cy - ih / 2f, ih, t.phoneBatteryPct, C_RED, C_RED)
                x = sl.numUnit(c, k.pNum, x + ih * 16f / 26f + 6f * s, cy + 10f * s, 28f * s, C_RED, null, 0f, 0, 0f)
            }
        }
    }

    private fun itemWidth(e: DashExtra, t: DashValues): Float {
        sl.value(e, t)
        return if (e == DashExtra.CLOCK) sl.numWidth(k.pNum, 64f * s, null, 0f, 0f)
        else 30f * s * 16f / 26f + 6f * s + sl.numWidth(k.pNum, 28f * s, null, 0f, 0f)
    }

    private fun drawTurn(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val l = 610f * sx
        val r = 990f * sx
        val top = 150f * sy
        val b = 256f * sy
        val rad = 18f * s
        k.pFill.color = C_CARD
        k.rect.set(l, top, r, b)
        c.drawRoundRect(k.rect, rad, rad, k.pFill)
        k.pStroke.color = C_EDGE; k.pStroke.strokeWidth = 2f * s
        k.rect.set(l + s, top + s, r - s, b - s)
        c.drawRoundRect(k.rect, rad, rad, k.pStroke)
        val ic = 78f * s
        k.turnIcon(c, l + 22f * s, (top + b) / 2f - ic / 2f, ic, t.cardDir, C_BRIGHT, 2.6f)
        k.buf.setLength(0)
        val unit: String
        if (t.cardMeters < 1000) { k.buf.append(t.cardMeters); unit = k.sM }
        else {
            val tn = (t.cardMeters / 100f + 0.5f).toInt()
            k.buf.append(tn / 10).append('.').append(tn % 10); unit = k.sKm
        }
        val x = l + 22f * s + ic + 18f * s
        sl.numUnitRight(c, k.pNum, r - 20f * s, (top + b) / 2f + 25f * s, 70f * s, C_BRIGHT, unit, 26f * s, C_RED, 8f * s, r - 20f * s - x)
    }

    /** The extras that are neither the clock nor the battery, as label-over-value readouts. */
    private fun drawReadouts(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val n = sl.collect(t)
        var cnt = 0
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e != DashExtra.PHONE_BATTERY && e != DashExtra.CLOCK) cnt++
        }
        if (cnt == 0) return
        val size = if (cnt <= 2) 50f else if (cnt == 3) 40f else 32f
        val colW = 380f * sx / cnt
        val base = (425f - 34f - 4f) * sy
        var i = 0
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e == DashExtra.PHONE_BATTERY || e == DashExtra.CLOCK) continue
            val x = 610f * sx + i * colW
            k.pLabel.textSize = 18f * s; k.pLabel.color = C_RED; k.pLabel.textAlign = Paint.Align.LEFT
            k.drawFitted(c, k.text.label(e), x, base - size * s - 8f * s, colW - 8f * s, k.pLabel)
            sl.value(e, t)
            val w = sl.numWidth(k.pNum, size * s, k.text.unit(e), 20f * s, 6f * s)
            val avail = colW - 12f * s
            var sz = size * s
            if (w > avail) sz *= (avail / w).coerceIn(0.5f, 1f)
            sl.numUnit(c, k.pNum, x, base, sz, C_BRIGHT, k.text.unit(e), 20f * s, C_RED, 6f * s)
            i++
        }
    }

    private companion object {
        const val SEGS = 20
        const val C_BG = 0xFF020202.toInt()
        const val C_RED = 0xFFC65A52.toInt()
        const val C_BRIGHT = 0xFFE0584E.toInt()
        const val C_OFF = 0xFF2A0F0D.toInt()
        const val C_DIM = 0xFF5A2A27.toInt()
        const val C_CARD = 0xFF0D0505.toInt()
        const val C_EDGE = 0xFF4A1A17.toInt()
    }
}
