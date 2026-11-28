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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * The two ways into MicroG RE from outside: the launcher icon and the app-info entry.
 *
 * Android hands out the app-info (INFO) entry before the launcher one as the app's launch
 * intent, and the system installer's "Open" button only starts launcher intents. So the
 * app-info entry is on only while there is no launcher icon (the no-icon build, or the icon
 * hidden in App settings), where it keeps "Open" working on the App info page. With the icon
 * shown, the launcher entry is the launch intent and the installer's "Open" works.
 */
object LauncherEntries {
    private const val TAG = "LauncherEntries"
    private const val LAUNCHER = "org.microg.gms.ui.SettingsActivityLauncher"
    private const val INFO = "org.microg.gms.ui.SettingsActivityInfo"

    @JvmStatic
    fun isIconHidden(context: Context): Boolean = !isEnabled(context, LAUNCHER)

    @JvmStatic
    fun setIconHidden(context: Context, hidden: Boolean) {
        setEnabled(context, LAUNCHER, !hidden)
        setEnabled(context, INFO, hidden)
    }

    /**
     * Puts the app-info entry in line with the launcher icon. Run after an update (the entry
     * is new, and an icon hidden before keeps its state) and when the settings open.
     */
    @JvmStatic
    fun sync(context: Context) {
        try {
            val hidden = isIconHidden(context)
            if (isEnabled(context, INFO) != hidden) setEnabled(context, INFO, hidden)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync launch entries", e)
        }
    }

    private fun isEnabled(context: Context, name: String): Boolean {
        val pm = context.packageManager
        val component = ComponentName(context, name)
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            // Not changed at runtime: what the manifest of this build says
            else -> try {
                @Suppress("DEPRECATION")
                val flags = if (Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_DISABLED_COMPONENTS
                else PackageManager.GET_DISABLED_COMPONENTS
                pm.getActivityInfo(component, flags).enabled
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Launch entry $name not found", e)
                false
            }
        }
    }

    private fun setEnabled(context: Context, name: String, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, name),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
