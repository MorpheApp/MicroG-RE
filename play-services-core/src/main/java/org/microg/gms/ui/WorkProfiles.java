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

package org.microg.gms.ui;

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Checks about the work profile of the device.
 */
public class WorkProfiles {
    private static final String TAG = "WorkProfiles";

    /**
     * Whether the device has a managed work profile.
     * <p>
     * The settings page that grants the ability to interact across profiles reads the work profile
     * from the user manager and does not handle a device without one, where it crashes instead of
     * showing the page. A device can also have a profile of another kind, such as a private space,
     * for which the ability to interact across profiles is offered as well, so the kind of the
     * profile has to be checked and not only its existence.
     */
    public static boolean hasManagedWorkProfile(Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager == null) return false;

        UserHandle self = Process.myUserHandle();
        for (UserHandle profile : userManager.getUserProfiles()) {
            if (profile.equals(self)) continue;
            if (isManagedProfile(userManager, profile)) return true;
        }
        return false;
    }

    /**
     * Whether the given profile is a managed profile.
     * <p>
     * {@link UserManager} only reports this for the current user, so the identifier of another
     * profile is queried through the method that takes a user id, which is not part of the public
     * API. If it cannot be called, a managed profile is assumed, so the ability to interact across
     * profiles stays available.
     */
    private static boolean isManagedProfile(UserManager userManager, UserHandle profile) {
        try {
            Method isManagedProfile = UserManager.class.getMethod("isManagedProfile", int.class);
            // UserHandle.hashCode() is the identifier of the user.
            Object result = isManagedProfile.invoke(userManager, profile.hashCode());
            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            Log.w(TAG, "Could not check if profile " + profile + " is a managed profile", e);
            return true;
        }
    }
}
