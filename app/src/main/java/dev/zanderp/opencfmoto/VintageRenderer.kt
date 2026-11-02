// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Design #9 "Vintage dial" (`mockups/dash-designs.html` #9): an analogue speedometer (0–180 km/h
 * over 270°, red zone from 140) in the middle with a digital readout under the hub, an analogue
 * clock with a digital time on the left, and panels for the chosen extras on the right. The ODO
 * extra is drawn as a small footer line instead of a panel, as in the mockup. The speed needle
 * rests at 0 when the speed is unknown (the readout then says "--").
 */
class VintageRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f
    private val sOdo = "ODO " + context.getString(R.string.dash368_app_odo)

    private val pHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pOdo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.09f
    }

    // Speed dial geometry, relative to the dial centre (design px).
    private val tickMajorN = FloatArray(7 * 4)
    private val tickMajorRed = FloatArray(3 * 4)
    private val tickMinorN = FloatArray(21 * 4)
    private val tickMinorRed = FloatArray(6 * 4)
    private val labelText = Array(10) { (it * 20).toString() }
    private val labelX = FloatArray(10)
    private val labelY = FloatArray(10)
    private val needle = Path().apply {
        moveTo(0f, -172f); lineTo(6f, 0f); lineTo(0f, 34f); lineTo(-6f, 0f); close()
    }
    private val clockMajor = FloatArray(4 * 4)
    private val clockMinor = FloatArray(8 * 4)

    init {
        val cur = IntArray(4)   // write cursor per tick array: majorN, majorRed, minorN, minorRed
        for (v in 0..180 step 5) {
            val a = Math.toRadians(-135.0 + v * 1.5)
            val sn = Math.sin(a).toFloat()
            val cs = -Math.cos(a).toFloat()
            val major = v % 20 == 0
            val red = v >= 140
            val inner = if (major) 158f else if (v % 10 == 0) 168f else 176f
            val which = (if (major) 0 else 2) + (if (red) 1 else 0)
            val arr = when (which) { 0 -> tickMajorN; 1 -> tickMajorRed; 2 -> tickMinorN; else -> tickMinorRed }
            var i = cur[which]
            arr[i++] = sn * 182f; arr[i++] = cs * 182f; arr[i++] = sn * inner; arr[i++] = cs * inner
            cur[which] = i
            if (major) {
                labelX[v / 20] = sn * 129f
                labelY[v / 20] = cs * 129f + 10f
            }
        }
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val sn = Math.sin(a).toFloat()
            val cs = -Math.cos(a).toFloat()
            if (i % 3 == 0) {
                val j = (i / 3) * 4
                clockMajor[j] = sn * 88f; clockMajor[j + 1] = cs * 88f; clockMajor[j + 2] = sn * 74f; clockMajor[j + 3] = cs * 74f
            } else {
                val j = (i - i / 3 - 1) * 4
                clockMinor[j] = sn * 88f; clockMinor[j + 1] = cs * 88f; clockMinor[j + 2] = sn * 82f; clockMinor[j + 3] = cs * 82f
            }
        }
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long {
        var h = 61L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + t.clockMinute
        h = h * 31 + sl.signature(t)
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        drawSpeedDial(c, t, sx, sy)
        drawClock(c, t, sx, sy)
        drawPanels(c, t, sx, sy)
    }

    private fun drawSpeedDial(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val kmh = if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt()
        c.save()
        c.translate(512f * sx, 212f * sy)
        c.scale(s, s)
        k.pFill.color = C_FACE_OUT
        c.drawCircle(0f, 0f, 202f, k.pFill)
        k.pStroke.color = C_BRASS; k.pStroke.strokeWidth = 4f
        c.drawCircle(0f, 0f, 202f, k.pStroke)
        k.pFill.color = C_FACE
        c.drawCircle(0f, 0f, 190f, k.pFill)
        k.pStroke.color = C_RIM; k.pStroke.strokeWidth = 2f
        c.drawCircle(0f, 0f, 190f, k.pStroke)

        k.pStroke.color = C_CREAM
        k.pStroke.strokeWidth = 4f; c.drawLines(tickMajorN, k.pStroke)
        k.pStroke.strokeWidth = 2f; c.drawLines(tickMinorN, k.pStroke)
        k.pStroke.color = C_RED
        k.pStroke.strokeWidth = 4f; c.drawLines(tickMajorRed, k.pStroke)
        k.pStroke.strokeWidth = 2f; c.drawLines(tickMinorRed, k.pStroke)
        k.pNum.textSize = 28f; k.pNum.textAlign = Paint.Align.CENTER
        for (i in 0 until 10) {
            k.pNum.color = if (i >= 7) C_RED else C_CREAM
            c.drawText(labelText[i], labelX[i], labelY[i], k.pNum)
        }

        c.save()
        c.rotate(-135f + kmh.coerceIn(0, 180) * 1.5f)
        k.pFill.color = C_NEEDLE
        c.drawPath(needle, k.pFill)
        k.pStroke.color = C_FACE; k.pStroke.strokeWidth = 2f
        c.drawPath(needle, k.pStroke)
        c.restore()
        k.pFill.color = C_BRASS
        c.drawCircle(0f, 0f, 20f, k.pFill)
        k.pStroke.color = C_FACE; k.pStroke.strokeWidth = 3f
        c.drawCircle(0f, 0f, 20f, k.pStroke)
        k.pFill.color = C_FACE
        c.drawCircle(0f, 0f, 7f, k.pFill)

        k.pLabel.textSize = 22f; k.pLabel.color = C_DIM; k.pLabel.textAlign = Paint.Align.CENTER
        c.drawText(k.sKmh, 0f, 98f, k.pLabel)
        k.buf.setLength(0)
        if (kmh < 0) k.buf.append("--") else k.buf.append(kmh)
        k.pNum.textSize = 50f; k.pNum.color = C_CREAM; k.pNum.textAlign = Paint.Align.CENTER
        c.drawText(k.buf, 0, k.buf.length, 0f, 150f, k.pNum)
        c.restore()
    }

    private fun drawClock(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.save()
        c.translate(130f * sx, 212f * sy)
        c.scale(s, s)
        k.pFill.color = C_FACE_OUT
        c.drawCircle(0f, 0f, 100f, k.pFill)
        k.pStroke.color = C_BRASS; k.pStroke.strokeWidth = 3f
        c.drawCircle(0f, 0f, 100f, k.pStroke)
        k.pFill.color = C_FACE
        c.drawCircle(0f, 0f, 92f, k.pFill)
        k.pStroke.color = C_RIM; k.pStroke.strokeWidth = 2f
        c.drawCircle(0f, 0f, 92f, k.pStroke)
        k.pStroke.color = C_CREAM
        k.pStroke.strokeWidth = 4f; c.drawLines(clockMajor, k.pStroke)
        k.pStroke.strokeWidth = 2f; c.drawLines(clockMinor, k.pStroke)
        val cm = t.clockMinute
        if (cm >= 0) {
            val hh = (cm / 60) % 12
            val mm = cm % 60
            pHand.color = C_CREAM
            c.save()
            c.rotate(hh * 30f + mm * 0.5f)
            pHand.strokeWidth = 7f
            c.drawLine(0f, 0f, 0f, -52f, pHand)
            c.restore()
            c.save()
            c.rotate(mm * 6f)
            pHand.strokeWidth = 4f
            c.drawLine(0f, 0f, 0f, -78f, pHand)
            c.restore()
        }
        k.pFill.color = C_NEEDLE
        c.drawCircle(0f, 0f, 6f, k.pFill)
        k.buf.setLength(0)
        DashFmt.clock(k.buf, cm, k.is24h)
        k.pNum.textSize = 40f; k.pNum.color = C_CREAM; k.pNum.textAlign = Paint.Align.CENTER
        c.drawText(k.buf, 0, k.buf.length, 0f, 138f, k.pNum)
        c.restore()
    }

    private fun drawPanels(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val n = sl.collect(t)
        var tiles = 0
        var odoIdx = -1
        for (j in 0 until n) {
            if (k.extras[sl.vis[j]] == DashExtra.ODOMETER) odoIdx = sl.vis[j] else tiles++
        }
        val kf = if (tiles > 3) 0.85f else 1f
        val l = (1024f - 30f - 240f) * sx
        val r = (1024f - 30f) * sx
        var i = 0
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e == DashExtra.ODOMETER) continue
            val top = (20f * kf + i * 108f * kf) * sy
            drawPanel(c, e, t, l, top, r, top + 100f * kf * sy, kf)
            i++
        }
        if (odoIdx >= 0) {
            sl.value(DashExtra.ODOMETER, t)
            k.pLabel.textSize = 16f * s; k.pLabel.color = C_DIM; k.pLabel.textAlign = Paint.Align.RIGHT
            val b = (425f - 18f) * sy
            c.drawText(sOdo, r, b - 40f * s, k.pLabel)
            pOdo.textSize = 34f * s; pOdo.color = C_CREAM; pOdo.textAlign = Paint.Align.RIGHT
            c.drawText(k.buf, 0, k.buf.length, r, b, pOdo)
        }
    }

    private fun drawPanel(c: Canvas, e: DashExtra, t: DashValues, l: Float, top: Float, r: Float, b: Float, kf: Float) {
        val rad = 12f * s
        k.pFill.color = C_FACE_OUT
        k.rect.set(l, top, r, b)
        c.drawRoundRect(k.rect, rad, rad, k.pFill)
        k.pStroke.color = C_EDGE; k.pStroke.strokeWidth = 2f * s
        k.rect.set(l + s, top + s, r - s, b - s)
        c.drawRoundRect(k.rect, rad, rad, k.pStroke)
        k.pLabel.textSize = 20f * s * kf; k.pLabel.color = C_DIM; k.pLabel.textAlign = Paint.Align.LEFT
        k.drawFitted(c, k.text.label(e), l + 18f * s, top + 30f * s * kf, r - l - 36f * s, k.pLabel)
        sl.value(e, t)
        sl.numUnitRight(
            c, k.pNum, r - 18f * s, b - 14f * s * kf, 54f * s * kf, C_CREAM,
            k.text.unit(e), 20f * s * kf, C_DIM, 6f * s, r - l - 36f * s,
        )
    }

    private companion object {
        const val C_BG = 0xFF0A0806.toInt()
        const val C_FACE_OUT = 0xFF1B1611.toInt()
        const val C_FACE = 0xFF0E0B08.toInt()
        const val C_RIM = 0xFF3A2E20.toInt()
        const val C_BRASS = 0xFF8C6B3A.toInt()
        const val C_CREAM = 0xFFEFE3C8.toInt()
        const val C_DIM = 0xFFCDBB98.toInt()
        const val C_RED = 0xFFFF6A5C.toInt()
        const val C_NEEDLE = 0xFFE8602E.toInt()
        const val C_EDGE = 0xFF5E4A2C.toInt()
    }
}
