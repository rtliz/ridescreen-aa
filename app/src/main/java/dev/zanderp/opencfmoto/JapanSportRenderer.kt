// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Design #16 "Japanese sport sweep bar" (`mockups/dash-designs.html` #16): 40 slanted bar cells
 * across the top over 0–180 km/h, yellow to red, giant slanted white digits, and corner wedges.
 * Up to two extras are small readouts on the scale row, left and right.
 */
class JapanSportRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val cells = Array(CELLS) { i ->
        val x = 42f + i * 24.1f
        Path().apply { moveTo(x, 22f); lineTo(x + 22f, 22f); lineTo(x + 10f, 84f); lineTo(x - 12f, 84f); close() }
    }
    private val cellColor = IntArray(CELLS) { sl.lerp(0xFFFFD400.toInt(), 0xFFFF2900.toInt(), it / (CELLS - 1f)) }
    private val wedgeRed1 = Path().apply { moveTo(0f, 425f); lineTo(0f, 230f); lineTo(260f, 425f); close() }
    private val wedgeRed2 = Path().apply { moveTo(1024f, 425f); lineTo(1024f, 260f); lineTo(770f, 425f); close() }
    private val wedgeYellow = Path().apply { moveTo(0f, 425f); lineTo(0f, 330f); lineTo(130f, 425f); close() }
    private val pDigits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER; textSkewX = -0.2126f; color = 0xFFFFFFFF.toInt()
    }
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (97L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        k.pFill.color = C_WEDGE
        c.drawPath(wedgeRed1, k.pFill)
        c.drawPath(wedgeRed2, k.pFill)
        k.pFill.color = C_YELLOW
        c.drawPath(wedgeYellow, k.pFill)

        val v = sl.speedInt(t)
        val lit = if (v < 0) 0 else (v * CELLS / 180f + 0.5f).toInt().coerceIn(0, CELLS)
        for (i in 0 until CELLS) {
            k.pFill.color = if (i < lit) cellColor[i] else C_OFF
            c.drawPath(cells[i], k.pFill)
        }

        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pDigits.textSize = 340f
        c.drawText(k.buf, 0, k.buf.length, 512f, 372f, pDigits)
        pSmall.textSize = 40f; pSmall.color = C_YELLOW; pSmall.textAlign = Paint.Align.RIGHT
        c.drawText(DashSlots.UNIT, 990f, 400f, pSmall)
        pSmall.textSize = 24f; pSmall.color = C_SCALE
        pSmall.textAlign = Paint.Align.LEFT
        c.drawText("0", 34f, 124f, pSmall)
        pSmall.textAlign = Paint.Align.RIGHT
        c.drawText("180", 990f, 124f, pSmall)

        val n = sl.four(t)
        if (n >= 1) sl.readout(c, pSmall, k.extras[sl.vis[0]], t, 110f, 124f, 24f, 0, C_READ, C_SCALE, 330f)
        if (n >= 2) sl.readout(c, pSmall, k.extras[sl.vis[1]], t, 900f, 124f, 24f, 2, C_READ, C_SCALE, 330f)
        // Slots 3 and 4 (owner, 2 Nov 2026: room for more than two readouts).
        if (n >= 3) sl.readout(c, pSmall, k.extras[sl.vis[2]], t, 110f, 160f, 24f, 0, C_READ, C_SCALE, 250f)
        if (n >= 4) sl.readout(c, pSmall, k.extras[sl.vis[3]], t, 900f, 160f, 24f, 2, C_READ, C_SCALE, 200f)
        sl.end(c)
    }

    private companion object {
        const val CELLS = 40
        const val C_BG = 0xFF090B10.toInt()
        const val C_WEDGE = 0xFFC62300.toInt()
        const val C_YELLOW = 0xFFFFD400.toInt()
        const val C_OFF = 0xFF1B212B.toInt()
        const val C_SCALE = 0xFF8A94A3.toInt()
        const val C_READ = 0xFFFFFFFF.toInt()
    }
}
