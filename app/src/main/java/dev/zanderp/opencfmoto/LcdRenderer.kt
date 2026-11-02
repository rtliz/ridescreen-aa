// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Design #13 "Retro 7-segment LCD" (`mockups/dash-designs.html` #13): three seven-segment digits
 * (right-aligned, no leading zero) on an olive LCD with faint ghost segments, a 30-cell bar under
 * them over 0–180 km/h, and up to two extras as small LCD text on the right. The 21 segment shapes
 * are pre-built Paths; each frame only picks lit or ghost per segment.
 */
class LcdRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    /** seg[position][segment a..g]. */
    private val seg = Array(3) { Array(7) { Path() } }
    private val cellL = FloatArray(CELLS) { 70f + it * 29.6f }
    private val frame = RectF(14f, 14f, 1010f, 411f)
    private val lcd = RectF(34f, 34f, 990f, 391f)
    private val pLcd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 34f, 0f, 391f, 0xFFC3D295.toInt(), 0xFFA9BB78.toInt(), Shader.TileMode.CLAMP)
    }
    private val pSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 34f, 0f, 391f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }
    private val pMono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    init {
        for (p in 0 until 3) {
            val ox = 84f + p * 186f
            hseg(seg[p][0], ox + 75f, 78f)    // a top
            hseg(seg[p][6], ox + 75f, 182f)   // g middle
            hseg(seg[p][3], ox + 75f, 286f)   // d bottom
            vseg(seg[p][5], ox + 16f, 122f)   // f upper left
            vseg(seg[p][1], ox + 134f, 122f)  // b upper right
            vseg(seg[p][4], ox + 16f, 226f)   // e lower left
            vseg(seg[p][2], ox + 134f, 226f)  // c lower right
        }
    }

    private fun hseg(p: Path, cx: Float, cy: Float) {
        p.moveTo(cx - 54f, cy); p.lineTo(cx - 38f, cy - 16f); p.lineTo(cx + 38f, cy - 16f)
        p.lineTo(cx + 54f, cy); p.lineTo(cx + 38f, cy + 16f); p.lineTo(cx - 38f, cy + 16f); p.close()
    }

    private fun vseg(p: Path, cx: Float, cy: Float) {
        p.moveTo(cx, cy - 39f); p.lineTo(cx + 16f, cy - 23f); p.lineTo(cx + 16f, cy + 23f)
        p.lineTo(cx, cy + 39f); p.lineTo(cx - 16f, cy + 23f); p.lineTo(cx - 16f, cy - 23f); p.close()
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (79L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        k.pFill.color = C_CASE
        c.drawRoundRect(frame, 26f, 26f, k.pFill)
        k.pStroke.color = 0xFF4A3F28.toInt(); k.pStroke.strokeWidth = 2f
        c.drawRoundRect(frame, 26f, 26f, k.pStroke)
        c.drawRoundRect(lcd, 16f, 16f, pLcd)
        k.pStroke.color = 0xFF0B0A06.toInt(); k.pStroke.strokeWidth = 4f
        c.drawRoundRect(lcd, 16f, 16f, k.pStroke)

        val v = sl.speedInt(t)
        // Three right-aligned digits; unknown speed = a dash in the last two positions.
        for (p in 0 until 3) {
            val mask = if (v < 0) (if (p == 0) 0 else MASK_DASH) else digitMask(v, p)
            for (g in 0 until 7) {
                k.pFill.color = if (mask shr g and 1 == 1) C_INK else C_GHOST
                c.drawPath(seg[p][g], k.pFill)
            }
        }

        pMono.color = C_INK; pMono.textAlign = Paint.Align.LEFT
        pMono.textSize = 22f; pMono.letterSpacing = 0.23f
        c.drawText("SPEED", 690f, 152f, pMono)
        pMono.textSize = 78f; pMono.letterSpacing = 0.03f
        c.drawText(DashSlots.UNIT, 690f, 236f, pMono)
        pMono.letterSpacing = 0f

        val n = sl.two(t)
        if (n >= 1) sl.readout(c, pMono, k.extras[sl.vis[0]], t, 690f, 276f, 32f, 0, C_INK, C_INK, 270f)
        if (n >= 2) sl.readout(c, pMono, k.extras[sl.vis[1]], t, 690f, 312f, 32f, 0, C_INK, C_INK, 270f)

        val lit = sl.litSegments(t.speedKmh, CELLS)
        for (i in 0 until CELLS) {
            k.pFill.color = if (i < lit) C_INK else C_GHOST
            c.drawRoundRect(cellL[i], 332f, cellL[i] + 24f, 366f, 2f, 2f, k.pFill)
        }
        pMono.textSize = 16f; pMono.color = C_INK
        c.drawText("0", 70f, 324f, pMono)
        pMono.textAlign = Paint.Align.RIGHT
        c.drawText("180", 880f, 324f, pMono)
        c.drawRoundRect(lcd, 16f, 16f, pSheen)
        sl.end(c)
    }

    /** Segments (bit 0 = a … bit 6 = g) lit for digit position [p] (0 = hundreds) of [v]; blank for a leading zero. */
    private fun digitMask(v: Int, p: Int): Int {
        val d = when (p) { 0 -> v / 100 % 10; 1 -> v / 10 % 10; else -> v % 10 }
        if (p == 0 && v < 100) return 0
        if (p == 1 && v < 10) return 0
        return DIGITS[d]
    }

    private companion object {
        const val CELLS = 30
        const val MASK_DASH = 1 shl 6
        /** a=1 b=2 c=4 d=8 e=16 f=32 g=64 */
        val DIGITS = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)
        const val C_BG = 0xFF241F16.toInt()
        const val C_CASE = 0xFF14110C.toInt()
        const val C_INK = 0xFF1A2410.toInt()
        const val C_GHOST = 0x1C1A2410
    }
}
