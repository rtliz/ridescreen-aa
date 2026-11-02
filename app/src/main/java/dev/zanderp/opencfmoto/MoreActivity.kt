// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

/**
 * "More" tab (mockups/app-redesign.html frame 23): every entry point that is not a tab, grouped,
 * with a label under each icon. Things that live in MainActivity (scan, mirror, live share, Help,
 * settings) go back to it with [EXTRA_ACTION] and run there through the same views as before.
 */
class MoreActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(buildContent())
        HomeNav.attach(this, HomeNav.Tab.MORE)
    }

    override fun onResume() {
        super.onResume()
        // Live share / parking rows depend on state that changes elsewhere.
        fillList()
    }

    private lateinit var list: LinearLayout

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            id = R.id.home_rd_more_root
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        // Header: title + settings (.row min-height 56).
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(16f), dp(4f), dp(16f), dp(4f))
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.home_rd_more_title)
            textSize = 24f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_rs_settings)
            imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_home_rd_iconbtn)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            contentDescription = getString(R.string.home_rd_more_settings)
            setOnClickListener { SettingsHubActivity.start(this@MoreActivity) }
        }, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        root.addView(header)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), 0, dp(16f), dp(20f))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun fillList() {
        list.removeAllViews()

        section(R.string.home_rd_more_riding).apply {
            row(R.drawable.ic_rs_gamepad, R.string.home_rd_more_controls, getString(R.string.home_rd_more_controls_sub)) {
                startActivity(Intent(this@MoreActivity, ControlsActivity::class.java))
            }
            row(R.drawable.ic_rs_monitor, R.string.home_rd_more_hud, getString(R.string.home_rd_more_hud_sub)) {
                startActivity(Intent(this@MoreActivity, HudViewActivity::class.java))
            }
            if (LiveShare.available) {
                val sub = getString(if (LiveShare.active) R.string.home_tile_live_on else R.string.home_rd_more_live_sub)
                row(R.drawable.ic_rs_live, R.string.home_rd_more_live, sub) { backToMain(ACTION_LIVE) }
            }
            row(R.drawable.ic_rs_users, R.string.home_rd_more_pax, getString(R.string.home_rd_more_pax_sub)) {
                CompanionActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_timer, R.string.home_rd_more_trip, getString(R.string.home_rd_more_trip_sub)) {
                TripActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_cast, R.string.home_rd_more_mirror, getString(R.string.home_rd_more_mirror_sub)) {
                backToMain(ACTION_MIRROR)
            }
            Parking.get(this@MoreActivity)?.let { spot ->
                row(R.drawable.ic_rs_parking, R.string.home_rd_more_parking, getString(R.string.home_rd_more_parking_sub)) {
                    if (Parking.hasPhoto(this@MoreActivity)) ParkingPhotoActivity.start(this@MoreActivity)
                    else try { startActivity(Parking.walkIntent(spot)) } catch (_: Exception) {
                        Toast.makeText(this@MoreActivity, R.string.open_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        section(R.string.home_rd_more_bike).apply {
            row(R.drawable.ic_rs_fuel, R.string.home_rd_more_fuel, getString(R.string.home_rd_more_fuel_sub)) {
                FuelActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_wrench, R.string.home_rd_more_maint, getString(R.string.home_rd_more_maint_sub)) {
                MaintenanceActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_live, R.string.home_rd_more_ble, getString(R.string.home_rd_more_ble_sub)) {
                BleSurveyActivity.start(this@MoreActivity)
            }
        }

        section(R.string.home_rd_more_stats).apply {
            row(R.drawable.ic_rs_trophy, R.string.home_rd_more_trophies, null) {
                TrophiesActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_traffic, R.string.home_rd_more_traffic, null) {
                TrafficLightsActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_film, R.string.home_rd_more_video, getString(R.string.home_rd_more_video_sub)) {
                TripsListActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_star, R.string.home_rd_more_year, getString(R.string.home_rd_more_year_sub)) {
                YearInReviewActivity.start(this@MoreActivity)
            }
        }

        section(R.string.home_rd_more_app).apply {
            row(R.drawable.ic_rs_qr, R.string.home_rd_more_scan, null) { backToMain(ACTION_SCAN) }
            row(R.drawable.ic_rs_settings, R.string.home_rd_more_settings, null) { SettingsHubActivity.start(this@MoreActivity) }
            row(R.drawable.ic_rs_layout, R.string.home_rd_more_dash_design, getString(R.string.home_rd_more_dash_design_sub)) {
                DashDesignActivity.start(this@MoreActivity)
            }
            row(R.drawable.ic_rs_help, R.string.home_rd_more_guide, null) { HelpMenu.openGuide(this@MoreActivity) }
            row(R.drawable.ic_rs_info, R.string.home_rd_more_help, getString(R.string.home_rd_more_help_sub)) {
                backToMain(ACTION_HELP)
            }
            row(R.drawable.ic_rs_info, R.string.home_rd_more_about, null) { AboutActivity.start(this@MoreActivity) }
        }
    }

    /** Group label (.lbl) + a card (.card) that the rows go into. */
    private fun section(label: Int): LinearLayout {
        list.addView(TextView(this).apply {
            text = getString(label)
            textSize = 13f
            letterSpacing = 0.04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.home_rd_muted))
            setPadding(dp(2f), dp(14f), 0, dp(6f))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_home_rd_card)
            setPadding(dp(14f), dp(2f), dp(14f), dp(2f))
        }
        list.addView(card)
        return card
    }

    /** One row (.rowi): icon box · title + detail · chevron; min 56 dp, divider between rows. */
    private fun LinearLayout.row(icon: Int, title: Int, sub: String?, action: () -> Unit) {
        if (childCount > 0) addView(View(context).apply { setBackgroundColor(color(R.color.home_rd_divider)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(0, dp(8f), 0, dp(8f))
            isClickable = true
            isFocusable = true
            val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = ta.getDrawable(0)
            ta.recycle()
            setOnClickListener { action() }
        }
        row.addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.brand_orange))
            setBackgroundResource(R.drawable.bg_home_rd_row_icon)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = getString(title)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.text_primary))
        })
        if (!sub.isNullOrBlank()) texts.addView(TextView(context).apply {
            text = sub
            textSize = 13.5f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(color(R.color.home_rd_muted))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12f); marginEnd = dp(8f)
        })
        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_rs_chevron_right)
            imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.home_rd_muted))
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Runs [action] in MainActivity (same views and handlers as the old home buttons). */
    private fun backToMain(action: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACTION, action),
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ACTION = "home_rd_more_action"
        const val ACTION_SCAN = "scan"
        const val ACTION_MIRROR = "mirror"
        const val ACTION_LIVE = "live"
        const val ACTION_HELP = "help"
        const val ACTION_SETTINGS = "settings"

        fun start(ctx: Context) = ctx.startActivity(Intent(ctx, MoreActivity::class.java))
    }
}
