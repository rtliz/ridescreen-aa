// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Design #20 "Big Thai numerals" (`mockups/dash-designs.html` #20): the speed in gold Thai digits
 * (๐–๙, mapped from the Arabic digits into a reused buffer) on a deep red background with gold
 * rings and a double border, and the small Arabic speed with the unit under it. Up to two extras are
 * small readouts in the top corners, inside the border.
 */
class ThaiNumeralRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f
    private val thai = StringBuilder(8)

    private val outer = RectF(22f, 22f, 1002f, 403f)
    private val inner = RectF(34f, 34f, 990f, 391f)
    private val pBg = Paint().apply {
        shader = RadialGradient(512f, 191f, 640f, 0xFF7C1414.toInt(), 0xFF2A0505.toInt(), Shader.TileMode.CLAMP)
    }
    private val pGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        shader = LinearGradient(
            0f, 60f, 0f, 318f, intArrayOf(0xFFFFE9A0.toInt(), 0xFFF0BE4A.toInt(), 0xFFD79A25.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeWidth = 8f
        color = 0xFF3A0606.toInt(); strokeJoin = Paint.Join.ROUND
    }
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (109L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        c.drawRect(0f, 0f, 1024f, 425f, pBg)
        k.pStroke.color = C_RING
        k.pStroke.strokeWidth = 3f; c.drawCircle(512f, 212f, 200f, k.pStroke)
        k.pStroke.strokeWidth = 2f
        c.drawCircle(512f, 212f, 170f, k.pStroke)
        c.drawCircle(512f, 212f, 140f, k.pStroke)
        k.pStroke.color = C_GOLD; k.pStroke.strokeWidth = 3f
        c.drawRoundRect(outer, 18f, 18f, k.pStroke)
        k.pStroke.color = C_GOLD_SOFT; k.pStroke.strokeWidth = 1.2f
        c.drawRoundRect(inner, 12f, 12f, k.pStroke)

        val v = sl.speedInt(t)
        k.buf.setLength(0)
        thai.setLength(0)
        if (v < 0) { k.buf.append("--"); thai.append("--") }
        else {
            k.buf.append(v)
            for (i in 0 until k.buf.length) thai.append(THAI_DIGITS[k.buf[i] - '0'])
        }
        pGold.textSize = 340f; pOutline.textSize = 340f
        c.drawText(thai, 0, thai.length, 512f, 318f, pOutline)
        c.drawText(thai, 0, thai.length, 512f, 318f, pGold)
        k.buf.append(' ').append(k.sKmh)
        pSmall.textSize = 34f; pSmall.color = 0xFFFFEFC2.toInt()
        c.drawText(k.buf, 0, k.buf.length, 512f, 372f, pSmall)

        val n = sl.four(t)
        if (n >= 1) sl.readout(c, pSmall, k.extras[sl.vis[0]], t, 56f, 74f, 26f, 0, C_READ, C_GOLD, 330f)
        if (n >= 2) sl.readout(c, pSmall, k.extras[sl.vis[1]], t, 968f, 74f, 26f, 2, C_READ, C_GOLD, 330f)
        // Slots 3 and 4 (owner, 2 Nov 2026: room for more than two readouts).
        if (n >= 3) sl.readout(c, pSmall, k.extras[sl.vis[2]], t, 56f, 372f, 26f, 0, C_READ, C_GOLD, 330f)
        if (n >= 4) sl.readout(c, pSmall, k.extras[sl.vis[3]], t, 968f, 372f, 26f, 2, C_READ, C_GOLD, 330f)
        sl.end(c)
    }

    private companion object {
        /** ๐ ๑ ๒ ๓ ๔ ๕ ๖ ๗ ๘ ๙ for 0–9. */
        val THAI_DIGITS = charArrayOf('๐', '๑', '๒', '๓', '๔', '๕', '๖', '๗', '๘', '๙')
        const val C_BG = 0xFF3E0808.toInt()
        const val C_RING = 0x73C9972B
        const val C_GOLD = 0xFFE7B84A.toInt()
        const val C_GOLD_SOFT = 0x99E7B84A.toInt()
        const val C_READ = 0xFFFFEFC2.toInt()
    }
}
