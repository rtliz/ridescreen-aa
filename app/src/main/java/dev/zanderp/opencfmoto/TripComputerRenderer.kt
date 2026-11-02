// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Design #5 "Trip computer" (`mockups/dash-designs.html` #5): a tall speed card on the left (speed
 * 200 px, status icons at the bottom) and a 2×3 grid of tiles for the chosen extras. Tiles fill
 * the grid in order; an optional extra that is unknown is skipped and the next one moves up.
 */
class TripComputerRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f
    private val sNow = context.getString(R.string.dash_speed_now)

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long {
        var h = 43L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + (if (t.gpsFix) 1 else 0)
        h = h * 31 + (if (t.recording) 1 else 0)
        h = h * 31 + (if (t.crashArmed) 1 else 0)
        h = h * 31 + (if (t.headsetConnected) 1 else 0)
        h = h * 31 + sl.signature(t)
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        drawSpeedCard(c, t, sx, sy)
        val n = sl.collect(t)
        val rowH = (385f - 2f * GAP) / 3f
        for (j in 0 until minOf(n, 6)) {
            val col = j % 2
            val row = j / 2
            val l = (if (col == 0) 404f else 710f) * sx
            val top = (20f + row * (rowH + GAP)) * sy
            drawTile(c, k.extras[sl.vis[j]], t, l, top, l + 294f * sx, top + rowH * sy)
        }
    }

    private fun drawSpeedCard(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val l = 20f * sx
        val top = 20f * sy
        val r = 392f * sx
        val b = 405f * sy
        val rad = 20f * s
        k.pFill.color = C_PANEL
        k.rect.set(l, top, r, b)
        c.drawRoundRect(k.rect, rad, rad, k.pFill)
        k.pStroke.color = C_BLUE; k.pStroke.strokeWidth = 2f * s
        k.rect.set(l + s, top + s, r - s, b - s)
        c.drawRoundRect(k.rect, rad, rad, k.pStroke)

        k.pLabel.textSize = 22f * s; k.pLabel.color = C_LABEL; k.pLabel.textAlign = Paint.Align.LEFT
        c.drawText(sNow, l + 24f * sx, top + 36f * s, k.pLabel)

        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val avail = r - l - 44f * sx
        val size = 200f * s
        val gap = 10f * s
        var w = sl.numWidth(k.pNum, size, k.sKmh, 24f * s, gap)
        var sz = size
        if (w > avail) {
            sz *= (avail / w).coerceIn(0.5f, 1f)
            w = sl.numWidth(k.pNum, sz, k.sKmh, 24f * s, gap)
        }
        sl.numUnit(c, k.pNum, (l + r - w) / 2f, (top + b) / 2f + 0.3f * sz, sz, C_WHITE, k.sKmh, 24f * s, C_LABEL, gap)

        // Status icons: GPS, recording, crash detection, headset. Off = dim, never hidden.
        val ic = 34f * s
        val ig = 18f * s
        var x = (l + r) / 2f - (4f * ic + 3f * ig) / 2f
        val y = b - 18f * s - ic
        sl.gps(c, x, y, ic, if (t.gpsFix) DashKit.C_GREEN else C_OFF)
        x += ic + ig
        sl.recording(c, x, y, ic, if (t.recording) C_RED else C_OFF)
        x += ic + ig
        sl.crashShield(c, x, y, ic, if (t.crashArmed) DashKit.C_GREEN else C_OFF)
        x += ic + ig
        sl.headset(c, x, y, ic, if (t.headsetConnected) DashKit.C_GREEN else C_OFF)
    }

    private fun drawTile(c: Canvas, e: DashExtra, t: DashValues, l: Float, top: Float, r: Float, b: Float) {
        val rad = 16f * s
        k.pFill.color = C_PANEL
        k.rect.set(l, top, r, b)
        c.drawRoundRect(k.rect, rad, rad, k.pFill)
        k.pStroke.color = C_TILE_EDGE; k.pStroke.strokeWidth = 2f * s
        k.rect.set(l + s, top + s, r - s, b - s)
        c.drawRoundRect(k.rect, rad, rad, k.pStroke)

        k.pLabel.textSize = 20f * s; k.pLabel.color = C_LABEL; k.pLabel.textAlign = Paint.Align.LEFT
        k.drawFitted(c, k.text.label(e), l + 18f * s, top + 14f * s + 18f * s, r - l - 36f * s, k.pLabel)
        sl.value(e, t)
        sl.numUnitRight(
            c, k.pNum, r - 18f * s, b - 22f * s, 64f * s,
            if (e == DashExtra.MAX_SPEED) C_AMBER else C_TEXT,
            k.text.unit(e), 22f * s, C_LABEL, 6f * s, r - l - 36f * s,
        )
    }

    private companion object {
        const val GAP = 12f
        const val C_BG = 0xFF0A0F14.toInt()
        const val C_PANEL = 0xFF121A22.toInt()
        const val C_BLUE = 0xFF4FC3F7.toInt()
        const val C_TILE_EDGE = 0xFF26323D.toInt()
        const val C_LABEL = 0xFFAEBBC7.toInt()
        const val C_TEXT = 0xFFF4F7FA.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_AMBER = 0xFFFFC857.toInt()
        const val C_RED = 0xFFE8322E.toInt()
        const val C_OFF = 0xFF3A434C.toInt()
    }
}
