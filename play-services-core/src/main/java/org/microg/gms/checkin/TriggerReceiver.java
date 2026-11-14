/*
 * Copyright (C) 2013-2017 microG Project Team
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

package org.microg.gms.checkin;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.PendingIntentCompat;
import androidx.legacy.content.WakefulBroadcastReceiver;

import org.microg.gms.common.ForegroundServiceContext;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.os.Build.VERSION.SDK_INT;
import static org.microg.gms.checkin.CheckinService.EXTRA_FORCE_CHECKIN;
import static org.microg.gms.checkin.CheckinService.REGULAR_CHECKIN_INTERVAL;

public class TriggerReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = "GmsCheckinTrigger";
    private static final long MIN_RETRY_INTERVAL_MS = 60_000L;
    private static volatile boolean registered = false;
    private static volatile long lastRetryMs = 0L;

    /** Modern API: unlike getActiveNetworkInfo(), not subject to per-UID NetworkInfo BLOCKED filtering. */
    private static boolean hasInternet(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (SDK_INT >= 23) {
            Network network = cm.getActiveNetwork();
            NetworkCapabilities caps =
                    network == null ? null : cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NET_CAPABILITY_INTERNET);
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean force = "android.provider.Telephony.SECRET_CODE".equals(intent.getAction());

            if (!CheckinPreferences.isEnabled(context) && !force) {
                Log.d(TAG, "Ignoring " + intent + ": checkin is disabled");
                return;
            }

            // The callback firing means the INTERNET request is already satisfied.
            // Never re-register from inside it, or we get an unbounded self-loop.
            boolean fromNetworkCallback =
                    intent.hasExtra(ConnectivityManager.EXTRA_NETWORK_REQUEST);

            if (fromNetworkCallback || hasInternet(context) || force) {
                registered = false;
                if (LastCheckinInfo.read(context).getLastCheckin() >
                        System.currentTimeMillis() - REGULAR_CHECKIN_INTERVAL && !force) {
                    CheckinService.schedule(context);
                    return;
                }
                Intent subIntent = new Intent(context, CheckinService.class);
                subIntent.putExtra(EXTRA_FORCE_CHECKIN, force);
                startWakefulService(new ForegroundServiceContext(context), subIntent);
                return;
            }

            // Genuinely offline: at most one pending retry, rate-limited.
            if (SDK_INT >= 23 && !registered) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastRetryMs < MIN_RETRY_INTERVAL_MS) return;
                lastRetryMs = now;
                ConnectivityManager cm = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkRequest networkRequest = new NetworkRequest.Builder()
                        .addCapability(NET_CAPABILITY_INTERNET)
                        .build();
                Intent i = new Intent(context, TriggerReceiver.class);
                PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(
                        context, 0, i, FLAG_UPDATE_CURRENT, true);
                if (cm != null) {
                    cm.registerNetworkCallback(networkRequest, pendingIntent);
                    registered = true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }
}
