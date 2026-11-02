// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Settings hub: 8 category rows plus a search box. Every category opens either an existing activity
 * or [SetupActivity] restricted to one section (its handlers are untouched). Reads no prefs.
 */
class SettingsHubActivity : AppCompatActivity() {

    private lateinit var categories: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var empty: TextView
    private lateinit var clear: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_hub)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.hub_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(v.paddingLeft, b.top, v.paddingRight, b.bottom)
            insets
        }
        categories = findViewById(R.id.hub_categories)
        results = findViewById(R.id.hub_results)
        empty = findViewById(R.id.hub_empty)
        clear = findViewById(R.id.hub_clear)
        findViewById<View>(R.id.hub_back).setOnClickListener { finish() }

        for (c in SettingsIndex.categories) {
            addRow(categories, getString(c.title), getString(c.sub), c.icon) {
                open(c.section, c.special, 0, getString(c.title))
            }
        }
        addRow(categories, getString(R.string.sr_bikes_title), getString(R.string.sr_bikes_sub), R.drawable.ic_set_bikes) {
            open("bikes", 0, 0, getString(R.string.sr_bikes_title))
        }

        val search = findViewById<EditText>(R.id.hub_search)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = runSearch(s?.toString().orEmpty())
        })
        clear.setOnClickListener { search.setText("") }
    }

    private fun runSearch(raw: String) {
        val q = raw.trim().lowercase()
        clear.visibility = if (raw.isEmpty()) View.GONE else View.VISIBLE
        results.removeAllViews()
        if (q.isEmpty()) {
            results.visibility = View.GONE
            empty.visibility = View.GONE
            categories.visibility = View.VISIBLE
            return
        }
        categories.visibility = View.GONE
        val hits = SettingsIndex.entries.filter {
            getString(it.label).lowercase().contains(q) || getString(it.keywords).lowercase().contains(q)
        }.take(20)
        results.visibility = if (hits.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
        for (e in hits) {
            val cat = SettingsIndex.categories.firstOrNull { it.section == e.section }
            val catName = getString(cat?.title ?: R.string.sr_bikes_title)
            addRow(results, getString(e.label), catName, cat?.icon ?: R.drawable.ic_set_bikes) {
                open(e.section, e.special, e.focusId, getString(e.label), catName)
            }
        }
    }

    private fun open(section: String, special: Int, focusId: Int, title: String, cat: String = title) {
        when (special) {
            SettingsIndex.DASH -> DashDesignActivity.start(this, fromSetup = true)
            SettingsIndex.CRASH -> CrashSettingsActivity.start(this)
            else -> SetupActivity.startSection(this, section, cat, focusId)
        }
    }

    private fun addRow(parent: ViewGroup, title: String, sub: String, icon: Int, onClick: () -> Unit) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_settings_row, parent, false)
        v.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.row_title).text = title
        v.findViewById<TextView>(R.id.row_sub).text = sub
        v.setOnClickListener { onClick() }
        parent.addView(v)
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, SettingsHubActivity::class.java))
        }
    }
}
