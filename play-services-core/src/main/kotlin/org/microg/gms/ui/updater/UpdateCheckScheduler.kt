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

package org.microg.gms.ui.updater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit

/**
 * Daily background update check while the app is not actively used.
 *
 * Uses [AlarmManager] instead of WorkManager because WorkManager's initializer is
 * disabled in this application's merged manifest.
 */
object UpdateCheckScheduler {

    const val ACTION_CHECK = "org.microg.gms.ui.updater.CHECK"

    private const val REQUEST_CODE = 0x5A17
    private val INTERVAL_MS = TimeUnit.DAYS.toMillis(1)

    /** Schedules (or keeps) the daily background update check. */
    @JvmStatic
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context)
        // Inexact repeating alarm: low battery impact; may be deferred slightly while dozing.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            INTERVAL_MS,
            pending
        )
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, UpdateCheckReceiver::class.java).setAction(ACTION_CHECK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Runs the update check and posts a notification when a newer release is available. */
class UpdateCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            UpdateCheckScheduler.schedule(context)
            return
        }
        if (intent?.action != UpdateCheckScheduler.ACTION_CHECK) return

        val pendingResult = goAsync()
        Thread {
            try {
                val noiconVariant =
                    context.resources.getBoolean(com.google.android.gms.R.bool.hide_launcher_icon_available)
                val includePrerelease = AppUpdater.includePrerelease(context)
                val update = AppUpdater.fetchLatestUpdate(noiconVariant, includePrerelease)
                if (update == null || !AppUpdater.isNewerThanInstalled(update)) return@Thread
                val ignored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(PREFS_IGNORED_VERSION, null)
                if (ignored != null && AppUpdater.compareVersions(update.version, ignored) <= 0) return@Thread
                AppUpdater.postUpdateNotification(context, update)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val PREFS_NAME = "org.microg.gms_updater"
        private const val PREFS_IGNORED_VERSION = "ignored_version"
    }
}
