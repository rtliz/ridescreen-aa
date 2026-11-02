// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Design #19 "Liquid-fill sphere" (`mockups/dash-designs.html` #19): a glass sphere whose water level
 * is speed/180 (0 at the bottom of the sphere, 180 at the top) with two wave layers and a few bubbles,
 * the speed in white over it, and a level axis (0, 60, 120, 180) on the right. The wave is one
 * pre-built Path that is only translated; its gradient is built in the wave's own space so it
 * follows. Up to two extras are small readouts in the bottom corners.
 */
class LiquidRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val clip = Path().apply { addCircle(512f, 212f, 194f, Path.Direction.CW) }
    private val wave = Path().apply {
        moveTo(306f, 0f); quadTo(376f, -16f, 446f, 0f); quadTo(516f, 16f, 586f, 0f); quadTo(656f, -16f, 726f, 0f)
        lineTo(726f, 500f); lineTo(306f, 500f); close()
    }
    private val pWater = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, -16f, 0f, 380f, intArrayOf(0xFF46F0D2.toInt(), 0xFF14B8D8.toInt(), 0xFF0A5BC4.toInt()),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pGlass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(453f, 114f, 314f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }
    private val bubbles = floatArrayOf(
        420f, 360f, 7f, 470f, 330f, 4f, 560f, 372f, 9f, 610f, 340f, 5f, 520f, 392f, 4f, 650f, 380f, 6f, 380f, 330f, 5f,
    )
    private val shine = RectF(422f - 70f, 102f - 26f, 422f + 70f, 102f + 26f)
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val pOutline = Paint(pNum).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; color = 0xFF021428.toInt(); strokeJoin = Paint.Join.ROUND
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (107L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        val v = sl.speedInt(t)
        val level = 408f - (if (v < 0) 0 else v.coerceAtMost(180)) * (392f / 180f)

        k.pFill.color = 0xFF071A33.toInt()
        c.drawCircle(512f, 212f, 196f, k.pFill)
        c.save()
        c.clipPath(clip)
        pWater.alpha = 110
        c.save(); c.translate(0f, level + 10f); c.drawPath(wave, pWater); c.restore()
        pWater.alpha = 255
        c.save(); c.translate(0f, level); c.drawPath(wave, pWater); c.restore()
        k.pStroke.color = 0xCCC8FFF3.toInt(); k.pStroke.strokeWidth = 2f
        var i = 0
        while (i < bubbles.size) {
            if (bubbles[i + 1] > level + 12f) c.drawCircle(bubbles[i], bubbles[i + 1], bubbles[i + 2], k.pStroke)
            i += 3
        }
        c.drawCircle(512f, 212f, 196f, pGlass)
        k.pStroke.color = 0xFF8FD8FF.toInt(); k.pStroke.strokeWidth = 4f
        c.drawCircle(512f, 212f, 196f, k.pStroke)
        c.save(); c.rotate(-30f, 422f, 102f)
        k.pFill.color = 0x1AFFFFFF
        c.drawOval(shine, k.pFill)
        c.restore()

        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pNum.textSize = 168f; pOutline.textSize = 168f; pNum.color = C_WHITE
        c.drawText(k.buf, 0, k.buf.length, 512f, 262f, pOutline)
        c.drawText(k.buf, 0, k.buf.length, 512f, 262f, pNum)
        // Unit: dark on the water, light when the water is below it.
        pNum.textSize = 30f; pNum.letterSpacing = 0.17f
        pNum.color = if (level < 300f) 0xFF021428.toInt() else C_AXIS
        c.drawText(DashSlots.UNIT, 512f, 333f, pNum)
        pNum.letterSpacing = 0f
        c.restore()

        k.pStroke.color = 0xFF8FD8FF.toInt(); k.pStroke.strokeWidth = 3f
        pNum.textAlign = Paint.Align.LEFT; pNum.textSize = 20f; pNum.color = C_AXIS
        for (m in 0..3) {
            val y = 408f - m * (392f / 3f)
            c.drawLine(722f, y, 744f, y, k.pStroke)
            c.drawText(AXIS[m], 752f, y + 7f, pNum)
        }
        val n = sl.two(t)
        if (n >= 1) sl.readout(c, pNum, k.extras[sl.vis[0]], t, 30f, 404f, 24f, 0, C_WHITE, C_AXIS, 270f)
        if (n >= 2) sl.readout(c, pNum, k.extras[sl.vis[1]], t, 994f, 404f, 24f, 2, C_WHITE, C_AXIS, 230f)
        sl.end(c)
    }

    private companion object {
        val AXIS = arrayOf("0", "60", "120", "180")
        const val C_BG = 0xFF03101F.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_AXIS = 0xFF9FD0F0.toInt()
    }
}
