// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Design #18 "Aviation airspeed indicator" (`mockups/dash-designs.html` #18): a black round dial with
 * white, green and yellow arcs, a red line near the top of the scale, a white needle and a boxed
 * digital readout. The scale is 0–180 km/h over 300° (-150° to +150°), 10 km/h per tick. Up to two
 * extras are small readouts bottom left.
 */
class AirspeedRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val majors = FloatArray(10 * 4)
    private val minors = FloatArray(9 * 4)
    private val labelX = FloatArray(10)
    private val labelY = FloatArray(10)
    private val labelText = Array(10) { (it * 20).toString() }
    private val ovalWhite = RectF(-188f, -188f, 188f, 188f)
    private val ovalBand = RectF(-178f, -178f, 178f, 178f)
    private val box = RectF(467f - 512f, 322f - 212f, 557f - 512f, 378f - 212f)
    private val needle = Path().apply {
        moveTo(-6f, 36f); lineTo(-3f, -120f); lineTo(0f, -168f); lineTo(3f, -120f); lineTo(6f, 36f); close()
    }
    private val tail = Path().apply { moveTo(-6f, 36f); lineTo(6f, 36f); lineTo(3f, 62f); lineTo(-3f, 62f); close() }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    init {
        var a = 0; var b = 0
        for (v in 0..180 step 10) {
            val ang = -150.0 + v * DEG
            if (v % 20 == 0) {
                sl.ticks(majors, a, 1, ang, 0.0, 0f, 0f, 158f, 138f)
                val r = Math.toRadians(ang)
                labelX[v / 20] = (Math.sin(r) * 108).toFloat()
                labelY[v / 20] = (-Math.cos(r) * 108).toFloat() + 10f
                a++
            } else { sl.ticks(minors, b, 1, ang, 0.0, 0f, 0f, 158f, 146f); b++ }
        }
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (103L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    /** Arc between two speeds on a band. */
    private fun arc(c: Canvas, oval: RectF, from: Float, to: Float, color: Int, w: Float) {
        k.pStroke.color = color; k.pStroke.strokeWidth = w; k.pStroke.strokeCap = Paint.Cap.BUTT
        c.drawArc(oval, -150f - 90f + from * DEG, (to - from) * DEG, false, k.pStroke)
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        for (i in 0 until 4) {
            val x = if (i % 2 == 0) 26f else 998f
            val y = if (i < 2) 26f else 399f
            k.pFill.color = 0xFF2A2E33.toInt()
            c.drawCircle(x, y, 9f, k.pFill)
            k.pStroke.color = 0xFF555B62.toInt(); k.pStroke.strokeWidth = 1f
            c.drawCircle(x, y, 9f, k.pStroke)
            k.pStroke.color = C_BG; k.pStroke.strokeWidth = 2f
            c.drawLine(x - 6f, y - 1f, x + 6f, y + 2f, k.pStroke)
        }
        c.save()
        c.translate(512f, 212f)
        k.pFill.color = 0xFF3A3F45.toInt()
        c.drawCircle(0f, 0f, 208f, k.pFill)
        k.pFill.color = 0xFF0F1113.toInt()
        c.drawCircle(0f, 0f, 198f, k.pFill)
        k.pStroke.color = C_LIGHT; k.pStroke.strokeWidth = 3f
        c.drawCircle(0f, 0f, 198f, k.pStroke)

        arc(c, ovalWhite, 42f, 100f, C_WHITE, 7f)
        arc(c, ovalBand, 50f, 140f, 0xFF2EC95A.toInt(), 13f)
        arc(c, ovalBand, 140f, 166f, 0xFFFFC400.toInt(), 13f)
        k.pStroke.color = 0xFFFF2A2A.toInt(); k.pStroke.strokeWidth = 7f
        val ra = Math.toRadians(-150.0 + 170 * DEG)
        c.drawLine((Math.sin(ra) * 168).toFloat(), (-Math.cos(ra) * 168).toFloat(), (Math.sin(ra) * 194).toFloat(), (-Math.cos(ra) * 194).toFloat(), k.pStroke)

        k.pStroke.color = 0xFFF2F4F5.toInt(); k.pStroke.strokeCap = Paint.Cap.BUTT
        k.pStroke.strokeWidth = 4f; c.drawLines(majors, k.pStroke)
        k.pStroke.strokeWidth = 2f; c.drawLines(minors, k.pStroke)
        pNum.textSize = 30f; pNum.color = 0xFFF2F4F5.toInt()
        for (i in 0 until 10) c.drawText(labelText[i], labelX[i], labelY[i], pNum)
        pNum.textSize = 20f; pNum.color = C_DIM; pNum.letterSpacing = 0.2f
        c.drawText("SPEED", 0f, -62f, pNum)
        pNum.letterSpacing = 0f

        val v = sl.speedInt(t)
        k.pFill.color = 0xFF000000.toInt()
        c.drawRoundRect(box, 6f, 6f, k.pFill)
        k.pStroke.color = C_LIGHT; k.pStroke.strokeWidth = 3f
        c.drawRoundRect(box, 6f, 6f, k.pStroke)
        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pNum.textSize = 50f; pNum.color = C_WHITE
        c.drawText(k.buf, 0, k.buf.length, 0f, 154f, pNum)

        c.save()
        c.rotate(-150f + v.coerceIn(0, 180) * DEG)
        k.pFill.color = 0xFFF7F7F2.toInt()
        c.drawPath(needle, k.pFill)
        c.drawPath(tail, k.pFill)
        k.pFill.color = 0xFFFF8A00.toInt()
        c.drawRect(-1.5f, -168f, 1.5f, -120f, k.pFill)
        c.restore()
        k.pFill.color = 0xFF2A2E33.toInt()
        c.drawCircle(0f, 0f, 16f, k.pFill)
        k.pStroke.color = C_LIGHT; k.pStroke.strokeWidth = 3f
        c.drawCircle(0f, 0f, 16f, k.pStroke)
        c.restore()

        pNum.textSize = 24f; pNum.color = C_DIM; pNum.textAlign = Paint.Align.RIGHT
        c.drawText(DashSlots.UNIT, 990f, 404f, pNum)
        val n = sl.four(t)
        if (n >= 1) sl.readout(c, pNum, k.extras[sl.vis[0]], t, 56f, 404f, 24f, 0, C_WHITE, C_DIM, 260f)
        if (n >= 2) sl.readout(c, pNum, k.extras[sl.vis[1]], t, 56f, 370f, 24f, 0, C_WHITE, C_DIM, 260f)
        // Slots 3 and 4 (owner, 2 Nov 2026: room for more than two readouts).
        if (n >= 3) sl.readout(c, pNum, k.extras[sl.vis[2]], t, 944f, 370f, 24f, 2, C_WHITE, C_DIM, 260f)
        if (n >= 4) sl.readout(c, pNum, k.extras[sl.vis[3]], t, 944f, 336f, 24f, 2, C_WHITE, C_DIM, 260f)
        sl.end(c)
    }

    private companion object {
        const val DEG = 300f / 180f
        const val C_BG = 0xFF0B0D0F.toInt()
        const val C_LIGHT = 0xFFCFD3D7.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_DIM = 0xFFAEB5BC.toInt()
    }
}
