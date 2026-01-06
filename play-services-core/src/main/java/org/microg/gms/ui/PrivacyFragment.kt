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

package org.microg.gms.ui

import android.accounts.AccountManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialSharedAxis
import org.microg.gms.auth.AuthConstants
import org.microg.gms.auth.AuthManager

class PrivacyFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey)

        val pref = findPreference<Preference>(AuthManager.PREF_AUTH_VISIBLE)
        if (pref != null) {
            if (Build.VERSION.SDK_INT < 26) {
                pref.isVisible = false
            } else {
                pref.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                        if (newValue is Boolean) {
                            val am = AccountManager.get(requireContext())
                            for (account in am.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)) {
                                am.setAccountVisibility(
                                    account,
                                    AccountManager.PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE,
                                    if (newValue) AccountManager.VISIBILITY_VISIBLE else AccountManager.VISIBILITY_NOT_VISIBLE
                                )
                            }
                        }
                        true
                    }
            }
        }
    }
}