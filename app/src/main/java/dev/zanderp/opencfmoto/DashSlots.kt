// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Drawing helpers shared by the non-map designs #4–#10: extras as tiles / rows with a themed
 * palette, the status icons (in any colour), and the speed segment maths. Built on a [DashKit]; one
 * instance per renderer, nothing here allocates while drawing.
 */
class DashSlots(val k: DashKit) {
    /** Indices into [DashKit.extras] of the extras that are visible now; filled by [collect]. */
    val vis = IntArray(16)

    private val headband = Path().apply {
        moveTo(6f, 20f); lineTo(6f, 16f); arcTo(RectF(6f, 6f, 26f, 26f), 180f, 180f); lineTo(26f, 20f)
    }
    private val shield = Path().apply {
        moveTo(12f, 2f); lineTo(20f, 5f); lineTo(20f, 11f)
        cubicTo(20f, 16f, 16.5f, 20f, 12f, 22f)
        cubicTo(7.5f, 20f, 4f, 16f, 4f, 11f)
        lineTo(4f, 5f); close()
    }
    private val shieldTick = Path().apply {
        moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f)
    }

    /** Fill [vis] with the visible extras (optional + unknown ones skipped); returns the count. */
    fun collect(t: DashValues): Int {
        var n = 0
        val ex = k.extras
        for (i in ex.indices) {
            if (n == vis.size) break
            if (k.text.visible(ex[i], t)) vis[n++] = i
        }
        return n
    }

    /** Reset the shared buffer and append the value of [e]; the odometer is zero-padded when it comes from the book. */
    fun value(e: DashExtra, t: DashValues) {
        val b = k.buf
        b.setLength(0)
        if (e == DashExtra.ODOMETER && t.odometerFromBook && !t.odometerKm.isNaN() && t.odometerKm >= 0.5) {
            val km = t.odometerKm.toLong().coerceIn(0L, 999_999L).toInt()
            var div = 100_000
            while (div > 0) { b.append(('0' + (km / div) % 10)); div /= 10 }
        } else {
            k.text.value(b, e, t, k.is24h)
        }
    }

    /** Signature of the chosen extras as [value] shows them. */
    fun signature(t: DashValues): Long =
        k.text.signature(k.extras, t, k.is24h) * 31 + (if (t.odometerFromBook) 1 else 0)

    // ---------------------------------------------------------------- numbers

    fun numWidth(p: Paint, size: Float, unit: String?, unitSize: Float, gap: Float): Float {
        p.textSize = size
        var w = p.measureText(k.buf, 0, k.buf.length)
        if (unit != null) { k.pLabel.textSize = unitSize; w += gap + k.pLabel.measureText(unit) }
        return w
    }

    /** [DashKit.buf] + optional unit on one baseline, left-aligned at [x] with the colours given; returns the right edge. */
    fun numUnit(
        c: Canvas, p: Paint, x: Float, base: Float, size: Float, color: Int,
        unit: String?, unitSize: Float, unitColor: Int, gap: Float,
    ): Float {
        p.textSize = size; p.color = color; p.textAlign = Paint.Align.LEFT
        c.drawText(k.buf, 0, k.buf.length, x, base, p)
        var r = x + p.measureText(k.buf, 0, k.buf.length)
        if (unit != null) {
            k.pLabel.textSize = unitSize; k.pLabel.color = unitColor; k.pLabel.textAlign = Paint.Align.LEFT
            c.drawText(unit, r + gap, base, k.pLabel)
            r += gap + k.pLabel.measureText(unit)
        }
        return r
    }

    /** [size] reduced (down to 50 %) so the number + unit run fits [maxW]; the number is [DashKit.buf]. */
    fun fitSize(p: Paint, size: Float, unit: String?, unitSize: Float, gap: Float, maxW: Float): Float {
        p.textSize = size
        val numW = p.measureText(k.buf, 0, k.buf.length)
        var unitW = 0f
        if (unit != null) { k.pLabel.textSize = unitSize; unitW = gap + k.pLabel.measureText(unit) }
        if (numW + unitW > maxW && numW > 0f) return size * ((maxW - unitW) / numW).coerceIn(0.5f, 1f)
        return size
    }

    /** Same as [numUnit] but shrunk to fit [maxW]. */
    fun numUnitFit(
        c: Canvas, p: Paint, x: Float, base: Float, size: Float, color: Int,
        unit: String?, unitSize: Float, unitColor: Int, gap: Float, maxW: Float,
    ) {
        numUnit(c, p, x, base, fitSize(p, size, unit, unitSize, gap, maxW), color, unit, unitSize, unitColor, gap)
    }

    /** Same, right-aligned at [right]; the number shrinks (down to 50 %) so the whole run fits [maxW]. */
    fun numUnitRight(
        c: Canvas, p: Paint, right: Float, base: Float, size: Float, color: Int,
        unit: String?, unitSize: Float, unitColor: Int, gap: Float, maxW: Float,
    ) {
        val sz = fitSize(p, size, unit, unitSize, gap, maxW)
        val w = numWidth(p, sz, unit, unitSize, gap)
        numUnit(c, p, right - w, base, sz, color, unit, unitSize, unitColor, gap)
    }

    /** Number of lit segments of [total] for [kmh] on the 0–180 km/h scale; 0 when unknown. */
    fun litSegments(kmh: Float, total: Int): Int {
        if (kmh.isNaN()) return 0
        return (kmh * total / SPEED_MAX).toInt().coerceIn(0, total)
    }

    // ---------------------------------------------------------------- icons

    fun gps(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        k.pStroke.color = color; k.pStroke.strokeWidth = 2f
        k.pFill.color = color
        c.save(); c.translate(x, y); c.scale(size / 24f, size / 24f)
        c.drawCircle(12f, 12f, 7f, k.pStroke)
        c.drawCircle(12f, 12f, 2.5f, k.pFill)
        c.drawLine(12f, 1f, 12f, 5f, k.pStroke); c.drawLine(12f, 19f, 12f, 23f, k.pStroke)
        c.drawLine(1f, 12f, 5f, 12f, k.pStroke); c.drawLine(19f, 12f, 23f, 12f, k.pStroke)
        c.restore()
    }

    fun headset(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        k.pStroke.color = color; k.pStroke.strokeWidth = 2f
        c.save(); c.translate(x, y); c.scale(size / 32f, size / 32f)
        c.drawPath(headband, k.pStroke)
        c.drawRoundRect(4f, 19f, 9f, 28f, 2f, 2f, k.pStroke)
        c.drawRoundRect(23f, 19f, 28f, 28f, 2f, 2f, k.pStroke)
        c.restore()
    }

    fun recording(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        k.pStroke.color = color; k.pStroke.strokeWidth = 2f
        k.pFill.color = color
        c.save(); c.translate(x, y); c.scale(size / 24f, size / 24f)
        c.drawCircle(12f, 12f, 10f, k.pStroke)
        c.drawCircle(12f, 12f, 5f, k.pFill)
        c.restore()
    }

    fun crashShield(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        k.pStroke.color = color; k.pStroke.strokeWidth = 2f
        c.save(); c.translate(x, y); c.scale(size / 24f, size / 24f)
        c.drawPath(shield, k.pStroke)
        c.drawPath(shieldTick, k.pStroke)
        c.restore()
    }

    /** Phone battery [h] px tall (16:26) in two colours (outline, level). */
    fun battery(c: Canvas, x: Float, y: Float, h: Float, pct: Int, outline: Int, level: Int) {
        c.save(); c.translate(x, y); c.scale(h / 26f, h / 26f)
        k.pStroke.color = outline; k.pStroke.strokeWidth = 2f
        c.drawRoundRect(2f, 4f, 14f, 24f, 2f, 2f, k.pStroke)
        c.drawLine(6f, 2f, 10f, 2f, k.pStroke)
        val fillH = 10f * pct.coerceIn(0, 100) / 100f
        if (fillH > 0.5f) {
            k.pFill.color = level
            c.drawRect(5f, 21f - fillH, 11f, 21f, k.pFill)
        }
        c.restore()
    }

    // ---------------------------------------------------------------- speedometer-only helpers (#11–#20)

    /** Whole-screen speed int (-1 unknown) as every #11–#20 design shows and signs it. */
    fun speedInt(t: DashValues): Int = if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt().coerceIn(0, 999)

    /** Fill [vis] with the visible extras and return how many of them fit the four readout slots. */
    fun four(t: DashValues): Int = minOf(collect(t), 4)

    /**
     * Small corner readout of extra [e]: an optional label (cut to fit), then value + unit. The clock
     * and battery show no label. [align] 0 = left of [x], 1 = centred on it, 2 = right of it.
     */
    fun readout(
        c: Canvas, p: Paint, e: DashExtra, t: DashValues, x: Float, base: Float, size: Float, align: Int,
        color: Int, labelColor: Int, maxW: Float,
    ) {
        value(e, t)
        val unit = k.text.unit(e)
        val gap = size * 0.3f
        val numW = numWidth(p, size, unit, size * 0.7f, size * 0.2f)
        val withLabel = e != DashExtra.CLOCK && e != DashExtra.PHONE_BATTERY
        var labelW = 0f
        var labelN = 0
        val label = k.text.label(e)
        if (withLabel) {
            k.pLabel.textSize = size * 0.7f
            labelN = k.pLabel.breakText(label, true, maxOf(0f, maxW - numW - gap), null)
            labelW = if (labelN > 0) k.pLabel.measureText(label, 0, labelN) + gap else 0f
        }
        val total = labelW + numW
        var l = if (align == 0) x else if (align == 1) x - total / 2f else x - total
        if (labelN > 0) {
            k.pLabel.color = labelColor; k.pLabel.textAlign = Paint.Align.LEFT; k.pLabel.textSize = size * 0.7f
            c.drawText(label, 0, labelN, l, base, k.pLabel)
            l += labelW
        }
        numUnit(c, p, l, base, size, color, unit, size * 0.7f, labelColor, size * 0.2f)
    }

    /** Linear blend of two ARGB colours (no allocation). */
    fun lerp(a: Int, b: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * g).toInt()
        val gr = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * g).toInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * g).toInt()
        return (0xFF shl 24) or (r shl 16) or (gr shl 8) or bl
    }

    /**
     * Put the canvas in design space (1024×425 units, uniform scale [s], centred): the speedometer-only
     * designs are single compositions, so every shape is drawn in design px between this and [end].
     */
    fun begin(c: Canvas, sx: Float, sy: Float, s: Float) {
        c.save()
        c.translate(512f * sx, 212.5f * sy)
        c.scale(s, s)
        c.translate(-512f, -212.5f)
    }

    fun end(c: Canvas) = c.restore()

    /**
     * Fill [out] with line endpoints (x1,y1,x2,y2) of [n] ticks on a dial around ([cx],[cy]) at [a0] + i·[step]
     * degrees clockwise from 12 o'clock, from radius [r1] to [r2]. Built once, in a constructor.
     */
    fun ticks(out: FloatArray, from: Int, n: Int, a0: Double, step: Double, cx: Float, cy: Float, r1: Float, r2: Float) {
        for (i in 0 until n) {
            val a = Math.toRadians(a0 + i * step)
            val sn = Math.sin(a).toFloat()
            val cs = -Math.cos(a).toFloat()
            val j = (from + i) * 4
            out[j] = cx + sn * r1; out[j + 1] = cy + cs * r1; out[j + 2] = cx + sn * r2; out[j + 3] = cy + cs * r2
        }
    }

    companion object {
        const val SPEED_MAX = 180f
        const val UNIT = "KM/H"
    }
}
