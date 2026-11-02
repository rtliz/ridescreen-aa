// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Design #15 "Synthwave sunset" (`mockups/dash-designs.html` #15): a purple to pink sky, a striped
 * half sun on the horizon, a perspective grid, and the speed in big chrome-filled digits with a dark
 * outline. The look is static; only the digits change. Up to two extras sit in the bottom corners.
 * There is no scale on this design (same as the mockup).
 */
class SynthwaveRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val grid = Path().apply {
        for (i in 0 until 29) {
            val x0 = 92f + i * 30f
            moveTo(x0, 269f); lineTo(512f + (x0 - 512f) * 6.33f, 425f)
        }
        for (y in HORIZONTALS) { moveTo(0f, y); lineTo(1024f, y) }
    }
    private val pSky = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 425f, intArrayOf(0xFF14052E.toInt(), 0xFF6A1B9A.toInt(), 0xFFFF2E88.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pSun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 128
        shader = LinearGradient(
            0f, 142f, 0f, 402f, intArrayOf(0xFFFFB02E.toInt(), 0xFFFF3C8E.toInt(), 0xFFB2189E.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pChrome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        shader = LinearGradient(
            0f, 85f, 0f, 270f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFC4F1FF.toInt(), 0xFFFFB5EC.toInt(), 0xFFFFF2B0.toInt()),
            floatArrayOf(0f, 0.5f, 0.52f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeWidth = 9f
        color = 0xFF1B0036.toInt(); strokeJoin = Paint.Join.ROUND
    }
    private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xBFFF4FD8.toInt()
    }
    private val pMono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (89L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        c.drawRect(0f, 0f, 1024f, 425f, pSky)
        c.save()
        c.clipRect(0f, 0f, 1024f, 267f)
        c.drawCircle(512f, 272f, 130f, pSun)
        k.pFill.color = C_STRIPE
        c.drawRect(300f, 214f, 724f, 217f, k.pFill)
        c.drawRect(300f, 228f, 724f, 233f, k.pFill)
        c.drawRect(300f, 242f, 724f, 249f, k.pFill)
        c.drawRect(300f, 256f, 724f, 265f, k.pFill)
        c.restore()
        k.pFill.color = C_GROUND
        c.drawRect(0f, 268f, 1024f, 425f, k.pFill)
        k.pFill.color = 0xFFFF7AD9.toInt()
        c.drawRect(0f, 266f, 1024f, 269f, k.pFill)
        c.save()
        c.clipRect(0f, 269f, 1024f, 425f)
        c.drawPath(grid, pGrid)
        c.restore()

        k.buf.setLength(0)
        val v = sl.speedInt(t)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pChrome.textSize = 250f; pOutline.textSize = 250f
        c.drawText(k.buf, 0, k.buf.length, 512f, 270f, pOutline)
        c.drawText(k.buf, 0, k.buf.length, 512f, 270f, pChrome)
        pMono.textSize = 40f; pMono.color = 0xFFFFE86B.toInt(); pMono.textAlign = Paint.Align.CENTER
        pMono.isFakeBoldText = true; pMono.letterSpacing = 0.15f
        c.drawText(DashSlots.UNIT, 512f, 322f, pMono)
        pMono.isFakeBoldText = false; pMono.letterSpacing = 0f

        val n = sl.two(t)
        if (n >= 1) sl.readout(c, pMono, k.extras[sl.vis[0]], t, 40f, 404f, 26f, 0, C_READ, C_READ, 420f)
        if (n >= 2) sl.readout(c, pMono, k.extras[sl.vis[1]], t, 984f, 404f, 26f, 2, C_READ, C_READ, 420f)
        sl.end(c)
    }

    private companion object {
        val HORIZONTALS = floatArrayOf(276f, 288f, 304f, 326f, 356f, 396f)
        const val C_BG = 0xFF14052E.toInt()
        const val C_STRIPE = 0xFF2A0A4D.toInt()
        const val C_GROUND = 0xFF120428.toInt()
        const val C_READ = 0xFFFFD1F2.toInt()
    }
}
