// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat

/**
 * Shared drawing pieces for dash renderers (#2 onward): paints, the reused text buffer, standard
 * icons, the dark card and the corner "status pill" that shows a design's chosen extras.
 * One instance per renderer; nothing here allocates while drawing. Sizes are in px already scaled
 * by the caller (design px × s).
 */
class DashKit(context: Context, val extras: Array<DashExtra>) {
    val is24h: Boolean = DateFormat.is24HourFormat(context)
    val text = DashExtraText(context)
    val buf = StringBuilder(48)
    val rect = RectF()

    val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    /** Big numbers (Barlow Condensed 700 in the mockups). */
    val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    /** Thai labels and units (IBM Plex Sans Thai 500/600 in the mockups). */
    val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    val sM: String = context.getString(R.string.dash368_unit_m)
    val sKm: String = context.getString(R.string.dash368_unit_km)
    val sKmh: String = context.getString(R.string.dash368_unit_kmh)
    val sMin: String = context.getString(R.string.dash368_unit_min)
    val sArrive: String = context.getString(R.string.dash368_arrive)

    // 24-unit icons.
    private val turnRight = Path().apply {
        moveTo(7f, 21f); lineTo(7f, 11f); arcTo(RectF(7f, 8f, 13f, 14f), 180f, 90f)
        lineTo(19f, 8f)
        moveTo(15f, 4f); lineTo(19f, 8f); lineTo(15f, 12f)
    }
    private val turnLeft = Path().apply {
        moveTo(17f, 21f); lineTo(17f, 11f); arcTo(RectF(11f, 8f, 17f, 14f), 0f, -90f)
        lineTo(5f, 8f)
        moveTo(9f, 4f); lineTo(5f, 8f); lineTo(9f, 12f)
    }
    private val straight = Path().apply {
        moveTo(12f, 21f); lineTo(12f, 5f)
        moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
    }
    private val itemW = FloatArray(MAX_PILL_ITEMS)
    private val itemOn = BooleanArray(MAX_PILL_ITEMS)

    /** Dark card with a 1 px edge, as every floating card in the mockups. */
    fun card(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b)
        pFill.color = C_CARD
        c.drawRoundRect(rect, radius, radius, pFill)
        pStroke.color = C_EDGE; pStroke.strokeWidth = 1f
        c.drawRoundRect(rect, radius, radius, pStroke)
    }

    /** Turn arrow (cardDir 1 left, 2 right, else straight), [size] px, stroke in 24-unit [stroke24]. */
    fun turnIcon(c: Canvas, x: Float, y: Float, size: Float, dir: Int, color: Int, stroke24: Float) {
        pStroke.color = color
        pStroke.strokeWidth = stroke24
        c.save()
        c.translate(x, y)
        c.scale(size / 24f, size / 24f)
        c.drawPath(if (dir == 1) turnLeft else if (dir == 2) turnRight else straight, pStroke)
        c.restore()
    }

    /** GPS crosshair: green with a fresh fix, grey without. */
    fun gpsIcon(c: Canvas, x: Float, y: Float, size: Float, fix: Boolean) {
        val col = if (fix) C_GREEN else C_GREY
        pStroke.color = col; pStroke.strokeWidth = 2f
        pFill.color = col
        c.save()
        c.translate(x, y)
        c.scale(size / 24f, size / 24f)
        c.drawCircle(12f, 12f, 7f, pStroke)
        c.drawCircle(12f, 12f, 2.5f, pFill)
        c.drawLine(12f, 1f, 12f, 5f, pStroke); c.drawLine(12f, 19f, 12f, 23f, pStroke)
        c.drawLine(1f, 12f, 5f, 12f, pStroke); c.drawLine(19f, 12f, 23f, 12f, pStroke)
        c.restore()
    }

    /** Phone battery outline [h] px tall (16:26), filled to [pct]. */
    fun batteryIcon(c: Canvas, x: Float, y: Float, h: Float, pct: Int) {
        c.save()
        c.translate(x, y)
        c.scale(h / 26f, h / 26f)
        pStroke.color = C_SEC; pStroke.strokeWidth = 2f
        c.drawRoundRect(2f, 4f, 14f, 24f, 2f, 2f, pStroke)
        c.drawLine(6f, 2f, 10f, 2f, pStroke)
        val fillH = 10f * pct / 100f
        if (fillH > 0.5f) {
            pFill.color = C_BATT
            c.drawRect(5f, 21f - fillH, 11f, 21f, pFill)
        }
        c.restore()
    }

    /**
     * Number + unit with one baseline, left-aligned at [x]; returns the right edge. The number is
     * whatever is in [buf].
     */
    fun numUnit(c: Canvas, x: Float, baseline: Float, numSize: Float, numColor: Int, unit: String?, unitSize: Float, gap: Float): Float {
        pNum.textSize = numSize; pNum.color = numColor; pNum.textAlign = Paint.Align.LEFT
        c.drawText(buf, 0, buf.length, x, baseline, pNum)
        var r = x + pNum.measureText(buf, 0, buf.length)
        if (unit != null) {
            pLabel.textSize = unitSize; pLabel.color = C_SEC; pLabel.textAlign = Paint.Align.LEFT
            c.drawText(unit, r + gap, baseline, pLabel)
            r += gap + pLabel.measureText(unit)
        }
        return r
    }

    fun numUnitWidth(numSize: Float, unit: String?, unitSize: Float, gap: Float): Float {
        pNum.textSize = numSize
        var w = pNum.measureText(buf, 0, buf.length)
        if (unit != null) { pLabel.textSize = unitSize; w += gap + pLabel.measureText(unit) }
        return w
    }

    // ---------------------------------------------------------------- corner pill

    /**
     * Status pill anchored at its top-right corner ([right], [top]), [h] px tall: GPS icon, then the
     * chosen [extras] left to right. CLOCK is drawn large ([clockSize]); PHONE_BATTERY as icon + %;
     * other extras as a small label over value + unit. Unknown optional extras are skipped (no gap).
     */
    fun cornerPill(c: Canvas, t: DashValues, right: Float, top: Float, h: Float, s: Float, clockSize: Float) {
        val padX = 18f * s
        val gap = 14f * s
        val gps = 0.5f * h
        var total = padX * 2 + gps
        val n = minOf(extras.size, MAX_PILL_ITEMS)
        for (i in 0 until n) {
            val e = extras[i]
            itemOn[i] = text.visible(e, t)
            if (!itemOn[i]) continue
            itemW[i] = itemWidth(e, t, h, clockSize)
            total += gap + itemW[i]
        }
        val left = right - total
        card(c, left, top, right, top + h, 0.24f * h)
        var x = left + padX
        gpsIcon(c, x, top + (h - gps) / 2f, gps, t.gpsFix)
        x += gps
        for (i in 0 until n) {
            if (!itemOn[i]) continue
            x += gap
            drawItem(c, extras[i], t, x, top, h, clockSize)
            x += itemW[i]
        }
    }

    /** Signature part for [cornerPill]. */
    fun pillSignature(t: DashValues): Long =
        text.signature(extras, t, is24h) * 31 + (if (t.gpsFix) 1 else 0)

    private fun itemWidth(e: DashExtra, t: DashValues, h: Float, clockSize: Float): Float {
        buf.setLength(0)
        text.value(buf, e, t, is24h)
        return when (e) {
            DashExtra.CLOCK -> { pNum.textSize = clockSize; pNum.measureText(buf, 0, buf.length) }
            DashExtra.PHONE_BATTERY -> {
                pNum.textSize = 0.4f * h
                0.43f * h * 16f / 26f + 0.15f * h + pNum.measureText(buf, 0, buf.length)
            }
            else -> {
                val vw = numUnitWidth(0.42f * h, text.unit(e), 0.24f * h, 0.06f * h)
                pLabel.textSize = 0.2f * h
                maxOf(vw, pLabel.measureText(text.label(e)))
            }
        }
    }

    private fun drawItem(c: Canvas, e: DashExtra, t: DashValues, x: Float, top: Float, h: Float, clockSize: Float) {
        buf.setLength(0)
        text.value(buf, e, t, is24h)
        when (e) {
            DashExtra.CLOCK -> {
                pNum.textSize = clockSize; pNum.color = C_TEXT; pNum.textAlign = Paint.Align.LEFT
                c.drawText(buf, 0, buf.length, x, top + h / 2f + 0.36f * clockSize, pNum)
            }
            DashExtra.PHONE_BATTERY -> {
                val ih = 0.43f * h
                if (t.phoneBatteryPct >= 0) batteryIcon(c, x, top + (h - ih) / 2f, ih, t.phoneBatteryPct)
                pNum.textSize = 0.4f * h; pNum.color = C_SEC; pNum.textAlign = Paint.Align.LEFT
                c.drawText(buf, 0, buf.length, x + ih * 16f / 26f + 0.15f * h, top + h / 2f + 0.14f * h, pNum)
            }
            else -> {
                pLabel.textSize = 0.2f * h; pLabel.color = C_SEC; pLabel.textAlign = Paint.Align.LEFT
                c.drawText(text.label(e), x, top + 0.3f * h, pLabel)
                numUnit(c, x, top + 0.82f * h, 0.42f * h, C_TEXT, text.unit(e), 0.24f * h, 0.06f * h)
            }
        }
    }

    /** Draw [str] left-aligned, cut to fit [maxW] px (no ellipsis, no allocation). */
    fun drawFitted(c: Canvas, str: String, x: Float, baseline: Float, maxW: Float, p: Paint) {
        val n = p.breakText(str, true, maxW, null)
        c.drawText(str, 0, n, x, baseline, p)
    }

    companion object {
        const val MAX_PILL_ITEMS = 6
        const val C_CARD = 0xFF0E1114.toInt()
        const val C_EDGE = 0xFF2A3138.toInt()
        const val C_TEXT = 0xFFF2F4F5.toInt()
        const val C_SEC = 0xFFB9C1C8.toInt()
        const val C_DIM = 0xFF6B747C.toInt()
        const val C_GREEN = 0xFF2EE05A.toInt()
        const val C_GREY = 0xFF6B747C.toInt()
        const val C_BATT = 0xFF7FD48A.toInt()
        const val C_AMBER = 0xFFF5A623.toInt()
        const val C_MAP_BG = 0xFF1A1F24.toInt()
    }
}
