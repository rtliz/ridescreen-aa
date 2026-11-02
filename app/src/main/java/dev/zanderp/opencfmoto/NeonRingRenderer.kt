// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Design #11 "Neon ring" (`mockups/dash-designs.html` #11): 91 ticks around a 270° ring, lit from 0
 * up to the speed in a cyan to pink blend, with the speed in white in the middle. Glow is a wider,
 * low-alpha stroke under the sharp one (no blur). Up to two extras are small readouts at the bottom.
 */
class NeonRingRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f
    private var sx = 1f
    private var sy = 1f

    private val ticks = FloatArray(TICKS * 4)
    private val pBg = Paint().apply {
        shader = RadialGradient(512f, 212.5f, 600f, 0xFF150A33.toInt(), 0xFF05020F.toInt(), Shader.TileMode.CLAMP)
    }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val pDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFF1F3A55.toInt()
        pathEffect = DashPathEffect(floatArrayOf(3f, 9f), 0f)
    }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val pGlowText = Paint(pNum).apply {
        style = Paint.Style.STROKE; strokeWidth = 12f; color = 0x4000F0FF; strokeJoin = Paint.Join.ROUND
    }
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    init { sl.ticks(ticks, 0, TICKS, -135.0, 3.0, 512f, 215f, 158f, 186f) }

    override fun onScale(sx: Float, sy: Float) { this.sx = sx; this.sy = sy; s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (71L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        c.drawRect(0f, 0f, 1024f, 425f, pBg)
        k.pStroke.color = 0xFF2A1B55.toInt(); k.pStroke.strokeWidth = 2f
        c.drawCircle(512f, 215f, 206f, k.pStroke)
        c.drawCircle(512f, 215f, 138f, pDash)

        val v = sl.speedInt(t)
        val lit = if (v < 0) 0 else v / 2 + 1
        for (i in 0 until TICKS) {
            val j = i * 4
            if (i < lit) {
                val col = sl.lerp(C_CYAN, C_PINK, if (lit <= 1) 0f else i / (lit - 1f))
                pTick.color = (col and 0x00FFFFFF) or 0x40000000
                pTick.strokeWidth = 11f
                c.drawLine(ticks[j], ticks[j + 1], ticks[j + 2], ticks[j + 3], pTick)
                pTick.color = col
                pTick.strokeWidth = 5f
            } else {
                pTick.color = C_OFF; pTick.strokeWidth = 5f
            }
            c.drawLine(ticks[j], ticks[j + 1], ticks[j + 2], ticks[j + 3], pTick)
        }

        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pNum.textSize = 176f
        pGlowText.textSize = 176f
        c.drawText(k.buf, 0, k.buf.length, 512f, 270f, pGlowText)
        pNum.color = C_WHITE
        c.drawText(k.buf, 0, k.buf.length, 512f, 270f, pNum)
        pSmall.textSize = 30f; pSmall.color = C_PINK_TEXT; pSmall.textAlign = Paint.Align.CENTER
        pSmall.letterSpacing = 0.2f
        c.drawText(DashSlots.UNIT, 512f, 322f, pSmall)
        pSmall.letterSpacing = 0f
        pSmall.textSize = 20f; pSmall.color = C_SCALE
        c.drawText("0", 362f, 373f, pSmall)
        c.drawText("180", 662f, 373f, pSmall)

        val n = sl.four(t)
        if (n == 1) sl.readout(c, pSmall, k.extras[sl.vis[0]], t, 512f, 405f, 24f, 1, C_READ, C_SCALE, 300f)
        else if (n >= 2) {
            sl.readout(c, pSmall, k.extras[sl.vis[0]], t, 496f, 405f, 24f, 2, C_READ, C_SCALE, 330f)
            sl.readout(c, pSmall, k.extras[sl.vis[1]], t, 528f, 405f, 24f, 0, C_READ, C_SCALE, 330f)
        }
        // Slots 3 and 4 (owner, 2 Nov 2026: room for more than two readouts).
        if (n >= 3) sl.readout(c, pSmall, k.extras[sl.vis[2]], t, 40f, 50f, 24f, 0, C_READ, C_SCALE, 260f)
        if (n >= 4) sl.readout(c, pSmall, k.extras[sl.vis[3]], t, 984f, 50f, 24f, 2, C_READ, C_SCALE, 260f)
        sl.end(c)
    }

    private companion object {
        const val TICKS = 91
        const val C_BG = 0xFF05020F.toInt()
        const val C_CYAN = 0xFF00F0FF.toInt()
        const val C_PINK = 0xFFFF2BD6.toInt()
        const val C_PINK_TEXT = 0xFFFF6AE6.toInt()
        const val C_OFF = 0x8C3B2A78.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_SCALE = 0xFF9FB4D8.toInt()
        const val C_READ = 0xFF7FF6FF.toInt()
    }
}
