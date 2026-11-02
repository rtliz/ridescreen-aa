// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Design #10 "Easy-read list" (`mockups/dash-designs.html` #10): a light background with black text
 * for direct sun. Speed is the first row, then one row per chosen extra, in two columns of up to
 * four rows (the left column fills first). Unknown optional extras are skipped, so there are no
 * gaps. Heading reads "047° NE".
 */
class InfoListRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f
    private val sSpeed = context.getString(R.string.dash_speed)

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    private fun headingDeg(t: DashValues): Int =
        if (t.headingDeg.isNaN()) -1 else (Math.round(t.headingDeg) % 360 + 360) % 360

    override fun signature(t: DashValues): Long {
        var h = 67L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + headingDeg(t)
        h = h * 31 + sl.signature(t)
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        val n = sl.collect(t)
        val total = minOf(n + 1, 8)
        val leftCount = minOf((total + 1) / 2, 4)
        val colW = 448f * sx
        val rowH = 86f * sy
        val top0 = (425f * sy - leftCount * rowH) / 2f
        for (i in 0 until total) {
            val col = if (i < leftCount) 0 else 1
            val x = (if (col == 0) 36f else 540f) * sx
            val top = top0 + (if (col == 0) i else i - leftCount) * rowH
            if (i == 0) {
                k.buf.setLength(0)
                if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
                row(c, sSpeed, k.sKmh, x, top, colW, rowH, C_SPEED)
            } else {
                val e = k.extras[sl.vis[i - 1]]
                if (e == DashExtra.HEADING) heading(t) else sl.value(e, t)
                row(c, k.text.label(e), k.text.unit(e), x, top, colW, rowH, C_TEXT)
            }
        }
    }

    /** "047° NE"; "--" when the heading is unknown. */
    private fun heading(t: DashValues) {
        val hd = headingDeg(t)
        val b = k.buf
        b.setLength(0)
        if (hd < 0) { b.append("--"); return }
        if (hd < 100) b.append('0')
        if (hd < 10) b.append('0')
        b.append(hd).append("° ").append(DashFmt.COMPASS[DashFmt.compassIndex(hd.toFloat())])
    }

    /** One row: label left, [DashKit.buf] + unit right, a 2 px rule under it. */
    private fun row(c: Canvas, label: String, unit: String?, x: Float, top: Float, w: Float, h: Float, color: Int) {
        val pad = 6f * s
        val base = top + h / 2f + 0.34f * 62f * s
        k.pLabel.textSize = 28f * s; k.pLabel.color = C_LABEL; k.pLabel.textAlign = Paint.Align.LEFT
        val lw = minOf(k.pLabel.measureText(label), w * 0.5f)
        k.drawFitted(c, label, x + pad, top + h / 2f + 10f * s, w * 0.5f, k.pLabel)
        sl.numUnitRight(c, k.pNum, x + w - pad, base, 62f * s, color, unit, 24f * s, C_LABEL, 8f * s, w - 2f * pad - lw - 14f * s)
        k.pFill.color = C_RULE
        c.drawRect(x, top + h - 2f * s, x + w, top + h, k.pFill)
    }

    private companion object {
        const val C_BG = 0xFFF2F0EA.toInt()
        const val C_TEXT = 0xFF14181C.toInt()
        const val C_SPEED = 0xFF8A3F00.toInt()
        const val C_LABEL = 0xFF3A434B.toInt()
        const val C_RULE = 0xFFD5D0C4.toInt()
    }
}
