// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * Design #8 "Touring" (`mockups/dash-designs.html` #8): a compass rose on the left that turns with
 * the heading (heading-up, fixed needle), the next turn + speed in the middle, and a column of rows
 * on the right (arrival, minutes left, km left, then the chosen extras).
 *
 * With a route the middle shows the turn and the right column the three route rows. Without one the
 * route-only parts are hidden (no turn, no ETA rows, no "--" placeholders) and the middle becomes
 * heading-focused: the compass point in large type over the speed. Unknown heading draws the ring
 * only, with no needle or letters.
 */
class TouringRenderer(context: Context, extras: Array<DashExtra>) : DashRenderer {
    private val k = DashKit(context, extras)
    private val sl = DashSlots(k)
    private var s = 1f

    private val sNext = context.getString(R.string.dash_next)
    private val sLeftWord = context.getString(R.string.dash_remaining)
    private val sLeft = context.getString(R.string.dash_dir_left)
    private val sRight = context.getString(R.string.dash_dir_right)
    private val sStraight = context.getString(R.string.dash_dir_straight)
    private val names = context.resources.getStringArray(R.array.dash_compass_names)

    private val majorTicks = FloatArray(12 * 4)
    private val minorTicks = FloatArray(24 * 4)
    private val needle = Path().apply {
        moveTo(0f, -100f); lineTo(14f, 8f); lineTo(0f, -6f); lineTo(-14f, 8f); close()
    }
    private val marker = Path().apply {
        moveTo(-10f, -150f); lineTo(10f, -150f); lineTo(0f, -132f); close()
    }
    private val pFillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        var mi = 0
        var ni = 0
        for (i in 0 until 36) {
            val a = Math.toRadians(i * 10.0)
            val sn = Math.sin(a).toFloat()
            val cs = -Math.cos(a).toFloat()
            if (i % 3 == 0) {
                majorTicks[mi++] = sn * 132f; majorTicks[mi++] = cs * 132f
                majorTicks[mi++] = sn * 114f; majorTicks[mi++] = cs * 114f
            } else {
                minorTicks[ni++] = sn * 132f; minorTicks[ni++] = cs * 132f
                minorTicks[ni++] = sn * 125f; minorTicks[ni++] = cs * 125f
            }
        }
    }

    override fun onScale(sx: Float, sy: Float) { s = minOf(sx, sy) }

    private fun headingDeg(t: DashValues): Int =
        if (t.headingDeg.isNaN()) -1 else (Math.round(t.headingDeg) % 360 + 360) % 360

    private fun hasTurn(t: DashValues) = t.routeActive && t.cardDir != 0 && t.cardMeters >= 0

    override fun signature(t: DashValues): Long {
        var h = 59L
        h = h * 31 + (if (t.speedKmh.isNaN()) -1 else t.speedKmh.toInt())
        h = h * 31 + headingDeg(t)
        h = h * 31 + (if (t.routeActive) 1 else 0)
        if (t.routeActive) {
            h = h * 31 + t.cardDir
            h = h * 31 + DashFmt.turnBucket(t.cardMeters)
            h = h * 31 + (t.nextRoad?.hashCode() ?: 0)
            h = h * 31 + t.etaClockMinute
            h = h * 31 + t.etaMinutes
            h = h * 31 + (if (t.remainKm.isNaN()) -1 else DashFmt.tenths(t.remainKm))
        }
        h = h * 31 + sl.signature(t)
        return h
    }

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        drawCompass(c, t, sx, sy)
        if (t.routeActive) drawRouteMiddle(c, t, sx, sy) else drawHeadingMiddle(c, t, sx, sy)
        drawRows(c, t, sx, sy)
    }

    // ------------------------------------------------------------------ compass

    private fun drawCompass(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val hd = headingDeg(t)
        c.save()
        c.translate(174f * sx, 222.5f * sy)
        c.scale(s, s)
        k.pFill.color = C_DIAL
        c.drawCircle(0f, 0f, 136f, k.pFill)
        k.pStroke.color = C_RING; k.pStroke.strokeWidth = 3f
        c.drawCircle(0f, 0f, 136f, k.pStroke)
        c.save()
        if (hd >= 0) c.rotate(-hd.toFloat())
        k.pStroke.color = C_TICK
        k.pStroke.strokeWidth = 3f
        c.drawLines(majorTicks, k.pStroke)
        k.pStroke.strokeWidth = 2f
        c.drawLines(minorTicks, k.pStroke)
        c.restore()
        if (hd >= 0) {
            // Letters stay upright; only their positions turn with the dial.
            k.pNum.textSize = 32f; k.pNum.textAlign = Paint.Align.CENTER
            for (i in 0 until 4) {
                val a = Math.toRadians((i * 90 - hd).toDouble())
                k.pNum.color = if (i == 0) C_NORTH else C_TEXT
                c.drawText(LETTERS[i], (Math.sin(a) * 85.5).toFloat(), (-Math.cos(a) * 85.5).toFloat() + 11f, k.pNum)
            }
            k.pFill.color = C_GREEN
            c.drawPath(needle, k.pFill)
            pFillStroke.color = C_BG; pFillStroke.strokeWidth = 2f
            c.drawPath(needle, pFillStroke)
            k.pFill.color = DashKit.C_AMBER
            c.drawPath(marker, k.pFill)
        }
        c.restore()

        // "047°  ตะวันออกเฉียงเหนือ", centred under the rose.
        val base = 392f * sy
        k.buf.setLength(0)
        if (hd < 0) k.buf.append("--") else {
            if (hd < 100) k.buf.append('0')
            if (hd < 10) k.buf.append('0')
            k.buf.append(hd)
        }
        k.buf.append('°')
        val name = if (hd < 0) null else names[DashFmt.compassIndex(hd.toFloat())]
        val w = sl.numWidth(k.pNum, 40f * s, name, 18f * s, 8f * s)
        sl.numUnit(c, k.pNum, 174f * sx - w / 2f, base, 40f * s, C_TEXT, name, 18f * s, C_LABEL, 8f * s)
    }

    // ------------------------------------------------------------------ middle

    private fun drawRouteMiddle(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val cx = 530f * sx
        val turn = hasTurn(t)
        // "เลี้ยวขวา ถนน…" or just "ถัดไป".
        k.buf.setLength(0)
        if (!turn) k.buf.append(sNext)
        else {
            k.buf.append(if (t.cardDir == 1) sLeft else if (t.cardDir == 2) sRight else sStraight)
            val road = t.nextRoad
            if (road != null) k.buf.append(' ').append(road)
        }
        k.pLabel.textSize = 22f * s; k.pLabel.color = C_GREEN; k.pLabel.textAlign = Paint.Align.CENTER
        val n = k.pLabel.breakText(k.buf, 0, k.buf.length, true, 376f * sx, null)
        c.drawText(k.buf, 0, n, cx, 108f * sy, k.pLabel)

        // Arrow + distance, centred as one run.
        val icon = 104f * s
        val gap = 14f * s
        k.buf.setLength(0)
        val unit: String
        if (!turn) { k.buf.append("--"); unit = k.sM }
        else if (t.cardMeters < 1000) { k.buf.append(t.cardMeters); unit = k.sM }
        else {
            val tn = (t.cardMeters / 100f + 0.5f).toInt()
            k.buf.append(tn / 10).append('.').append(tn % 10); unit = k.sKm
        }
        val maxW = 376f * sx - icon - gap
        val size = sl.fitSize(k.pNum, 116f * s, unit, 32f * s, 8f * s, maxW)
        val w = sl.numWidth(k.pNum, size, unit, 32f * s, 8f * s)
        val x0 = cx - (icon + gap + w) / 2f
        val midY = 175.5f * sy
        k.turnIcon(c, x0, midY - icon / 2f, icon, if (turn) t.cardDir else 3, if (turn) C_GREEN else C_OFF, 2.8f)
        sl.numUnit(c, k.pNum, x0 + icon + gap, midY + 0.36f * size, size, if (turn) C_TEXT else C_OFF, unit, 32f * s, C_LABEL, 8f * s)

        // Speed.
        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val sw = sl.numWidth(k.pNum, 96f * s, k.sKmh, 22f * s, 8f * s)
        sl.numUnit(c, k.pNum, cx - sw / 2f, 325f * sy, 96f * s, C_WHITE, k.sKmh, 22f * s, C_LABEL, 8f * s)
    }

    /** No route: the compass point in large type, the speed under it. Nothing route-related. */
    private fun drawHeadingMiddle(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val cx = 530f * sx
        val hd = headingDeg(t)
        k.buf.setLength(0)
        if (hd < 0) k.buf.append("--") else k.buf.append(DashFmt.COMPASS[DashFmt.compassIndex(hd.toFloat())])
        k.pNum.textSize = 130f * s; k.pNum.color = C_GREEN; k.pNum.textAlign = Paint.Align.CENTER
        c.drawText(k.buf, 0, k.buf.length, cx, 180f * sy, k.pNum)

        k.buf.setLength(0)
        if (t.speedKmh.isNaN()) k.buf.append("--") else k.buf.append(t.speedKmh.toInt())
        val sw = sl.numWidth(k.pNum, 110f * s, k.sKmh, 24f * s, 8f * s)
        sl.numUnit(c, k.pNum, cx - sw / 2f, 310f * sy, 110f * s, C_WHITE, k.sKmh, 24f * s, C_LABEL, 8f * s)
    }

    // ------------------------------------------------------------------ right column

    private fun drawRows(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val n = sl.collect(t)
        var extraCount = 0
        for (j in 0 until n) if (k.extras[sl.vis[j]] != DashExtra.HEADING) extraCount++
        val rows = extraCount + (if (t.routeActive) 3 else 0)
        if (rows == 0) return
        val pitch = 64f * sy
        val l = (1024f - 24f - 264f) * sx
        val r = (1024f - 24f) * sx
        var top = (425f * sy - rows * pitch) / 2f
        if (t.routeActive) {
            k.buf.setLength(0)
            DashFmt.clock(k.buf, t.etaClockMinute, k.is24h)
            row(c, k.sArrive, null, l, r, top, pitch, C_GREEN); top += pitch
            k.buf.setLength(0)
            if (t.etaMinutes < 0) k.buf.append("--") else k.buf.append(t.etaMinutes)
            row(c, sLeftWord, k.sMin, l, r, top, pitch, C_TEXT); top += pitch
            k.buf.setLength(0)
            if (t.remainKm.isNaN()) k.buf.append("--") else DashFmt.oneDecimal(k.buf, t.remainKm)
            row(c, sLeftWord, k.sKm, l, r, top, pitch, C_TEXT); top += pitch
        }
        for (j in 0 until n) {
            val e = k.extras[sl.vis[j]]
            if (e == DashExtra.HEADING) continue
            sl.value(e, t)
            row(c, k.text.label(e), k.text.unit(e), l, r, top, pitch, C_TEXT); top += pitch
        }
    }

    /** One row: label left, [DashKit.buf] + unit right, a hairline under it. */
    private fun row(c: Canvas, label: String, unit: String?, l: Float, r: Float, top: Float, pitch: Float, color: Int) {
        val base = top + pitch - 16f * s
        k.pLabel.textSize = 20f * s; k.pLabel.color = C_LABEL; k.pLabel.textAlign = Paint.Align.LEFT
        c.drawText(label, l, base, k.pLabel)
        val lw = k.pLabel.measureText(label)
        sl.numUnitRight(c, k.pNum, r, base, 42f * s, color, unit, 18f * s, C_LABEL, 6f * s, r - l - lw - 10f * s)
        k.pFill.color = C_RULE
        c.drawRect(l, top + pitch - 1f, r, top + pitch, k.pFill)
    }

    private companion object {
        val LETTERS = arrayOf("N", "E", "S", "W")
        const val C_BG = 0xFF0B1210.toInt()
        const val C_DIAL = 0xFF10201B.toInt()
        const val C_RING = 0xFF2E4A40.toInt()
        const val C_TICK = 0xFF9FB8AD.toInt()
        const val C_TEXT = 0xFFEDE6D6.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
        const val C_LABEL = 0xFFA9BBB1.toInt()
        const val C_GREEN = 0xFF62D2A2.toInt()
        const val C_NORTH = 0xFFFF6A5C.toInt()
        const val C_OFF = 0xFF3A4A44.toInt()
        const val C_RULE = 0xFF26362F.toInt()
    }
}
