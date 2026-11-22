/*
 * Copyright (C) 2018 microG Project Team
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

package org.microg.gms.gcm;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.microg.gms.checkin.LastCheckinInfo;
import org.microg.gms.common.HttpFormClient;
import org.microg.gms.utils.ExtendedPackageInfo;

import java.io.IOException;

import static org.microg.gms.gcm.GcmConstants.ERROR_SERVICE_NOT_AVAILABLE;
import static org.microg.gms.gcm.GcmConstants.EXTRA_ERROR;
import static org.microg.gms.gcm.GcmConstants.EXTRA_REGISTRATION_ID;
import static org.microg.gms.gcm.GcmConstants.EXTRA_RETRY_AFTER;
import static org.microg.gms.gcm.GcmConstants.EXTRA_UNREGISTERED;

public class PushRegisterManager {
    private static final String TAG = "GmsGcmRegisterMgr";

    public static RegisterResponse unregister(Context context, String packageName, String pkgSignature, String sender, String info) {
        GcmDatabase database = new GcmDatabase(context);
        RegisterResponse response = new RegisterResponse();
        try {
            response = new RegisterRequest()
                    .build(context)
                    .sender(sender)
                    .info(info)
                    .checkin(LastCheckinInfo.read(context))
                    .app(packageName, pkgSignature)
                    .delete(true)
                    .getResponse();
        } catch (IOException e) {
            Log.w(TAG, e);
        }
        if (!packageName.equals(response.deleted)) {
            database.noteAppRegistrationError(packageName, response.responseText);
        } else {
            database.noteAppUnregistered(packageName, pkgSignature);
        }
        database.close();
        return response;
    }

    public interface BundleCallback {
        void onResult(Bundle bundle);
    }

    public static void completeRegisterRequest(Context context, GcmDatabase database, RegisterRequest request, BundleCallback callback) {
        completeRegisterRequest(context, database, null, request, callback);
    }

    public static void completeRegisterRequest(Context context, GcmDatabase database, String requestId, RegisterRequest request, BundleCallback callback) {
        String realPackage = request.app;
        if (request.app != null) {
            // RE changes start: Resolve spoofed package and signature for modded Google apps
            String spoofedPackage = org.microg.gms.common.PackageSpoofUtils.spoofPackageName(context.getPackageManager(), realPackage);
            if (spoofedPackage != null && !spoofedPackage.equals(realPackage)) {
                request.realApp = realPackage;
                request.app = spoofedPackage;
            }
            // RE changes end

            ExtendedPackageInfo packageInfo = new ExtendedPackageInfo(context, realPackage);
            if (request.appVersion <= 0)
                request.appVersion = packageInfo.getShortVersionCode();
            if (!request.delete) {
                if (request.appSignature == null) {
                    request.appSignature = packageInfo.getFirstCertificateSha1Hex();
                }
                // RE changes start: Resolve spoofed signature if declared
                request.realAppSignature = request.appSignature;
                String spoofedSig = org.microg.gms.common.PackageSpoofUtils.spoofStringSignature(context.getPackageManager(), realPackage, request.appSignature);
                if (spoofedSig != null) {
                    request.appSignature = spoofedSig;
                }
                // RE changes end
                request.sdkVersion = packageInfo.getTargetSdkVersion();
                if (!request.hasExtraParam(GcmConstants.EXTRA_APP_VERSION_NAME))
                    request.extraParam(GcmConstants.EXTRA_APP_VERSION_NAME, packageInfo.getVersionName());
            }

            // RE changes start: Strip unauthenticated FIS extras for spoofed/cloned Google apps
            if (request.realApp != null) {
                request.removeExtraParam("appid");
                request.removeExtraParam("gmp_app_id");
            }
            // RE changes end
        }

        // RE changes start: Check registration and prefs using real installed package
        GcmDatabase.App app = database.getApp(realPackage);
        // RE changes end
        GcmPrefs prefs = GcmPrefs.get(context);
        if (!request.delete) {
            if (!prefs.isEnabled() ||
                    (app != null && !app.allowRegister) ||
                    LastCheckinInfo.read(context).getLastCheckin() <= 0 ||
                    (app == null && prefs.getConfirmNewApps())) {
                Bundle bundle = new Bundle();
                bundle.putString(EXTRA_ERROR, ERROR_SERVICE_NOT_AVAILABLE);
                callback.onResult(bundle);
                return;
            }
        } else {
            // RE changes start: Use realPackage for unregistration check
            if (database.getRegistrationsByApp(realPackage).isEmpty()) {
                Bundle bundle = new Bundle();
                bundle.putString(EXTRA_UNREGISTERED, attachRequestId(realPackage, requestId));
                callback.onResult(bundle);
                return;
            }
            // RE changes end
        }

        request.getResponseAsync(new HttpFormClient.Callback<RegisterResponse>() {
            @Override
            public void onResponse(RegisterResponse response) {
                callback.onResult(handleResponse(database, request, response, requestId));
            }

            @Override
            public void onException(Exception e) {
                Log.w(TAG, e);
                callback.onResult(handleResponse(database, request, e, requestId));
            }
        });
    }



    private static Bundle handleResponse(GcmDatabase database, RegisterRequest request, RegisterResponse response, String requestId) {
        return handleResponse(database, request, response, null, requestId);
    }

    private static Bundle handleResponse(GcmDatabase database, RegisterRequest request, Exception e, String requestId) {
        return handleResponse(database, request, null, e, requestId);
    }

    private static Bundle handleResponse(GcmDatabase database, RegisterRequest request, RegisterResponse response, Exception e, String requestId) {
        Bundle resultBundle = new Bundle();
        // RE changes start: Record registration against the real installed package and signature
        String targetApp = (request.realApp != null && !request.realApp.isEmpty()) ? request.realApp : request.app;
        String targetSig = (request.realAppSignature != null && !request.realAppSignature.isEmpty()) ? request.realAppSignature : request.appSignature;
        // RE changes end
        if (response == null && e == null) {
            resultBundle.putString(EXTRA_ERROR, attachRequestId(ERROR_SERVICE_NOT_AVAILABLE, requestId));
        } else if (e != null) {
            if (e.getMessage() != null && e.getMessage().startsWith("Error=")) {
                String errorMessage = e.getMessage().substring(6);
                database.noteAppRegistrationError(targetApp, errorMessage);
                resultBundle.putString(EXTRA_ERROR, attachRequestId(errorMessage, requestId));
            } else {
                resultBundle.putString(EXTRA_ERROR, attachRequestId(ERROR_SERVICE_NOT_AVAILABLE, requestId));
            }
        } else {
            if (!request.delete) {
                if (response.error != null) {
                    database.noteAppRegistrationError(targetApp, response.error);
                    resultBundle.putString(EXTRA_ERROR, attachRequestId(response.error, requestId));
                } else if (response.token == null) {
                    database.noteAppRegistrationError(targetApp, response.responseText);
                    resultBundle.putString(EXTRA_ERROR, attachRequestId(ERROR_SERVICE_NOT_AVAILABLE, requestId));
                } else {
                    database.noteAppRegistered(targetApp, targetSig, response.token);
                    // RE changes start: Register alias mapping if spoofed
                    if (!targetApp.equals(request.app)) {
                        database.notePackageAlias(request.app, targetApp);
                    }
                    // RE changes end
                    resultBundle.putString(EXTRA_REGISTRATION_ID, attachRequestId(response.token, requestId));
                }
            } else {
                if (!request.app.equals(response.deleted) && !request.app.equals(response.token) && !request.sender.equals(response.token) && !"".equals(response.token)) {
                    database.noteAppRegistrationError(targetApp, response.error != null ? response.error : response.responseText);
                    resultBundle.putString(EXTRA_ERROR, attachRequestId(ERROR_SERVICE_NOT_AVAILABLE, requestId));
                } else {
                    database.noteAppUnregistered(targetApp, targetSig);
                    resultBundle.putString(EXTRA_UNREGISTERED, attachRequestId(targetApp, requestId));
                }
            }

            if (response.retryAfter != null && !response.retryAfter.contains(":")) {
                resultBundle.putLong(EXTRA_RETRY_AFTER, Long.parseLong(response.retryAfter));
            }
        }
        return resultBundle;
    }

    public static String attachRequestId(String msg, String requestId) {
        if (requestId == null) return msg;
        return "|ID|" + requestId + "|" + msg;
    }
}
