// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Design #14 "Fighter-jet HUD" (`mockups/dash-designs.html` #14): a vertical speed tape that slides
 * with the speed (9 px per km/h, ticks every 5, a label every 10), a green readout box with the speed,
 * and a fixed horizon / pitch-ladder reticle on the right. Glow is a wider low-alpha stroke under the
 * sharp one. Up to two extras: top left and bottom right.
 */
class HudRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val pMono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val box = Path().apply {
        moveTo(252f, 212f); lineTo(284f, 134f); lineTo(600f, 134f); lineTo(600f, 290f); lineTo(284f, 290f); close()
    }
    private val reticle = Path().apply {
        moveTo(640f, 212f); lineTo(770f, 212f); moveTo(870f, 212f); lineTo(1010f, 212f)
        addCircle(820f, 212f, 26f, Path.Direction.CW)
        moveTo(794f, 212f); lineTo(770f, 212f); moveTo(846f, 212f); lineTo(870f, 212f); moveTo(820f, 186f); lineTo(820f, 164f)
        moveTo(700f, 132f); lineTo(770f, 132f); moveTo(870f, 132f); lineTo(940f, 132f)
        moveTo(700f, 132f); lineTo(700f, 142f); moveTo(940f, 132f); lineTo(940f, 142f)
        moveTo(700f, 292f); lineTo(770f, 292f); moveTo(870f, 292f); lineTo(940f, 292f)
        moveTo(700f, 292f); lineTo(700f, 282f); moveTo(940f, 292f); lineTo(940f, 282f)
        moveTo(640f, 20f); lineTo(660f, 20f); moveTo(640f, 405f); lineTo(660f, 405f)
        moveTo(1004f, 20f); lineTo(984f, 20f); moveTo(1004f, 405f); lineTo(984f, 405f)
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    override fun signature(t: DashValues): Long = (83L * 31 + sl.speedInt(t)) * 31 + sl.signature(t)

    /** Glow under, sharp line over. */
    private fun stroke(c: Canvas, p: Path, w: Float) {
        pLine.color = C_GLOW; pLine.strokeWidth = w + 6f
        c.drawPath(p, pLine)
        pLine.color = C_GREEN; pLine.strokeWidth = w
        c.drawPath(p, pLine)
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        sl.begin(c, sx, sy, s)
        val v = sl.speedInt(t)

        // Tape: the line, then ticks at every 5 km/h around the current speed (rest at 0 when unknown).
        val cur = if (v < 0) 0 else v
        pLine.color = C_GREEN; pLine.strokeWidth = 3f
        c.drawLine(246f, 12f, 246f, 413f, pLine)
        var tv = ((cur - 23) / 5) * 5
        if (tv < 0) tv = 0
        while (tv <= cur + 23) {
            val y = 212f + (cur - tv) * 9f
            if (y in 12f..413f) {
                val major = tv % 10 == 0
                pLine.strokeWidth = if (major) 3f else 2f
                c.drawLine(246f, y, if (major) 212f else 228f, y, pLine)
                if (major && Math.abs(y - 212f) > 40f) {
                    k.buf.setLength(0); k.buf.append(tv)
                    pMono.textSize = 30f; pMono.color = C_GREEN; pMono.textAlign = Paint.Align.RIGHT
                    c.drawText(k.buf, 0, k.buf.length, 198f, y + 10f, pMono)
                }
            }
            tv += 5
        }

        k.pFill.color = C_BOX
        c.drawPath(box, k.pFill)
        stroke(c, box, 4f)
        k.buf.setLength(0)
        if (v < 0) k.buf.append("--") else k.buf.append(v)
        pMono.textSize = 150f; pMono.color = C_GREEN; pMono.textAlign = Paint.Align.CENTER
        c.drawText(k.buf, 0, k.buf.length, 448f, 244f, pMono)
        pMono.textSize = 24f; pMono.letterSpacing = 0.25f
        c.drawText("GS  KM/H", 448f, 124f, pMono)
        pMono.letterSpacing = 0f

        stroke(c, reticle, 3f)
        pMono.textSize = 26f; pMono.textAlign = Paint.Align.LEFT
        c.drawText("10", 706f, 138f, pMono)
        c.drawText("10", 706f, 304f, pMono)

        val n = sl.four(t)
        if (n >= 1) sl.readout(c, pMono, k.extras[sl.vis[0]], t, 640f, 60f, 26f, 0, C_GREEN, C_GREEN, 360f)
        if (n >= 2) sl.readout(c, pMono, k.extras[sl.vis[1]], t, 1004f, 404f, 26f, 2, C_GREEN, C_GREEN, 300f)
        // Slots 3 and 4 (owner, 2 Nov 2026: room for more than two readouts).
        if (n >= 3) sl.readout(c, pMono, k.extras[sl.vis[2]], t, 660f, 404f, 26f, 0, C_GREEN, C_GREEN, 250f)
        if (n >= 4) sl.readout(c, pMono, k.extras[sl.vis[3]], t, 1004f, 60f, 26f, 2, C_GREEN, C_GREEN, 300f)
        sl.end(c)
    }

    private companion object {
        const val C_BG = 0xFF020503.toInt()
        const val C_GREEN = 0xFF46FF86.toInt()
        const val C_GLOW = 0x3846FF86
        const val C_BOX = 0xFF031A0C.toInt()
    }
}
