// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Design #12 "Carbon + red needle" (`mockups/dash-designs.html` #12): a carbon-weave background
 * (one tiny repeating bitmap shader), red corner wedges, a chrome-ringed dial with a 0–180 km/h scale
 * over 270°, a red needle and the digital speed under the hub. Up to two extras sit in the top
 * corners.
 */
class CarbonRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val majorW = FloatArray(8 * 4)
    private val majorR = FloatArray(2 * 4)
    private val minorW = FloatArray(7 * 4)
    private val minorR = FloatArray(2 * 4)
    private val labelX = FloatArray(10)
    private val labelY = FloatArray(10)
    private val labelText = Array(10) { (it * 20).toString() }

    private val pCarbon = Paint().apply { shader = BitmapShader(weave(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    private val pVignette = Paint().apply {
        shader = RadialGradient(
            512f, 212.5f, 620f, intArrayOf(0x00000000, 0x00000000, 0xB3000000.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    // Gradients live in dial space (origin = dial centre) and are drawn under translate(512,212).
    private val pChrome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -208f, -208f, 208f, 208f,
            intArrayOf(0xFFF5F7F8.toInt(), 0xFF7C858C.toInt(), 0xFFE9ECEE.toInt(), 0xFF5E666C.toInt(), 0xFFD5D9DC.toInt()),
            floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pDialShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-0.1f * 382f, -0.2f * 382f, 0.8f * 382f, 0x1AFFFFFF, 0x8C000000.toInt(), Shader.TileMode.CLAMP)
    }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val needleTip = Path().apply {
        moveTo(-6f, 52f); lineTo(6f, 52f); lineTo(2f, -150f); lineTo(0f, -168f); lineTo(-2f, -150f); close()
    }
    private val needleBody = Path().apply {
        moveTo(-7f, 52f); lineTo(-7f, 0f); lineTo(-1.5f, -150f); lineTo(1.5f, -150f); lineTo(7f, 0f); lineTo(7f, 52f); close()
    }
    private val wedgeL = Path().apply { moveTo(0f, 300f); lineTo(130f, 425f); lineTo(0f, 425f); close() }
    private val wedgeR = Path().apply { moveTo(1024f, 300f); lineTo(894f, 425f); lineTo(1024f, 425f); close() }
    private val face = RectF(-191f, -191f, 191f, 191f)

    init {
        var a = 0; var b = 0; var c2 = 0; var d = 0
        for (v in 0..180 step 10) {
            val major = v % 20 == 0
            val red = v >= 150
            val ang = -135.0 + v * 1.5
            val arr = if (major) (if (red) majorR else majorW) else (if (red) minorR else minorW)
            val from = if (major) (if (red) b else a) else (if (red) d else c2)
            sl.ticks(arr, from, 1, ang, 0.0, 0f, 0f, 184f, if (major) 162f else 172f)
            if (major) { if (red) b++ else a++ } else { if (red) d++ else c2++ }
            if (major) {
                val r = Math.toRadians(ang)
                labelX[v / 20] = (Math.sin(r) * 134).toFloat()
                labelY[v / 20] = (-Math.cos(r) * 134).toFloat() + 10f
            }
        }
    }

    /** 12×12 carbon weave: dark/light 6 px squares. */
    private fun weave(): Bitmap {
        val bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        for (y in 0 until 12) for (x in 0 until 12) {
            val light = (x < 6) == (y < 6)
            bmp.setPixel(x, y, if (light) 0xFF232323.toInt() else 0xFF090909.toInt())
        }
        return bmp
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (73L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        c.drawRect(0f, 0f, 1024f, 425f, pCarbon)
        c.drawRect(0f, 0f, 1024f, 425f, pVignette)
        k.pFill.color = C_RED_WEDGE
        c.drawPath(wedgeL, k.pFill)
        c.drawPath(wedgeR, k.pFill)

        c.save()
        c.translate(512f, 212f)
        c.drawCircle(0f, 0f, 208f, pChrome)
        k.pFill.color = 0xFF0A0A0A.toInt()
        c.drawCircle(0f, 0f, 196f, k.pFill)
        c.drawCircle(0f, 0f, 191f, pCarbon)
        c.drawCircle(0f, 0f, 191f, pDialShade)

        k.pStroke.color = C_TICK
        k.pStroke.strokeWidth = 5f; k.pStroke.strokeCap = Paint.Cap.BUTT
        c.drawLines(majorW, k.pStroke)
        k.pStroke.strokeWidth = 2.5f; c.drawLines(minorW, k.pStroke)
        k.pStroke.color = C_RED_TICK
        k.pStroke.strokeWidth = 5f; c.drawLines(majorR, k.pStroke)
        k.pStroke.strokeWidth = 2.5f; c.drawLines(minorR, k.pStroke)
        k.pStroke.strokeCap = Paint.Cap.ROUND
        pNum.textSize = 30f
        for (i in 0 until 10) {
            pNum.color = if (i >= 8) C_RED_TICK else C_TICK
            c.drawText(labelText[i], labelX[i], labelY[i], pNum)
        }

        val v = sl.speedInt(t)
        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pNum.textSize = 84f; pNum.color = C_WHITE
        c.drawText(k.buf, 0, k.buf.length, 0f, 126f, pNum)
        pNum.textSize = 22f; pNum.color = C_UNIT; pNum.letterSpacing = 0.14f
        c.drawText(DashSlots.UNIT, 0f, 160f, pNum)
        pNum.letterSpacing = 0f

        c.save()
        c.rotate(-135f + v.coerceIn(0, 180) * 1.5f)
        k.pFill.color = C_NEEDLE_BODY
        c.drawPath(needleBody, k.pFill)
        k.pStroke.color = 0xFF7A0000.toInt(); k.pStroke.strokeWidth = 1f
        c.drawPath(needleBody, k.pStroke)
        k.pFill.color = C_NEEDLE
        c.drawPath(needleTip, k.pFill)
        c.restore()
        c.drawCircle(0f, 0f, 22f, pChrome)
        k.pFill.color = 0xFF101010.toInt()
        c.drawCircle(0f, 0f, 9f, k.pFill)
        c.restore()

        val n = sl.four(t)
        if (n >= 1) sl.readout(c, pNum, k.extras[sl.vis[0]], t, 40f, 46f, 26f, 0, C_WHITE, C_UNIT, 300f)
        if (n >= 2) sl.readout(c, pNum, k.extras[sl.vis[1]], t, 984f, 46f, 26f, 2, C_WHITE, C_UNIT, 300f)
        // Slots 3 and 4 (owner, 2 Nov 2026: room for more than two readouts).
        if (n >= 3) sl.readout(c, pNum, k.extras[sl.vis[2]], t, 150f, 404f, 26f, 0, C_WHITE, C_UNIT, 300f)
        if (n >= 4) sl.readout(c, pNum, k.extras[sl.vis[3]], t, 874f, 404f, 26f, 2, C_WHITE, C_UNIT, 300f)
        sl.end(c)
    }

    private companion object {
        const val C_BG = 0xFF111111.toInt()
        const val C_RED_WEDGE = 0xFFD50000.toInt()
        const val C_TICK = 0xFFF2F4F5.toInt()
        const val C_RED_TICK = 0xFFFF2A2A.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_UNIT = 0xFFC9CFD4.toInt()
        const val C_NEEDLE = 0xFFFF1E1E.toInt()
        const val C_NEEDLE_BODY = 0xFFE10000.toInt()
    }
}
