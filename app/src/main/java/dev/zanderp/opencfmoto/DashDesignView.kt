// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Hosts one [DashRenderer] and does the work every design shares: scaling from the 1024×425 design
 * canvas, the 4 Hz [DashTelemetry] tick, the phone-battery receiver, redraw-only-on-change, and the
 * map-slot rectangle. Three modes:
 *  - [Mode.LIVE]: the chrome under the map. Owns the tick and the battery receiver (while attached)
 *    and repaints [overlay] too.
 *  - [Mode.OVERLAY]: the cards over the map. Not clickable (touches fall through to the map); it is
 *    repainted by its LIVE partner and never ticks itself.
 *  - [Mode.PREVIEW]: picker card. Draws chrome + a sample map + overlay in one pass from fixed
 *    [values]; no tick, no receivers. Keeps the 1024:425 aspect from its width.
 */
class DashDesignView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Mode { LIVE, OVERLAY, PREVIEW }

    private var mode = Mode.LIVE
    private var design: DashDesign = DashDesign.DEFAULT
    private var renderer: DashRenderer? = null
    private var values: DashValues = DashTelemetry

    /** The over-the-map view repainted from this view's tick (LIVE only). */
    var overlay: DashDesignView? = null

    /** Called with (left, top, width, height) in px of the map slot whenever the size is known. */
    var onMapRect: ((Int, Int, Int, Int) -> Unit)? = null
        set(v) { field = v; reportMapRect() }

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var batteryOn = false
    private var lastSig = Long.MIN_VALUE
    private var lastOverSig = Long.MIN_VALUE
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            DashTelemetry.refresh(context)
            val r = renderer
            if (r != null) {
                val sig = r.signature(values)
                if (sig != lastSig) { lastSig = sig; invalidate() }
                val ov = overlay
                if (ov != null) {
                    val osig = r.overSignature(values)
                    if (osig != lastOverSig) { lastOverSig = osig; ov.invalidate() }
                }
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    private var sx = 1f
    private var sy = 1f

    // Preview-only: sample map + "not built yet" placeholder.
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pRoad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val mapRectPx = RectF()
    private val mapClip = Path()
    private val route = Path()
    private val numText = StringBuilder(4)

    /**
     * Attach [design] drawn by [renderer]. LIVE/OVERLAY views read [DashTelemetry]; PREVIEW reads
     * [values]. A null [renderer] (preview of a design not built yet) draws a numbered placeholder.
     */
    fun bind(design: DashDesign, renderer: DashRenderer?, mode: Mode, values: DashValues = DashTelemetry) {
        this.design = design
        this.renderer = renderer
        this.mode = mode
        this.values = if (mode == Mode.PREVIEW) values else DashTelemetry
        if (mode == Mode.OVERLAY) { isClickable = false; isFocusable = false }
        if (width > 0 && height > 0) applySize(width, height)
        lastSig = Long.MIN_VALUE
        lastOverSig = Long.MIN_VALUE
        invalidate()
        if (mode == Mode.LIVE && isAttachedToWindow) start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mode != Mode.PREVIEW) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, Math.round(w * DESIGN_H / DESIGN_W))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mode == Mode.LIVE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (running || renderer == null) return
        running = true
        if (!batteryOn) { DashTelemetry.startBattery(context); batteryOn = true }
        lastSig = Long.MIN_VALUE
        lastOverSig = Long.MIN_VALUE
        main.post(tick)
    }

    private fun stop() {
        running = false
        main.removeCallbacks(tick)
        if (batteryOn) { DashTelemetry.stopBattery(context); batteryOn = false }
    }

    /** Stop the tick and the battery receiver (also happens on detach). Safe to call twice. */
    fun release() {
        stop()
        onMapRect = null
        overlay = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        applySize(w, h)
        lastSig = Long.MIN_VALUE
        lastOverSig = Long.MIN_VALUE
        reportMapRect()
    }

    private fun applySize(w: Int, h: Int) {
        sx = w / DESIGN_W
        sy = h / DESIGN_H
        renderer?.onScale(sx, sy)
        val m = design.mapRect
        if (mode == Mode.PREVIEW && m != null) {
            mapRectPx.set(m.left * sx, m.top * sy, m.right * sx, m.bottom * sy)
            val r = design.mapRadius * minOf(sx, sy)
            mapClip.reset()
            mapClip.addRoundRect(mapRectPx, r, r, Path.Direction.CW)
            // A sample route: up from the bottom, right at 40 % height (as in the mockups).
            val x = mapRectPx.left + mapRectPx.width() * 0.45f
            val y = mapRectPx.top + mapRectPx.height() * 0.38f
            route.reset()
            route.moveTo(x, mapRectPx.bottom)
            route.lineTo(x, y)
            route.lineTo(mapRectPx.right, y)
        }
    }

    private fun reportMapRect() {
        if (width <= 0 || height <= 0 || mode != Mode.LIVE) return
        val m = design.mapRect ?: return
        val l = Math.round(m.left * sx)
        val t = Math.round(m.top * sy)
        onMapRect?.invoke(l, t, Math.round(m.right * sx) - l, Math.round(m.bottom * sy) - t)
    }

    override fun onDraw(canvas: Canvas) {
        val r = renderer
        if (r == null) {
            if (mode == Mode.PREVIEW) drawPlaceholder(canvas)
            return
        }
        when (mode) {
            Mode.LIVE -> r.draw(canvas, values, sx, sy)
            Mode.OVERLAY -> r.drawOver(canvas, values, sx, sy)
            Mode.PREVIEW -> {
                r.draw(canvas, values, sx, sy)
                if (design.showsMap) {
                    drawSampleMap(canvas)
                    r.drawOver(canvas, values, sx, sy)
                }
            }
        }
    }

    /** Stand-in for the live map in picker previews (blocks, roads and the route in amber). */
    private fun drawSampleMap(c: Canvas) {
        val s = minOf(sx, sy)
        c.save()
        c.clipPath(mapClip)
        pFill.color = C_MAP_BG
        c.drawRect(mapRectPx, pFill)
        val l = mapRectPx.left
        val t = mapRectPx.top
        val w = mapRectPx.width()
        val h = mapRectPx.height()
        pRoad.color = C_ROAD
        pRoad.strokeWidth = 14f * s
        c.drawLine(l, t + h * 0.38f, l + w, t + h * 0.38f, pRoad)
        c.drawLine(l + w * 0.45f, t, l + w * 0.45f, t + h, pRoad)
        pRoad.color = C_ROAD2
        pRoad.strokeWidth = 8f * s
        c.drawLine(l, t + h * 0.8f, l + w, t + h * 0.8f, pRoad)
        c.drawLine(l + w * 0.15f, t, l + w * 0.15f, t + h, pRoad)
        c.drawLine(l + w * 0.85f, t, l + w * 0.85f, t + h, pRoad)
        pRoad.color = C_ROUTE
        pRoad.strokeWidth = 9f * s
        c.drawPath(route, pRoad)
        pFill.color = C_ROUTE
        c.drawCircle(l + w * 0.45f, t + h * 0.74f, 12f * s, pFill)
        c.restore()
    }

    private fun drawPlaceholder(c: Canvas) {
        c.drawColor(C_PLACEHOLDER)
        numText.setLength(0)
        numText.append(design.number)
        pNum.color = C_PLACEHOLDER_TEXT
        pNum.textSize = 160f * minOf(sx, sy)
        c.drawText(numText, 0, numText.length, width / 2f, height / 2f + 56f * minOf(sx, sy), pNum)
    }

    companion object {
        const val DESIGN_W = 1024f
        const val DESIGN_H = 425f
        private const val TICK_MS = 250L
        private const val C_MAP_BG = 0xFF1A1F24.toInt()
        private const val C_ROAD = 0xFF39424C.toInt()
        private const val C_ROAD2 = 0xFF2E363E.toInt()
        private const val C_ROUTE = 0xFFF5A623.toInt()
        private const val C_PLACEHOLDER = 0xFF0E1114.toInt()
        private const val C_PLACEHOLDER_TEXT = 0xFF2A3138.toInt()
    }
}

/**
 * The inflated bike screen for one [DashDesign]: [root] is the whole canvas (aim handlebar/touch
 * events here), [mapRoot] is `presentation_gpx` (bind [GpxDashUi] to it with
 * `compact = true, mapVisible = showsMap`), [view] draws the chrome. A design without a map keeps
 * [mapRoot] laid out but INVISIBLE: no drawing, no touches, location and nav keep running.
 */
class DashScreen private constructor(
    val design: DashDesign,
    val root: FrameLayout,
    val mapRoot: View,
    val view: DashDesignView,
) {
    val showsMap: Boolean get() = design.showsMap

    /** Release the 4 Hz tick and battery receiver. */
    fun release() = view.release()

    companion object {
        /** [design] not built yet ([DashDesign.factory] null) falls back to [DashDesign.DEFAULT]. */
        fun inflate(ctx: Context, design: DashDesign): DashScreen {
            val use = if (design.implemented) design else DashDesign.DEFAULT
            // Extras are resolved once here: the renderer never looks prefs up while drawing.
            val renderer = use.newRenderer(ctx, DashPrefs.extras(ctx, use))!!
            val inflater = LayoutInflater.from(ctx)
            val root = inflater.inflate(R.layout.presentation_dash, null) as FrameLayout
            val slot = root.findViewById<FrameLayout>(R.id.dash_map_slot)
            val view = root.findViewById<DashDesignView>(R.id.dash_view)
            val overlay = root.findViewById<DashDesignView>(R.id.dash_overlay)
            val map = inflater.inflate(R.layout.presentation_gpx, slot, false)
            slot.addView(
                map,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            view.bind(use, renderer, DashDesignView.Mode.LIVE)
            if (use.showsMap) {
                overlay.bind(use, renderer, DashDesignView.Mode.OVERLAY)
                view.overlay = overlay
                // Round the map's corners to match the drawn frame.
                var radius = 0f
                if (use.mapRadius > 0f) {
                    slot.clipToOutline = true
                    slot.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, v.width, v.height, radius)
                        }
                    }
                }
                val rectW = use.mapRect!!.width()
                view.onMapRect = { l, t, w, h ->
                    radius = use.mapRadius * w / rectW
                    val lp = FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.START)
                    lp.leftMargin = l
                    lp.topMargin = t
                    slot.layoutParams = lp
                    slot.invalidateOutline()
                }
            } else {
                // No map in this design: keep the map bound (GPS, route, turn cues) but never shown.
                slot.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                )
                slot.visibility = View.INVISIBLE
                overlay.visibility = View.GONE
            }
            return DashScreen(use, root, map, view)
        }
    }
}
