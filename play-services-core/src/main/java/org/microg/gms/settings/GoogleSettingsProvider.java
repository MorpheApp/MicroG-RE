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

package org.microg.gms.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.LocationManager;
import android.net.Uri;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class GoogleSettingsProvider extends ContentProvider {
    private static final String USE_LOCATION_FOR_SERVICES = "use_location_for_services";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"/partner".equals(uri.getPath())) return null;
        if (selection != null && !("name=?".equals(selection.replace(" ", ""))
                && selectionArgs != null && selectionArgs.length == 1
                && USE_LOCATION_FOR_SERVICES.equals(selectionArgs[0]))) return null;
        if (selection == null && selectionArgs != null && selectionArgs.length != 0) return null;

        String[] columns = projection == null ? new String[]{"name", "value"} : projection;
        for (String column : columns) {
            if (!"name".equals(column) && !"value".equals(column)) return null;
        }

        Context context = getContext();
        LocationManager manager = context == null ? null
                : (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // Legacy Maps consults this setting when Google's location-settings activity
        // exists. A null cursor disables all Maps providers even when Android allows
        // location. Reflect the actual switch/permissions, never override them.
        boolean hasLocationPermission = context != null && (
                ContextCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        String value = manager != null && hasLocationPermission && LocationManagerCompat.isLocationEnabled(manager) ? "1" : "0";
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            row.add("name".equals(column) ? USE_LOCATION_FOR_SERVICES : value);
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
