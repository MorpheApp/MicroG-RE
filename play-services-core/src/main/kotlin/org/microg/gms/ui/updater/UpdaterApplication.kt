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

import android.app.Application

/**
 * Schedules the daily background update check on app start, so the alarm exists even if
 * the user never opens the settings screen or reboots.
 *
 * [UpdateCheckScheduler.schedule] is idempotent: it registers the same PendingIntent, so
 * re-calling it (from any process or any later launch) refreshes the existing alarm
 * instead of stacking duplicates.
 */
class UpdaterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UpdateCheckScheduler.schedule(this)
    }
}