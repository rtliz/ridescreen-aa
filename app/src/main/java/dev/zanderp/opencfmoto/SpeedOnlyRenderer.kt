// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Design #4 "Speed only" (`mockups/dash-designs.html` #4): a black screen with the speed at 330 px,
 * a top row (clock on the left; GPS, headset and the other chosen extras on the right) and a
 * 26-segment bar along the bottom over 0–180 km/h. The clock is the CLOCK extra: when the rider
 * turns it off, the left side stays empty.
 */
class SpeedOnlyRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f
    private val hasClock = extras.contains(DashExtra.CLOCK)

    private val pBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = -0.012f
    }
    private val pBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long {
        var h = 41L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + sl.signature(t)
        h = h * 31 + (if (t.gpsFix) 1 else 0)
        h = h * 31 + (if (t.headsetConnected) 1 else 0)
        h = h * 31 + (if (t.phoneBatteryPct < 0) -1 else t.phoneBatteryPct)
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        drawTopRow(c, t, sx, sy)
        drawSpeed(c, t, sx, sy)
        drawBar(c, t, sx, sy)
    }

    private fun drawTopRow(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val cy = 44f * sy
        if (hasClock) {
            k.buf.setLength(0)
            DashFmt.clock(k.buf, t.clockMinute, k.is24h)
            pBold.textSize = 48f * s; pBold.color = C_CLOCK; pBold.textAlign = Paint.Align.LEFT
            c.drawText(k.buf, 0, k.buf.length, 30f * sx, cy + 17f * s, pBold)
        }
        val ic = 30f * s
        val gap = 20f * s
        val n = sl.collect(t)
        // Width of the right-hand group (GPS, headset, then the extras that are not the clock).
        var total = ic
        if (t.headsetConnected) total += gap + ic
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e == DashExtra.CLOCK) continue
            total += gap + itemWidth(e, t)
        }
        var x = 1024f * sx - 30f * sx - total
        sl.gps(c, x, cy - ic / 2f, ic, if (t.gpsFix) DashKit.C_GREEN else DashKit.C_GREY)
        x += ic
        if (t.headsetConnected) {
            x += gap
            sl.headset(c, x, cy - ic / 2f, ic, DashKit.C_GREEN)
            x += ic
        }
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e == DashExtra.CLOCK) continue
            x += gap
            x += drawItem(c, e, t, x, cy)
        }
    }

    private fun itemWidth(e: DashExtra, t: DashValues): Float {
        sl.value(e, t)
        return if (e == DashExtra.PHONE_BATTERY) {
            pBold.textSize = 28f * s
            30f * s * 16f / 26f + 6f * s + pBold.measureText(k.buf, 0, k.buf.length)
        } else sl.numWidth(pBold, 28f * s, k.text.unit(e), 20f * s, 6f * s)
    }

    /** Draws one extra at [x]; returns its width. */
    private fun drawItem(c: Canvas, e: DashExtra, t: DashValues, x: Float, cy: Float): Float {
        sl.value(e, t)
        if (e == DashExtra.PHONE_BATTERY) {
            val ih = 30f * s
            if (t.phoneBatteryPct >= 0) sl.battery(c, x, cy - ih / 2f, ih, t.phoneBatteryPct, DashKit.C_SEC, DashKit.C_BATT)
            val tx = x + ih * 16f / 26f + 6f * s
            val r = sl.numUnit(c, pBold, tx, cy + 10f * s, 28f * s, DashKit.C_SEC, null, 0f, 0, 0f)
            return r - x
        }
        val r = sl.numUnit(c, pBold, x, cy + 10f * s, 28f * s, DashKit.C_SEC, k.text.unit(e), 20f * s, DashKit.C_SEC, 6f * s)
        return r - x
    }

    private fun drawSpeed(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val size = 330f * s
        pBig.textSize = size
        val numW = pBig.measureText(k.buf, 0, k.buf.length)
        k.pLabel.textSize = 40f * s
        val unitW = k.pLabel.measureText(k.sKmh)
        val gap = 16f * s
        val x = (1024f * sx - (numW + gap + unitW)) / 2f
        pBig.color = C_WHITE; pBig.textAlign = Paint.Align.LEFT
        c.drawText(k.buf, 0, k.buf.length, x, 322f * sy, pBig)
        k.pLabel.color = DashKit.C_SEC; k.pLabel.textAlign = Paint.Align.LEFT
        c.drawText(k.sKmh, x + numW + gap, 136f * sy, k.pLabel)
    }

    private fun drawBar(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val lit = sl.litSegments(t.speedKmh, SEGS)
        val left = 30f * sx
        val gap = 5f * sx
        val w = (1024f * sx - 60f * sx - gap * (SEGS - 1)) / SEGS
        val top = (425f - 24f - 18f) * sy
        val bot = (425f - 24f) * sy
        val r = 3f * s
        for (i in 0 until SEGS) {
            val red = i >= SEGS_NORMAL
            k.pFill.color = if (i < lit) (if (red) C_RED else DashKit.C_AMBER) else (if (red) C_RED_OFF else C_OFF)
            val l = left + i * (w + gap)
            c.drawRoundRect(l, top, l + w, bot, r, r, k.pFill)
        }
    }

    private companion object {
        const val SEGS = 26
        const val SEGS_NORMAL = 20
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_BG = 0xFF000000.toInt()
        const val C_CLOCK = 0xFFE6EAED.toInt()
        const val C_OFF = 0xFF242B31.toInt()
        const val C_RED = 0xFFE8322E.toInt()
        const val C_RED_OFF = 0xFF4A1D1C.toInt()
    }
}
