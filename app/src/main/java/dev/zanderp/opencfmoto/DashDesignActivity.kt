// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * "Dash screen design" picker: an Android Auto info card, then a 2-column grid of the custom
 * designs with live previews (sample values, no tick, no receivers), the selected one marked.
 * Tapping a selected card (or its "Options" button) opens that design's extras sheet. Map theme and
 * AA DPI rows sit at the bottom. Choices apply the next time the map starts on the bike.
 */
class DashDesignActivity : AppCompatActivity() {

    private val cards = mutableListOf<Pair<DashDesign, MaterialCardView>>()
    private val previews = mutableMapOf<DashDesign, DashDesignView>()
    private lateinit var sample: DashValues

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash_design)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dash_design_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        sample = DashSamples.preview(this)

        findViewById<View>(R.id.dash_design_aa_card).setOnClickListener {
            // Info only: show the existing connection / setup screen, change nothing here.
            if (intent.getBooleanExtra(EXTRA_FROM_SETUP, false)) finish() else SetupActivity.start(this)
        }
        findViewById<MaterialSwitch>(R.id.dash_connect_opens_dash).apply {
            isChecked = MapPrefs.connectOpensDash(this@DashDesignActivity)
            setOnCheckedChangeListener { _, on -> MapPrefs.setConnectOpensDash(this@DashDesignActivity, on) }
        }
        buildGrid()

        findViewById<MaterialButton>(R.id.dash_theme_auto).setOnClickListener { setMapTheme(MapTheme.AUTO) }
        findViewById<MaterialButton>(R.id.dash_theme_day).setOnClickListener { setMapTheme(MapTheme.DAY) }
        findViewById<MaterialButton>(R.id.dash_theme_night).setOnClickListener { setMapTheme(MapTheme.NIGHT) }
        for ((id, dpi) in DPI_IDS) findViewById<MaterialButton>(id).setOnClickListener { setAaDpi(dpi) }
        refresh()
    }

    // ---------------------------------------------------------------- grid

    private fun buildGrid() {
        val grid = findViewById<LinearLayout>(R.id.dash_design_grid)
        val gap = dp(8)
        var row: LinearLayout? = null
        var n = 0   // cards in the current group
        var first = true
        for (d in DashDesign.entries) {
            if (d == DashDesign.NEON_RING) {
                // Start of the speedometer-only group: finish the half-filled row, add a heading.
                if (n % 2 == 1) row!!.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).also { it.marginStart = gap / 2 })
                grid.addView(TextView(this).apply {
                    setText(R.string.dash_group_speedo)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    it.topMargin = dp(20)
                })
                n = 0
            }
            if (n % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                }
                grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    if (!first) it.topMargin = gap
                })
            }
            first = false
            val card = makeCard(d)
            row!!.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                if (n % 2 == 0) it.marginEnd = gap / 2 else it.marginStart = gap / 2
            })
            cards += d to card
            n++
        }
        // Odd count in the last group: keep the last card half width.
        if (n % 2 == 1) {
            row!!.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).also { it.marginStart = gap / 2 })
        }
    }

    private fun makeCard(d: DashDesign): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1.5f)
            isClickable = true
            isFocusable = true
        }
        val frame = FrameLayout(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val preview = DashDesignView(this)
        preview.bind(d, d.newRenderer(this, DashPrefs.extras(this, d)), DashDesignView.Mode.PREVIEW, sample)
        previews[d] = preview
        col.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val name = TextView(this).apply {
            text = numberedName(d)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        col.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(8)
        })
        if (!d.implemented) {
            col.addView(TextView(this).apply {
                setText(R.string.dash_design_coming_soon)
                setTextColor(ContextCompat.getColor(context, R.color.status_idle))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            card.alpha = 0.5f
        } else if (d.supportedExtras.isNotEmpty()) {
            col.addView(MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                setText(R.string.dash_design_options)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minHeight = dp(48)
                minimumHeight = dp(48)
                setPadding(0, 0, 0, 0)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
                setOnClickListener { openOptions(d) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        frame.addView(col)
        val check = TextView(this).apply {
            tag = TAG_CHECK
            text = "✓"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.on_brand))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.brand_orange))
            }
            contentDescription = getString(R.string.dash_design_selected, getString(d.labelRes))
        }
        frame.addView(check, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.BOTTOM or Gravity.END).also {
            it.setMargins(0, 0, dp(8), dp(8))
        })
        card.addView(frame)
        card.setOnClickListener { onCardTapped(d) }
        return card
    }

    private fun numberedName(d: DashDesign): CharSequence {
        val num = d.number.toString()
        val sb = SpannableStringBuilder(num).append("  ").append(getString(d.labelRes))
        sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_orange)), 0, num.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, num.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun onCardTapped(d: DashDesign) {
        if (!d.implemented) {
            Toast.makeText(this, R.string.dash_design_coming_soon, Toast.LENGTH_SHORT).show()
            return
        }
        // Tapping any design means "use my custom design": Auto only picks it when the bike reports a
        // 368 at a wide resolution (and never in the phone preview), so riders saw the old screen.
        if (MapPrefs.dashLayout(this) != DashLayout.STOCK_368) MapPrefs.setDashLayout(this, DashLayout.STOCK_368)
        val current = MapPrefs.dashDesign(this)
        if (current == d) {
            if (d.supportedExtras.isNotEmpty()) openOptions(d)
            return
        }
        MapPrefs.setDashDesign(this, d)
        refresh()
        Toast.makeText(
            this,
            getString(R.string.dash_design_selected, getString(d.labelRes)) + " · " + getString(R.string.dash_design_applies_next),
            Toast.LENGTH_SHORT,
        ).show()
    }

    // ---------------------------------------------------------------- options sheet

    private fun openOptions(d: DashDesign) {
        val dialog = BottomSheetDialog(this)
        val chosen = DashPrefs.extras(this, d).toMutableSet()
        val pad = dp(16)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        }
        col.addView(TextView(this).apply {
            text = getString(R.string.dash_opts_title, getString(d.labelRes))
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val preview = DashDesignView(this)
        fun rebindPreview() {
            val arr = d.supportedExtras.filter { it in chosen }.toTypedArray()
            preview.bind(d, d.newRenderer(this, arr), DashDesignView.Mode.PREVIEW, sample)
        }
        rebindPreview()
        col.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(10)
        })
        col.addView(smallText(getString(R.string.dash_opts_preview_note), R.color.status_idle))
        val counter = TextView(this).apply {
            setTextColor(ContextCompat.getColor(context, R.color.brand_orange))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        fun refreshCounter() { counter.text = getString(R.string.dash_opts_slots, chosen.size, d.slots) }
        refreshCounter()
        col.addView(counter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(10)
        })

        if (d.supportedExtras.isEmpty()) col.addView(smallText(getString(R.string.dash_opts_none), R.color.text_secondary))
        for (e in d.supportedExtras) {
            val sw = MaterialSwitch(this).apply {
                text = getString(e.labelRes)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minHeight = dp(48)
                isChecked = e in chosen
                // No sensor source yet: shown dimmed, but can be pre-enabled to appear by itself later.
                if (e.needsSensor) alpha = 0.6f
            }
            sw.setOnCheckedChangeListener { btn, on ->
                if (on && e !in chosen && chosen.size >= d.slots) {
                    btn.isChecked = false
                    Toast.makeText(this, getString(R.string.dash_opts_full, d.slots), Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                if (on) chosen += e else chosen -= e
                DashPrefs.setExtras(this, d, chosen)
                refreshCounter()
                rebindPreview()
                previews[d]?.bind(d, d.newRenderer(this, DashPrefs.extras(this, d)), DashDesignView.Mode.PREVIEW, sample)
            }
            col.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            when {
                e.needsSensor -> col.addView(smallText(getString(R.string.dash_opts_needs_sensor), R.color.status_busy))
                e.optional -> col.addView(smallText(getString(R.string.dash_opts_optional_hint), R.color.text_secondary))
            }
        }

        if (DashExtra.RANGE_EST in d.supportedExtras) {
            col.addView(smallText(getString(R.string.dash_opts_tank), R.color.text_primary).also { it.setPadding(0, dp(12), 0, 0) })
            val tank = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = getString(R.string.dash_opts_tank_hint)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setHintTextColor(ContextCompat.getColor(context, R.color.status_idle))
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                DashPrefs.tankLiters(context)?.let { setText(trimFloat(it)) }
            }
            tank.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val v = s?.toString()?.trim()?.replace(',', '.')?.toFloatOrNull()
                    DashPrefs.setTankLiters(this@DashDesignActivity, v?.takeIf { it in 1f..100f })
                }
            })
            col.addView(tank, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        col.addView(MaterialButton(this).apply {
            setText(R.string.dash_opts_done)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(12)
        })
        val scroll = ScrollView(this).apply { addView(col) }
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun smallText(s: String, color: Int) = TextView(this).apply {
        text = s
        setTextColor(ContextCompat.getColor(context, color))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    }

    private fun trimFloat(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

    // ---------------------------------------------------------------- theme / DPI

    /** Same side effects as [SetupActivity]'s map theme row. */
    private fun setMapTheme(theme: MapTheme) {
        NightPrefs.setTheme(this, theme)
        AaVideoBridge.nightSink?.invoke(NightPrefs.isNightNow(this))
        refresh()
        Toast.makeText(this, getString(R.string.setup_toast_map_theme, getString(theme.labelRes)), Toast.LENGTH_SHORT).show()
    }

    private fun setAaDpi(dpi: Int?) {
        VideoPrefs.setDpiOverride(this, dpi)
        refresh()
        Toast.makeText(
            this,
            if (dpi == null) getString(R.string.setup_aa_dpi_auto) else getString(R.string.setup_aa_dpi) + ": $dpi",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun refresh() {
        val selected = MapPrefs.dashDesign(this)
        val amber = ContextCompat.getColor(this, R.color.brand_orange)
        val outline = ContextCompat.getColor(this, R.color.outline)
        val surface = ContextCompat.getColor(this, R.color.surface)
        for ((d, card) in cards) {
            val on = d == selected
            card.strokeColor = if (on) amber else outline
            card.setCardBackgroundColor(if (on) SELECTED_BG else surface)
            card.findViewWithTag<View>(TAG_CHECK)?.visibility = if (on) View.VISIBLE else View.GONE
        }
        highlight(
            NightPrefs.theme(this),
            R.id.dash_theme_auto to MapTheme.AUTO,
            R.id.dash_theme_day to MapTheme.DAY,
            R.id.dash_theme_night to MapTheme.NIGHT,
        )
        highlight(VideoPrefs.dpiOverride(this), *DPI_IDS.toTypedArray())
    }

    private fun <T> highlight(selected: T, vararg pairs: Pair<Int, T>) {
        val onColor = ContextCompat.getColor(this, R.color.brand_orange)
        val onText = ContextCompat.getColor(this, R.color.on_brand)
        val offColor = ContextCompat.getColor(this, R.color.surface_high)
        val offText = ContextCompat.getColor(this, R.color.text_primary)
        for ((id, value) in pairs) {
            val btn = findViewById<MaterialButton>(id)
            val on = value == selected
            btn.backgroundTintList = ColorStateList.valueOf(if (on) onColor else offColor)
            btn.setTextColor(if (on) onText else offText)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val EXTRA_FROM_SETUP = "from_setup"
        private const val TAG_CHECK = "dash_check"
        private const val SELECTED_BG = 0xFF1D1A12.toInt()
        private val DPI_IDS = listOf<Pair<Int, Int?>>(
            R.id.dash_dpi_auto to null,
            R.id.dash_dpi_120 to 120,
            R.id.dash_dpi_140 to 140,
            R.id.dash_dpi_160 to 160,
            R.id.dash_dpi_180 to 180,
            R.id.dash_dpi_240 to 240,
            R.id.dash_dpi_320 to 320,
        )

        fun start(ctx: Context, fromSetup: Boolean = false) {
            val i = Intent(ctx, DashDesignActivity::class.java).putExtra(EXTRA_FROM_SETUP, fromSetup)
            if (ctx.findActivity() == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }

        private fun Context.findActivity(): Activity? {
            var c: Context? = this
            while (c is ContextWrapper) {
                if (c is Activity) return c
                c = c.baseContext
            }
            return null
        }
    }
}

/**
 * Sample of the Android Auto screen for the picker card: a simple Google-Maps-like picture (light
 * map, blue route, green turn banner). Static; it shows no data.
 */
class AaPreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, Math.round(w * 425f / 1024f))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val k = w / 1024f
        p.style = Paint.Style.FILL
        p.color = 0xFF2B3A46.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF3E5262.toInt()
        p.strokeWidth = 18f * k
        c.drawLine(0f, h * 0.55f, w, h * 0.55f, p)
        c.drawLine(w * 0.62f, 0f, w * 0.62f, h, p)
        p.color = 0xFF4C8DF6.toInt()
        p.strokeWidth = 14f * k
        c.drawLine(w * 0.62f, h, w * 0.62f, h * 0.55f, p)
        c.drawLine(w * 0.62f, h * 0.55f, w, h * 0.55f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF0F7A4B.toInt()
        c.drawRoundRect(16f * k, 16f * k, 380f * k, 130f * k, 14f * k, 14f * k, p)
        p.color = 0xFF111822.toInt()
        c.drawRoundRect(16f * k, h - 100f * k, 560f * k, h - 16f * k, 14f * k, 14f * k, p)
    }
}
