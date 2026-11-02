// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Design #17 "Minimal Swiss" (`mockups/dash-designs.html` #17): a cream background, one thin ring
 * that fills clockwise from 12 o'clock as the speed rises (0–180 km/h over the full circle, 2° per
 * km/h) with a red dot at its end, and big light-weight digits. Up to two extras are small readouts
 * under the unit, centred.
 */
class SwissRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val ring = RectF(512f - 190f, 212f - 190f, 512f + 190f, 212f + 190f)
    private val tickThick = FloatArray(3 * 4)
    private val tickThin = FloatArray(15 * 4)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER; letterSpacing = -0.03f
    }
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    init {
        var a = 0; var b = 0
        for (i in 0 until 18) {
            if (i % 6 == 0) { sl.ticks(tickThick, a++, 1, i * 20.0, 0.0, 512f, 212f, 178f, 164f) }
            else { sl.ticks(tickThin, b++, 1, i * 20.0, 0.0, 512f, 212f, 178f, 170f) }
        }
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (101L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        k.pStroke.color = C_RING; k.pStroke.strokeWidth = 3f
        c.drawCircle(512f, 212f, 190f, k.pStroke)
        k.pStroke.color = C_TICK
        k.pStroke.strokeWidth = 2f; k.pStroke.strokeCap = Paint.Cap.BUTT
        c.drawLines(tickThick, k.pStroke)
        k.pStroke.strokeWidth = 1.2f
        c.drawLines(tickThin, k.pStroke)
        k.pStroke.strokeCap = Paint.Cap.ROUND

        val v = sl.speedInt(t)
        val ang = (if (v < 0) 0 else v.coerceAtMost(180)) * 2f
        if (ang > 0f) {
            k.pStroke.color = C_INK; k.pStroke.strokeWidth = 3f
            c.drawArc(ring, -90f, ang, false, k.pStroke)
        }
        val r = Math.toRadians(ang.toDouble())
        k.pFill.color = C_RED
        c.drawCircle(512f + (Math.sin(r) * 190).toFloat(), 212f - (Math.cos(r) * 190).toFloat(), 11f, k.pFill)

        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pText.textSize = 250f; pText.color = C_INK
        c.drawText(k.buf, 0, k.buf.length, 512f, 282f, pText)
        pSmall.textSize = 22f; pSmall.color = C_UNIT; pSmall.textAlign = Paint.Align.CENTER; pSmall.letterSpacing = 0.27f
        c.drawText(DashSlots.UNIT, 512f, 338f, pSmall)
        pSmall.letterSpacing = 0f

        val n = sl.two(t)
        if (n == 1) sl.readout(c, pSmall, k.extras[sl.vis[0]], t, 512f, 406f, 20f, 1, C_UNIT, C_UNIT, 300f)
        else if (n == 2) {
            sl.readout(c, pSmall, k.extras[sl.vis[0]], t, 496f, 406f, 20f, 2, C_UNIT, C_UNIT, 230f)
            sl.readout(c, pSmall, k.extras[sl.vis[1]], t, 528f, 406f, 20f, 0, C_UNIT, C_UNIT, 230f)
        }
        sl.end(c)
    }

    private companion object {
        const val C_BG = 0xFFF4F2EC.toInt()
        const val C_RING = 0xFFCFCBC0.toInt()
        const val C_TICK = 0xFF8D897D.toInt()
        const val C_INK = 0xFF111111.toInt()
        const val C_RED = 0xFFE30613.toInt()
        const val C_UNIT = 0xFF4A4A44.toInt()
    }
}
