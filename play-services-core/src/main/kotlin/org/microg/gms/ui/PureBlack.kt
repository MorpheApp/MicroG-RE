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
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.google.android.gms.R

/**
 * "Pure black" from App settings: true black backgrounds in dark mode, on top of the Material
 * You (or static) palette. The overlay itself only has values in values-night, so it does
 * nothing in light mode.
 */
object PureBlack {
    const val PREF_KEY = "pref_pure_black"

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_KEY, false)

    /**
     * Applies the overlay to every activity as it is created. Registered after
     * DynamicColors, so it lands on top of the wallpaper colors.
     */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                apply(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // onActivityPreCreated only exists from Android 10
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) apply(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun apply(activity: Activity) {
        if (isEnabled(activity)) activity.theme.applyStyle(R.style.ThemeOverlay_MicroG_PureBlack, true)
    }
}
