// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Design #6 "Sport / track" (`mockups/dash-designs.html` #6): a 30-segment slanted light bar across
 * the top over 0–180 km/h (lime, amber, red zones), the speed in italic at 290 px on the left and up
 * to three slanted tiles on the right for the chosen extras. Tiles stack from the top; an unknown
 * optional extra is skipped without a gap.
 */
class SportRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val pItalic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    }
    private val pBigItalic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        letterSpacing = -0.01f
    }
    private val segPath = Array(SEGS) { Path() }
    private val segLit = IntArray(SEGS)
    private val segDim = IntArray(SEGS)
    private val tilePath = Array(TILES) { Path() }
    private val stripPath = Array(TILES) { Path() }
    private val tileColor = intArrayOf(C_RED, C_WHITE, C_LIME)

    init {
        for (i in 0 until SEGS) {
            val col = if (i < 14) C_LIME else if (i < 22) C_AMBER else C_SEG_RED
            segLit[i] = col
            segDim[i] = (col and 0x00FFFFFF) or (0x38 shl 24)   // 22 % like the mockup
        }
    }

    override fun onScale(sx: Float, sy: Float) {
        s = minOf(sx, sy)
        val left = 28f * sx
        val gap = 6f * sx
        val w = (1024f * sx - 56f * sx - gap * (SEGS - 1)) / SEGS
        val off = 5.46f * s   // tan(20°) × half the 30 px height
        val top = 20f * sy
        val bot = 50f * sy
        for (i in 0 until SEGS) {
            val l = left + i * (w + gap)
            para(segPath[i], l, top, l + w, bot, off)
        }
        val tl = (1024f - 26f - 370f) * sx
        val tr = (1024f - 26f) * sx
        val toff = 10.2f * s   // tan(12°) × half the 96 px height
        for (j in 0 until TILES) {
            val tt = (84f + j * 112f) * sy
            val tb = tt + 96f * sy
            para(tilePath[j], tl, tt, tr, tb, toff)
            para(stripPath[j], tl, tt, tl + 6f * s, tb, toff)
        }
    }

    /** Parallelogram leaning right: the top edge shifted by +[off], the bottom by -[off]. */
    private fun para(p: Path, l: Float, t: Float, r: Float, b: Float, off: Float) {
        p.reset()
        p.moveTo(l + off, t); p.lineTo(r + off, t); p.lineTo(r - off, b); p.lineTo(l - off, b); p.close()
    }

    override fun signature(t: DashValues): Long {
        var h = 47L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + sl.signature(t)
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        val lit = sl.litSegments(t.speedKmh, SEGS)
        for (i in 0 until SEGS) {
            k.pFill.color = if (i < lit) segLit[i] else segDim[i]
            c.drawPath(segPath[i], k.pFill)
        }
        drawSpeed(c, t, sx, sy)
        val n = sl.collect(t)
        var j = 0
        for (m in 0 until n) {
            if (j == TILES) break
            drawTile(c, k.extras[sl.vis[m]], t, j, sx, sy)
            j++
        }
    }

    private fun drawSpeed(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        var size = 290f * s
        pBigItalic.textSize = size
        val maxW = 590f * sx
        val w0 = pBigItalic.measureText(k.buf, 0, k.buf.length)
        if (w0 > maxW) { size *= maxW / w0; pBigItalic.textSize = size }
        val cx = 310f * sx
        pBigItalic.color = C_LIME; pBigItalic.textAlign = Paint.Align.CENTER
        c.drawText(k.buf, 0, k.buf.length, cx, 310f * sy, pBigItalic)
        k.pLabel.textSize = 28f * s; k.pLabel.color = C_UNIT; k.pLabel.textAlign = Paint.Align.CENTER
        k.pLabel.letterSpacing = 0.07f
        c.drawText(k.sKmh, cx, 372f * sy, k.pLabel)
        k.pLabel.letterSpacing = 0f
    }

    private fun drawTile(c: Canvas, e: DashExtra, t: DashValues, j: Int, sx: Float, sy: Float) {
        k.pFill.color = C_TILE
        c.drawPath(tilePath[j], k.pFill)
        k.pFill.color = tileColor[j]
        c.drawPath(stripPath[j], k.pFill)
        val l = (1024f - 26f - 370f) * sx
        val r = (1024f - 26f) * sx
        val top = (84f + j * 112f) * sy
        k.pLabel.textSize = 20f * s; k.pLabel.color = C_LABEL; k.pLabel.textAlign = Paint.Align.LEFT
        k.drawFitted(c, k.text.label(e), l + 30f * s, top + 28f * s, r - l - 52f * s, k.pLabel)
        sl.value(e, t)
        sl.numUnitRight(
            c, pItalic, r - 22f * s, top + 82f * s, 54f * s, tileColor[j],
            k.text.unit(e), 20f * s, C_LABEL, 8f * s, r - l - 52f * s,
        )
    }

    private companion object {
        const val SEGS = 30
        const val TILES = 3
        const val C_BG = 0xFF0A0A0B.toInt()
        const val C_TILE = 0xFF16181B.toInt()
        const val C_LIME = 0xFFD7FF3A.toInt()
        const val C_AMBER = 0xFFFFB020.toInt()
        const val C_RED = 0xFFFF6A5C.toInt()
        const val C_SEG_RED = 0xFFFF4B3E.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_LABEL = 0xFFC9D1D8.toInt()
        const val C_UNIT = 0xFFE6EAED.toInt()
    }
}
