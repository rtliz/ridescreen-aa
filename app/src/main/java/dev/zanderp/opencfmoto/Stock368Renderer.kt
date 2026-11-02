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
 * Design #1 "Stock 368G" (D2): a Zontes-368-style instrument screen around the map slot
 * (`mockups/dash-368g.html` D2, `mockups/dash-designs.html` #1). Every x scales by [sx], every y by
 * [sy], text and icons by s = min(sx, sy).
 *
 * The left column shows the chosen [extras] as panels (default trip km, ride time, max speed). Unknown
 * phone values show "--"; an unknown optional extra (fuel, tyres, coolant) is skipped and the
 * remaining panels share the column height, so there is never an empty panel.
 *
 * [drawOver] is the turn card + ETA strip over the map, hidden when no route is active.
 */
class Stock368Renderer(context: Context, private val extras: Array<DashExtra>) : DashRenderer {

    private var s = 1f
    private var w = DESIGN_W

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val pNumMed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }

    private val buf = StringBuilder(32)
    private val rect = RectF()
    /** panelPath[n - 1][i] = panel i when n panels share the column. */
    private val panelPath = Array(3) { n -> Array(n + 1) { Path() } }
    private val panelR = FloatArray(8)
    private val mapPath = Path()
    private val is24h = DateFormat.is24HourFormat(context)
    private val text = DashExtraText(context)
    private val visibleIdx = IntArray(3)

    // Unit-space icon paths (drawn through a scaled canvas).
    private val arrowLeft = Path().apply {
        moveTo(2f, 15f); lineTo(16f, 2f); lineTo(16f, 9f); lineTo(38f, 9f)
        lineTo(38f, 21f); lineTo(16f, 21f); lineTo(16f, 28f); close()
    }
    private val arrowRight = Path().apply {
        moveTo(38f, 15f); lineTo(24f, 2f); lineTo(24f, 9f); lineTo(2f, 9f)
        lineTo(2f, 21f); lineTo(24f, 21f); lineTo(24f, 28f); close()
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
    private val micCup = Path().apply { addArc(RectF(5f, 4f, 19f, 18f), 0f, 180f) }
    private val headband = Path().apply {
        moveTo(6f, 20f); lineTo(6f, 16f); arcTo(RectF(6f, 6f, 26f, 26f), 180f, 180f); lineTo(26f, 20f)
    }
    private val mountain = Path().apply {
        moveTo(1f, 19f); lineTo(8f, 7f); lineTo(12f, 13f); lineTo(16f, 5f); lineTo(23f, 19f); close()
    }
    private val octagon = Path().apply {
        moveTo(16f, 3f); lineTo(40f, 3f); lineTo(53f, 16f); lineTo(53f, 46f)
        lineTo(40f, 59f); lineTo(16f, 59f); lineTo(3f, 46f); lineTo(3f, 16f); close()
    }
    // Overlay turn icons (24-unit).
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

    // Strings (resolved once).
    private val sM = context.getString(R.string.dash368_unit_m)
    private val sKm = context.getString(R.string.dash368_unit_km)
    private val sMin = context.getString(R.string.dash368_unit_min)
    private val sArrive = context.getString(R.string.dash368_arrive)
    private val sAlt = context.getString(R.string.dash368_altitude)
    private val sAppOdo = context.getString(R.string.dash368_app_odo)
    private val sScale = context.getString(R.string.dash368_speed_scale)
    private val sTurnLeft = context.getString(R.string.dash368_turn_left)
    private val sTurnRight = context.getString(R.string.dash368_turn_right)

    // Overlay geometry (map rect in px), set in onScale.
    private var mapL = 0f
    private var mapT = 0f
    private var mapW = 0f
    private var mapH = 0f
    private var os = 1f

    override fun onScale(sx: Float, sy: Float) {
        s = minOf(sx, sy)
        w = DESIGN_W * sx
        val colH = 3 * ROW_H + 2 * ROW_GAP
        for (n in 1..3) {
            val rowH = (colH - (n - 1) * ROW_GAP) / n
            for (i in 0 until n) {
                val top = (BAR_H + i * (rowH + ROW_GAP)) * sy
                rect.set(PANEL_X * sx, top, (PANEL_X + PANEL_W) * sx, top + rowH * sy)
                panelR[0] = 4f * s; panelR[1] = 4f * s; panelR[2] = 18f * s; panelR[3] = 18f * s
                panelR[4] = 4f * s; panelR[5] = 4f * s; panelR[6] = 4f * s; panelR[7] = 4f * s
                panelPath[n - 1][i].reset()
                panelPath[n - 1][i].addRoundRect(rect, panelR, Path.Direction.CW)
            }
        }
        rect.set(MAP_X * sx, BAR_H * sy, (MAP_X + MAP_W) * sx, (BAR_H + MAP_H) * sy)
        mapPath.reset()
        mapPath.addRoundRect(rect, 10f * s, 10f * s, Path.Direction.CW)
        // Same rounding as the map slot DashScreen lays out.
        mapL = Math.round(MAP_X * sx).toFloat()
        mapT = Math.round(BAR_H * sy).toFloat()
        mapW = Math.round((MAP_X + MAP_W) * sx) - mapL
        mapH = Math.round((BAR_H + MAP_H) * sy) - mapT
        os = minOf(mapW / MAP_W, mapH / MAP_H)
    }

    override fun signature(t: DashValues): Long {
        var h = 17L
        h = h * 31 + q(t.speedKmh)
        h = h * 31 + (if (t.headingDeg.isNaN()) -1 else DashFmt.compassIndex(t.headingDeg))
        h = h * 31 + (if (t.altitudeM.isNaN()) Int.MIN_VALUE else Math.round(t.altitudeM))
        h = h * 31 + t.phoneBatteryPct
        h = h * 31 + (if (t.headsetConnected) 1 else 0)
        h = h * 31 + (if (t.gpsFix) 1 else 0)
        h = h * 31 + (if (t.recording) 1 else 0)
        h = h * 31 + (if (t.crashArmed) 1 else 0)
        h = h * 31 + t.nextTurnDir
        h = h * 31 + (if (t.nextTurnMeters < 0) -1 else t.nextTurnMeters / 10)
        h = h * 31 + (if (t.odometerKm.isNaN()) -1L else t.odometerKm.toLong())
        h = h * 31 + (if (t.odometerFromBook) 1 else 0)
        h = h * 31 + t.clockMinute
        h = h * 31 + text.signature(extras, t, is24h)
        return h
    }

    override fun overSignature(t: DashValues): Long {
        var h = 7L
        h = h * 31 + (if (t.routeActive) 1 else 0)
        if (t.routeActive) {
            h = h * 31 + t.cardDir
            h = h * 31 + DashFmt.turnBucket(t.cardMeters)
            h = h * 31 + t.etaClockMinute
            h = h * 31 + t.etaMinutes
            h = h * 31 + (if (t.remainKm.isNaN()) -1 else DashFmt.tenths(t.remainKm))
        }
        return h
    }

    private fun q(f: Float): Int = if (f.isNaN()) -1 else f.toInt()

    // ------------------------------------------------------------------ chrome

    override fun draw(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        c.drawColor(C_BG)
        drawTopBar(c, t, sx, sy)
        drawIconColumn(c, t, sx, sy)
        drawPanels(c, t, sx, sy)
        pFill.color = C_MAP_BG
        c.drawPath(mapPath, pFill)
        drawRightColumn(c, t, sx, sy)
        drawBottomBar(c, t, sx, sy)
    }

    private inline fun Canvas.icon(x: Float, y: Float, size: Float, viewBox: Float, block: () -> Unit) {
        save()
        translate(x, y)
        val k = size / viewBox
        scale(k, k)
        block()
        restore()
    }

    private fun drawTopBar(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val cy = BAR_H / 2f * sy
        val arrowW = 40f * s
        val lit = t.nextTurnDir != 0 && t.nextTurnMeters in 0..TURN_NEAR_M
        val leftLit = lit && t.nextTurnDir == 1
        val rightLit = lit && t.nextTurnDir == 2

        pFill.color = if (leftLit) C_GREEN else C_ARROW_OFF
        c.icon(16f * sx, cy - 15f * s, arrowW, 40f) { c.drawPath(arrowLeft, pFill) }

        if (lit) {
            buf.setLength(0)
            buf.append(if (leftLit) sTurnLeft else sTurnRight).append(' ').append(t.nextTurnMeters).append(' ').append(sM)
            pLabel.color = C_SEC; pLabel.textSize = 15f * s; pLabel.textAlign = Paint.Align.LEFT
            c.drawText(buf, 0, buf.length, 16f * sx + arrowW + 18f * sx, cy + 5f * s, pLabel)
        }

        val rx = w - 16f * sx - arrowW
        pFill.color = if (rightLit) C_GREEN else C_ARROW_OFF
        c.icon(rx, cy - 15f * s, arrowW, 40f) { c.drawPath(arrowRight, pFill) }
        var cursor = rx - 18f * sx

        if (t.headsetConnected) {
            val sz = 30f * s
            cursor -= sz
            pStroke.color = C_GREEN; pStroke.strokeWidth = 2f
            c.icon(cursor, cy - sz / 2f, sz, 32f) {
                c.drawPath(headband, pStroke)
                c.drawRoundRect(4f, 19f, 9f, 28f, 2f, 2f, pStroke)
                c.drawRoundRect(23f, 19f, 28f, 28f, 2f, 2f, pStroke)
            }
            cursor -= 18f * sx
        }

        val pct = t.phoneBatteryPct
        if (pct >= 0) {
            buf.setLength(0); buf.append(pct).append('%')
            pNumMed.color = C_SEC; pNumMed.textSize = 22f * s; pNumMed.textAlign = Paint.Align.RIGHT
            c.drawText(buf, 0, buf.length, cursor, cy + 8f * s, pNumMed)
            val tw = pNumMed.measureText(buf, 0, buf.length)
            val iconH = 26f * s
            val ix = cursor - tw - 6f * s - 16f * s
            pStroke.color = C_SEC; pStroke.strokeWidth = 2f
            c.icon(ix, cy - iconH / 2f, iconH, 26f) {
                c.drawRoundRect(2f, 4f, 14f, 24f, 2f, 2f, pStroke)
                c.drawLine(6f, 2f, 10f, 2f, pStroke)
                val fillH = 10f * pct / 100f
                if (fillH > 0.5f) {
                    pFill.color = C_BATT
                    c.drawRect(5f, 21f - fillH, 11f, 21f, pFill)
                }
            }
            cursor = ix - 18f * sx
        }

        if (t.clockMinute >= 0) {
            buf.setLength(0)
            DashFmt.clock(buf, t.clockMinute, is24h)
            pNum.color = C_TEXT; pNum.textSize = 32f * s; pNum.textAlign = Paint.Align.RIGHT
            c.drawText(buf, 0, buf.length, cursor, cy + 11f * s, pNum)
        }
    }

    private fun drawIconColumn(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val sz = 30f * s
        val x = (12f + 7f) * sx
        val areaTop = (BAR_H + 4f) * sy
        val area = (MAP_H - 8f) * sy
        val gap = (area - 4f * sz) / 4f
        fun slotY(i: Int) = areaTop + gap / 2f + i * (sz + gap)

        // GPS fix: green when we have one, grey when not.
        pStroke.color = if (t.gpsFix) C_GREEN else C_GREY; pStroke.strokeWidth = 2f
        pFill.color = pStroke.color
        c.icon(x, slotY(0), sz, 24f) {
            c.drawCircle(12f, 12f, 7f, pStroke)
            c.drawCircle(12f, 12f, 2.5f, pFill)
            c.drawLine(12f, 1f, 12f, 5f, pStroke); c.drawLine(12f, 19f, 12f, 23f, pStroke)
            c.drawLine(1f, 12f, 5f, 12f, pStroke); c.drawLine(19f, 12f, 23f, 12f, pStroke)
        }
        // Trip recording: red dot only while recording.
        if (t.recording) {
            pStroke.color = C_RED; pStroke.strokeWidth = 2f
            pFill.color = C_RED
            c.icon(x, slotY(1), sz, 24f) {
                c.drawCircle(12f, 12f, 10f, pStroke)
                c.drawCircle(12f, 12f, 5f, pFill)
            }
        }
        // Crash detection armed: green shield, hidden when off.
        if (t.crashArmed) {
            pStroke.color = C_GREEN; pStroke.strokeWidth = 2f
            c.icon(x, slotY(2), sz, 24f) {
                c.drawPath(shield, pStroke)
                c.drawPath(shieldTick, pStroke)
            }
        }
        // Voice / mic.
        pStroke.color = C_TEXT; pStroke.strokeWidth = 2f
        c.icon(x, slotY(3), sz, 24f) {
            c.drawRoundRect(9f, 2f, 15f, 14f, 3f, 3f, pStroke)
            c.drawPath(micCup, pStroke)
            c.drawLine(12f, 18f, 12f, 22f, pStroke)
        }
    }

    /** The chosen extras as panels; unknown optional ones are skipped and the rest grow to fill. */
    private fun drawPanels(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        var n = 0
        for (i in extras.indices) {
            if (n == 3) break
            if (text.visible(extras[i], t)) visibleIdx[n++] = i
        }
        if (n == 0) return
        val colH = 3 * ROW_H + 2 * ROW_GAP
        val rowH = (colH - (n - 1) * ROW_GAP) / n
        for (k in 0 until n) {
            val e = extras[visibleIdx[k]]
            buf.setLength(0)
            text.value(buf, e, t, is24h)
            val top = (BAR_H + k * (rowH + ROW_GAP)) * sy
            drawPanel(c, panelPath[n - 1][k], top, rowH * sy, text.label(e), text.unit(e), sx)
        }
    }

    /** One left panel: [label] top-left, the number already in [buf] bottom-right, optional [unit]. */
    private fun drawPanel(c: Canvas, path: Path, top: Float, h: Float, label: String, unit: String?, sx: Float) {
        val left = PANEL_X * sx
        val right = (PANEL_X + PANEL_W) * sx
        pFill.color = C_PANEL
        c.drawPath(path, pFill)
        pLabel.color = C_SEC; pLabel.textSize = 14f * s; pLabel.textAlign = Paint.Align.LEFT
        c.drawText(label, left + 14f * sx, top + h / 2f - 13f * s, pLabel)
        var nx = right - 14f * sx
        val by = top + h / 2f + 22f * s
        if (unit != null) {
            pLabel.textSize = 16f * s; pLabel.textAlign = Paint.Align.RIGHT
            c.drawText(unit, nx, by, pLabel)
            nx -= pLabel.measureText(unit) + 4f * s
        }
        pNum.color = C_TEXT; pNum.textSize = 34f * s; pNum.textAlign = Paint.Align.RIGHT
        c.drawText(buf, 0, buf.length, nx, by, pNum)
    }

    private fun drawRightColumn(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val x0 = RIGHT_X * sx
        val rw = RIGHT_W * sx

        // Big GPS speed + unit.
        buf.setLength(0)
        if (t.speedKmh.isNaN()) buf.append("--") else buf.append(t.speedKmh.toInt())
        pNum.color = C_TEXT; pNum.textSize = 104f * s; pNum.textAlign = Paint.Align.LEFT
        c.drawText(buf, 0, buf.length, x0, 130f * sy, pNum)
        pNum.textSize = 20f * s; pNum.color = C_SEC
        c.drawText("km/h", x0, 152f * sy, pNum)

        // Octagon with the compass heading.
        pStroke.color = C_AMBER; pStroke.strokeWidth = 3f
        c.icon(x0 + rw - 56f * s, 72f * sy, 62f * s, 62f) {
            c.drawPath(octagon, pStroke)
            buf.setLength(0)
            if (t.headingDeg.isNaN()) buf.append("--") else buf.append(DashFmt.COMPASS[DashFmt.compassIndex(t.headingDeg)])
            pNum.color = C_TEXT; pNum.textSize = 26f; pNum.textAlign = Paint.Align.CENTER
            c.drawText(buf, 0, buf.length, 28f, 40f, pNum)
        }

        // 13-segment speed bar, one segment per 10 km/h.
        pLabel.color = C_SEC; pLabel.textSize = 13f * s; pLabel.textAlign = Paint.Align.LEFT
        c.drawText(sScale, x0, 236f * sy, pLabel)
        val lit = if (t.speedKmh.isNaN()) 0 else minOf(13, (t.speedKmh / 10f).toInt())
        val gap = 3f * sx
        val segW = (rw - 12f * gap) / 13f
        val barTop = 244f * sy
        val barBot = 266f * sy
        pNumMed.textSize = 13f * s; pNumMed.textAlign = Paint.Align.CENTER
        for (i in 0 until 13) {
            val l = x0 + i * (segW + gap)
            pFill.color = when {
                i < lit -> C_TEXT
                i >= 8 -> C_RED_OFF
                else -> C_SEG_OFF
            }
            rect.set(l, barTop, l + segW, barBot)
            c.drawRect(rect, pFill)
            pNumMed.color = if (i >= 8) C_RED else C_DIM
            c.drawText(DIGITS[i + 1], l + segW / 2f, 283f * sy, pNumMed)
        }

        // Phone battery: 8 segments + %.
        val pct = t.phoneBatteryPct
        if (pct >= 0) {
            val cy = 362f * sy
            pStroke.color = C_SEC; pStroke.strokeWidth = 2f
            c.icon(x0, cy - 11f * s, 22f * s, 26f) {
                c.drawRoundRect(2f, 4f, 14f, 24f, 2f, 2f, pStroke)
                c.drawLine(6f, 2f, 10f, 2f, pStroke)
            }
            buf.setLength(0); buf.append(pct).append('%')
            pNumMed.color = C_SEC; pNumMed.textSize = 16f * s; pNumMed.textAlign = Paint.Align.RIGHT
            c.drawText(buf, 0, buf.length, x0 + rw, cy + 6f * s, pNumMed)
            val tw = pNumMed.measureText(buf, 0, buf.length)
            val sl = x0 + 16f * s + 8f * sx
            val sr = x0 + rw - tw - 8f * sx
            val g = 3f * sx
            val sw = (sr - sl - 7f * g) / 8f
            val on = pct * 8 / 100
            for (i in 0 until 8) {
                val l = sl + i * (sw + g)
                pFill.color = if (i < on) C_BATT else C_SEG_OFF
                rect.set(l, cy - 8f * sy, l + sw, cy + 8f * sy)
                c.drawRect(rect, pFill)
            }
        }
    }

    private fun drawBottomBar(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        val cy = (BAR_H + MAP_H + BAR_H / 2f) * sy

        pStroke.color = C_SEC; pStroke.strokeWidth = 2f
        c.icon(16f * sx, cy - 10f * s, 24f * s, 24f) { c.drawPath(mountain, pStroke) }
        var x = 16f * sx + 24f * s + 6f * s
        pLabel.color = C_SEC; pLabel.textSize = 14f * s; pLabel.textAlign = Paint.Align.LEFT
        c.drawText(sAlt, x, cy + 5f * s, pLabel)
        x += pLabel.measureText(sAlt) + 6f * s
        buf.setLength(0)
        if (t.altitudeM.isNaN()) buf.append("--") else buf.append(Math.round(t.altitudeM)).append(' ').append(sM)
        pNumMed.color = C_SEC; pNumMed.textSize = 22f * s; pNumMed.textAlign = Paint.Align.LEFT
        c.drawText(buf, 0, buf.length, x, cy + 8f * s, pNumMed)

        // "ODO 012345 km" = maintenance-book odometer (or the app's km) + the live trip.
        buf.setLength(0)
        buf.append("ODO ")
        if (t.odometerKm.isNaN() || t.odometerKm < 0.5) {
            buf.append("--")
        } else {
            val km = t.odometerKm.toLong().coerceIn(0L, 999_999L).toInt()
            if (t.odometerFromBook) {
                // Rider-entered odometer reading: classic six-digit zero-padded look.
                var div = 100_000
                while (div > 0) { buf.append(('0' + (km / div) % 10)); div /= 10 }
            } else {
                buf.append(km)   // only the app's own km so far: no fake leading zeros
            }
        }
        buf.append(" km")
        pNum.color = C_ODO; pNum.textSize = 28f * s; pNum.textAlign = Paint.Align.RIGHT
        pNum.letterSpacing = 0.035f
        val right = w - 16f * sx
        c.drawText(buf, 0, buf.length, right, cy + 10f * s, pNum)
        val ow = pNum.measureText(buf, 0, buf.length)
        pNum.letterSpacing = 0f
        pLabel.textSize = 13f * s; pLabel.textAlign = Paint.Align.RIGHT; pLabel.color = C_SEC
        c.drawText(sAppOdo, right - ow - 20f * sx, cy + 5f * s, pLabel)
    }

    // ------------------------------------------------------------------ over the map

    override fun drawOver(c: Canvas, t: DashValues, sx: Float, sy: Float) {
        if (!t.routeActive) return
        c.save()
        c.translate(mapL, mapT)
        if (t.cardDir != 0 && t.cardMeters >= 0) drawCard(c, t)
        drawStrip(c, t)
        c.restore()
    }

    private fun drawCard(c: Canvas, t: DashValues) {
        val s = os
        buf.setLength(0)
        DashFmt.turnDistance(buf, t.cardMeters, sM, sKm)
        pNum.textSize = 30f * s
        val tw = pNum.measureText(buf, 0, buf.length)
        val pad = 10f * s
        val icon = 36f * s
        val cw = pad + icon + 10f * s + tw + 14f * s
        val h = 56f * s
        rect.set(10f * s, 10f * s, 10f * s + cw, 10f * s + h)
        pFill.color = C_CARD
        c.drawRoundRect(rect, 12f * s, 12f * s, pFill)
        pStroke.color = C_EDGE; pStroke.strokeWidth = 1f
        c.drawRoundRect(rect, 12f * s, 12f * s, pStroke)
        pStroke.color = C_ACCENT; pStroke.strokeWidth = 2.4f * (icon / 24f)
        c.save()
        c.translate(rect.left + pad, rect.top + (h - icon) / 2f)
        c.scale(icon / 24f, icon / 24f)
        c.drawPath(if (t.cardDir == 1) turnLeft else if (t.cardDir == 2) turnRight else straight, pStroke)
        c.restore()
        pNum.color = C_TEXT; pNum.textAlign = Paint.Align.LEFT
        c.drawText(buf, 0, buf.length, rect.left + pad + icon + 10f * s, rect.top + h / 2f + 10f * s, pNum)
    }

    private fun drawStrip(c: Canvas, t: DashValues) {
        val s = os
        val h = 40f * s
        rect.set(10f * s, mapH - 10f * s - h, mapW - 10f * s, mapH - 10f * s)
        pFill.color = C_CARD
        c.drawRoundRect(rect, 10f * s, 10f * s, pFill)
        pStroke.color = C_EDGE; pStroke.strokeWidth = 1f
        c.drawRoundRect(rect, 10f * s, 10f * s, pStroke)
        val base = rect.centerY() + 8f * s
        pNum.textSize = 22f * s

        // Arrival clock time (left).
        buf.setLength(0)
        buf.append(sArrive).append(' ')
        DashFmt.clock(buf, t.etaClockMinute, is24h)
        pNum.color = C_BATT; pNum.textAlign = Paint.Align.LEFT
        c.drawText(buf, 0, buf.length, rect.left + 14f * s, base, pNum)

        // Minutes left (centre).
        buf.setLength(0)
        if (t.etaMinutes < 0) buf.append("--") else buf.append(t.etaMinutes)
        buf.append(' ').append(sMin)
        pNum.color = C_TEXT; pNum.textAlign = Paint.Align.CENTER
        c.drawText(buf, 0, buf.length, rect.centerX(), base, pNum)

        // Distance left (right).
        buf.setLength(0)
        if (t.remainKm.isNaN()) buf.append("--") else DashFmt.oneDecimal(buf, t.remainKm)
        buf.append(' ').append(sKm)
        pNum.textAlign = Paint.Align.RIGHT
        c.drawText(buf, 0, buf.length, rect.right - 14f * s, base, pNum)
    }

    private companion object {
        const val TURN_NEAR_M = 300
        const val DESIGN_W = 1024f
        const val BAR_H = 46f
        const val PANEL_X = 66f
        const val PANEL_W = 236f
        const val ROW_H = 107f
        const val ROW_GAP = 6f
        const val MAP_X = 312f
        const val MAP_W = 476f
        const val MAP_H = 333f
        const val RIGHT_X = 798f
        const val RIGHT_W = 214f

        const val C_BG = 0xFF07090B.toInt()
        const val C_TEXT = 0xFFF2F4F5.toInt()
        const val C_SEC = 0xFFB9C1C8.toInt()
        const val C_DIM = 0xFF8A949E.toInt()
        const val C_PANEL = 0xFF14181C.toInt()
        const val C_MAP_BG = 0xFF1A1F24.toInt()
        const val C_GREEN = 0xFF2EE05A.toInt()
        const val C_ARROW_OFF = 0xFF1C2A20.toInt()
        const val C_GREY = 0xFF6B747C.toInt()
        const val C_RED = 0xFFE8322E.toInt()
        const val C_RED_OFF = 0xFF4A1D1C.toInt()
        const val C_SEG_OFF = 0xFF2A3138.toInt()
        const val C_AMBER = 0xFFF2B21B.toInt()
        const val C_BATT = 0xFF7FD48A.toInt()
        const val C_ODO = 0xFFD5DADF.toInt()
        const val C_CARD = 0xFF0E1114.toInt()
        const val C_EDGE = 0xFF2A3138.toInt()
        const val C_ACCENT = 0xFFF5A623.toInt()

        val DIGITS = Array(14) { it.toString() }
    }
}
