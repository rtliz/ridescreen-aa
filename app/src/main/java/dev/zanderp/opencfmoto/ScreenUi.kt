// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import android.app.Activity
import android.view.View

/** Small helpers shared by the restyled secondary screens (visual only). */
object ScreenUi {
    /** Wires the round "back" button (R.id.scr_back) of the screen header, if the layout has one. */
    fun bindBack(a: Activity) {
        a.findViewById<View?>(R.id.scr_back)?.setOnClickListener { a.finish() }
    }

    fun dp(a: android.content.Context, v: Int): Int = (v * a.resources.displayMetrics.density).toInt()

    /** Redesign header for code-built screens: round back button + title. */
    fun header(a: Activity, title: String): android.widget.LinearLayout {
        val c = a as android.content.Context
        return android.widget.LinearLayout(c).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(c, 56)
            addView(android.widget.ImageButton(c).apply {
                setBackgroundResource(R.drawable.bg_scr_back)
                setImageResource(R.drawable.ic_scr_back)
                setColorFilter(androidx.core.content.ContextCompat.getColor(c, R.color.text_primary))
                contentDescription = c.getString(R.string.scr_back)
                setOnClickListener { a.finish() }
            }, android.widget.LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)))
            addView(android.widget.TextView(c).apply {
                text = title
                textSize = 21f
                setTextColor(androidx.core.content.ContextCompat.getColor(c, R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(c, 12), 0, 0, 0)
            }, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
