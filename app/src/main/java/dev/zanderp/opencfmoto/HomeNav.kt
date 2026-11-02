// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

/**
 * The 5-tab bottom bar of the redesign (mockups/app-redesign.html): Home · Map · Trips · My bike · More.
 *
 * No fragments: each tab is the existing activity. [attach] wraps the activity's content view in a
 * column with the bar under it, so the activity's own layout and inset handling stay as they were
 * (it gets the system-bar insets minus the bottom; the bar takes the bottom inset).
 *
 * Stack: MainActivity stays at the bottom. From Main a tab starts its activity on top; from another
 * top-level screen a tab replaces that screen; Home goes back to Main.
 */
object HomeNav {

    enum class Tab(val itemId: Int) {
        HOME(R.id.home_rd_tab_home),
        MAP(R.id.home_rd_tab_map),
        TRIPS(R.id.home_rd_tab_trips),
        BIKE(R.id.home_rd_tab_bike),
        MORE(R.id.home_rd_tab_more),
    }

    /**
     * Adds the bar under [activity]'s content (call right after setContentView). [onTab] may handle a
     * tab itself (return true), e.g. MainActivity routes Map through its own Map button.
     */
    fun attach(activity: Activity, current: Tab, onTab: ((Tab) -> Boolean)? = null): BottomNavigationView {
        val dp = activity.resources.displayMetrics.density
        fun color(id: Int) = ContextCompat.getColor(activity, id)

        val nav = BottomNavigationView(activity).apply {
            inflateMenu(R.menu.home_rd_bottom_nav)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            setBackgroundColor(color(R.color.home_rd_bg2))
            elevation = 0f
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            val tint = ColorStateList(states, intArrayOf(color(R.color.brand_orange), color(R.color.home_rd_muted)))
            itemIconTintList = tint
            itemTextColor = tint
            itemIconSize = (26 * dp).toInt()
            isItemActiveIndicatorEnabled = true
            itemActiveIndicatorColor = ColorStateList.valueOf(color(R.color.home_rd_amber_soft))
            itemActiveIndicatorWidth = (58 * dp).toInt()
            itemActiveIndicatorHeight = (32 * dp).toInt()
            itemRippleColor = ColorStateList.valueOf(0x22FFFFFF)
            setItemTextAppearanceActive(R.style.HomeRd_NavLabel)
            setItemTextAppearanceInactive(R.style.HomeRd_NavLabel)
            // Before the listener: selecting the current tab must not navigate.
            selectedItemId = current.itemId
            setOnItemReselectedListener { }
            setOnItemSelectedListener { item ->
                val tab = Tab.entries.firstOrNull { it.itemId == item.itemId } ?: return@setOnItemSelectedListener false
                if (onTab?.invoke(tab) != true) go(activity, tab)
                false // the screen we open shows its own bar; this one keeps its tab highlighted
            }
        }

        // Bar = 1 dp top border (.tabs border-top) + the BottomNavigationView, padded for the gesture/nav bar.
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.home_rd_bg2))
            addView(View(activity).apply { setBackgroundColor(color(R.color.outline)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp.toInt())))
            addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val page = content.getChildAt(0)
        content.removeView(page)
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(column) { _, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            bar.setPadding(b.left, 0, b.right, b.bottom)
            // The page keeps its own listener; it just no longer pads for the bottom bar.
            val pageInsets = WindowInsetsCompat.Builder(insets)
                .setInsets(SafeInsets.BARS, Insets.of(b.left, b.top, b.right, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(page, pageInsets)
            WindowInsetsCompat.CONSUMED
        }
        return nav
    }

    private fun go(activity: Activity, tab: Tab) {
        when (tab) {
            Tab.HOME -> activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            Tab.MAP -> {
                // Same as MainActivity.onMapPressed(): the Map button is off while connecting, and a
                // stale NAV_TO must not follow the rider into the hub.
                val phase = ConnectionState.phase
                val live = phase == Phase.STREAMING || phase == Phase.MIRRORING
                if (phase.busy && !live) return
                if (!live) GpxSession.abandonStaleNavigation("map hub")
                GpxActivity.start(activity)
            }
            Tab.TRIPS -> TripsListActivity.start(activity)
            Tab.BIKE -> GarageActivity.start(activity)
            Tab.MORE -> MoreActivity.start(activity)
        }
        if (activity !is MainActivity) activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
