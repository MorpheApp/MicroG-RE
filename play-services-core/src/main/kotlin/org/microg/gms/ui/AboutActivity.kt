/*
 * Copyright (C) 2026 Morphe.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.text.BidiFormatter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.R
import org.microg.gms.ui.updater.AppUpdater
import org.microg.tools.ui.WindowInsetsHelper

/**
 * Full-screen About page (laid out like Morphe Manager's About screen). Hosts [AboutFragment]
 * edge to edge without an app bar; Close and system back return to the settings.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowInsetsHelper.enableEdgeToEdge(this)
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val container = FrameLayout(this).apply { id = R.id.about_fragment_container }
        // Content scrolls under the transparent status bar; this strip keeps the clock and
        // icons readable by covering it with the page background.
        val statusBarScrim = View(this).apply {
            resolveColor(android.R.attr.colorBackground)?.let { setBackgroundColor(it) }
        }
        root.addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(statusBarScrim, FrameLayout.LayoutParams(MATCH_PARENT, 0, Gravity.TOP))
        ViewCompat.setOnApplyWindowInsetsListener(statusBarScrim) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            v.layoutParams = v.layoutParams.apply { height = top }
            insets
        }
        setContentView(root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.about_fragment_container, AboutFragment())
                .commit()
        }
    }
}

/**
 * Fills the About version card's status from the release check, as Morphe's About shows its own:
 * the answer the last check came back with straight away, then a fresh check that rewords it.
 * "Up to date", or "Update available: <version>" with an Update badge that opens the update
 * sheet. Nothing stands there before a check or after a failed one: neither means up to date.
 */
fun bindAboutUpdateStatus(activity: Activity, status: TextView, divider: View) {
    showAboutUpdateStatus(activity, status, divider, AppUpdater.lastSeenLatest(activity))
    val noIconVariant = activity.resources.getBoolean(R.bool.hide_launcher_icon_available)
    val includePrerelease = AppUpdater.includePrerelease(activity)
    Thread {
        val update = AppUpdater.fetchLatestUpdate(noIconVariant, includePrerelease)
        AppUpdater.rememberLatest(activity, update, includePrerelease)
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            showAboutUpdateStatus(activity, status, divider, update?.version)
        }
    }.start()
}

private fun showAboutUpdateStatus(activity: Activity, status: TextView, divider: View, latest: String?) {
    val card = status.rootView
    val updateRow = card.findViewById<View>(org.microg.tools.ui.R.id.about_update_row)
    val available = latest != null && AppUpdater.isNewerThanInstalled(latest)
    divider.visibility = if (latest != null) View.VISIBLE else View.GONE
    status.visibility = if (latest != null && !available) View.VISIBLE else View.GONE
    status.setText(org.microg.tools.ui.R.string.about_morphe_up_to_date)
    updateRow?.visibility = if (available) View.VISIBLE else View.GONE
    if (available) {
        card.findViewById<TextView>(org.microg.tools.ui.R.id.about_update_text)?.text =
            activity.getString(R.string.about_update_available, BidiFormatter.getInstance().unicodeWrap(latest))
        card.findViewById<TextView>(org.microg.tools.ui.R.id.about_update_badge)?.apply {
            setText(R.string.update_now)
            setOnClickListener { AppUpdater.checkManually(activity) }
        }
    }
}
