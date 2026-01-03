/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.R
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import org.microg.gms.checkin.CheckinPreferences
import org.microg.gms.gcm.GcmDatabase
import org.microg.gms.gcm.GcmPrefs
import org.microg.gms.ui.settings.SettingsProvider
import org.microg.gms.ui.settings.getAllSettingsProviders
import org.microg.tools.ui.ResourceSettingsFragment

class SettingsFragment : ResourceSettingsFragment() {

    companion object {
        private const val TAG = "SettingsFragment"

        const val PREF_ABOUT = "pref_about"
        const val PREF_GCM = "pref_gcm"
        const val PREF_PRIVACY = "pref_privacy"
        const val PREF_CHECKIN = "pref_checkin"
        const val PREF_ACCOUNTS = "pref_accounts"
        const val PREF_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"
        const val PREF_GITHUB = "pref_github"

        private const val PREF_GITHUB_URL = "https://github.com/MorpheApp/MicroG-RE"
        const val PREF_IGNORE_BATTERY_OPTIMIZATION = "pref_ignore_battery_optimization"
    }

    private val createdPreferences = mutableListOf<Preference>()

    private val requestIgnoreBatteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateBatteryOptimizationPreference()
        }

    init {
        preferencesResource = R.xml.preferences_start
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setupStaticPreferenceClickListeners()
        updateAboutSummary()
        loadStaticEntries()
        updateBatteryOptimizationPreference()
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<ExtendedFloatingActionButton>(R.id.preference_fab)?.visibility =
            View.GONE
        updateBatteryOptimizationPreference()
        updateHideLauncherIconSwitchState()
        updateGcmSummary()
        updateCheckinSummary()
        updateDynamicEntries()
    }

    private fun setupStaticPreferenceClickListeners() {
        findPreference<Preference>(PREF_ACCOUNTS)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.accountManagerFragment)
            true
        }
        findPreference<Preference>(PREF_CHECKIN)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openCheckinSettings)
            true
        }
        findPreference<Preference>(PREF_GCM)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openGcmSettings)
            true
        }
        findPreference<Preference>(PREF_PRIVACY)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.privacyFragment)
            true
        }
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.setOnPreferenceChangeListener { _, newValue ->
            toggleActivityVisibility(MainSettingsActivity::class.java, !(newValue as Boolean))
            true
        }
        findPreference<Preference>(PREF_GITHUB)?.setOnPreferenceClickListener {
            openGithub()
            true
        }
        findPreference<Preference>(PREF_ABOUT)?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.openAbout)
            true
        }
    }

    private fun updateAboutSummary() {
        findPreference<Preference>(PREF_ABOUT)?.summary = getString(
            org.microg.tools.ui.R.string.about_version_str, AboutFragment.getSelfVersion(context)
        )
    }

    private fun loadStaticEntries() {
        val ctx = context ?: return
        getAllSettingsProviders(ctx).flatMap { it.getEntriesStatic(ctx) }
            .forEach { entry -> entry.createPreference(ctx) }
    }

    private fun updateDynamicEntries() {
        lifecycleScope.launch {
            val ctx = context ?: return@launch
            val entries = getAllSettingsProviders(ctx).flatMap { it.getEntriesDynamic(ctx) }

            createdPreferences.forEach { preference ->
                if (entries.none { it.key == preference.key }) preference.isVisible = false
            }

            entries.forEach { entry ->
                val preference = createdPreferences.find { it.key == entry.key }
                if (preference != null) preference.fillFromEntry(entry)
                else entry.createPreference(ctx)
            }
        }
    }

    private val Context.isIgnoringBatteryOptimizations: Boolean
        get() = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(
            packageName
        ) == true

    private fun updateBatteryOptimizationPreference() {
        val ctx = context ?: return
        findPreference<Preference>(PREF_IGNORE_BATTERY_OPTIMIZATION)?.apply {
            isVisible = !ctx.isIgnoringBatteryOptimizations
            setOnPreferenceClickListener {
                requestIgnoringBatteryOptimizations()
                true
            }
        }
    }

    private fun requestIgnoringBatteryOptimizations() {
        val ctx = context ?: return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${ctx.packageName}".toUri()
        }
        try {
            requestIgnoreBatteryOptimizationLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                requestIgnoreBatteryOptimizationLauncher.launch(fallbackIntent)
            } catch (e2: ActivityNotFoundException) {
                Log.w(TAG, "Device does not support ignoring battery optimizations", e2)
            }
        }
    }

    private fun toggleActivityVisibility(activityClass: Class<*>, showActivity: Boolean) {
        val ctx = context ?: return
        val component = ComponentName(ctx, activityClass)
        val newState = if (showActivity) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        ctx.packageManager.setComponentEnabledSetting(
            component, newState, PackageManager.DONT_KILL_APP
        )
    }

    private fun updateHideLauncherIconSwitchState() {
        val isVisible = isIconActivityVisible(MainSettingsActivity::class.java)
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.isChecked = !isVisible
    }

    private fun isIconActivityVisible(activityClass: Class<*>): Boolean {
        val ctx = context ?: return false
        val component = ComponentName(ctx, activityClass)
        return when (ctx.packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> {
                try {
                    ctx.packageManager.getActivityInfo(component, 0).enabled
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            }
        }
    }

    private fun updateGcmSummary() {
        val context = requireContext()
        val pref = findPreference<Preference>(PREF_GCM) ?: return

        if (GcmPrefs.get(context).isEnabled) {
            val database = GcmDatabase(context)
            val regCount = database.registrationList.size
            database.close()
            pref.summary =
                context.getString(org.microg.gms.base.core.R.string.service_status_enabled_short) + " - " + context.resources.getQuantityString(
                    R.plurals.gcm_registered_apps_counter, regCount, regCount
                )
        } else {
            pref.setSummary(org.microg.gms.base.core.R.string.service_status_disabled_short)
        }
    }

    private fun updateCheckinSummary() {
        val summaryRes =
            if (CheckinPreferences.isEnabled(requireContext())) org.microg.gms.base.core.R.string.service_status_enabled_short
            else org.microg.gms.base.core.R.string.service_status_disabled_short
        findPreference<Preference>(PREF_CHECKIN)?.setSummary(summaryRes)
    }


    private fun openGithub() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, PREF_GITHUB_URL.toUri()))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Error opening link: $PREF_GITHUB_URL", e)
        }
    }

    private fun SettingsProvider.Companion.Entry.createPreference(context: Context): Preference? {
        val preference = Preference(context).fillFromEntry(this)
        val categoryKey = when (group) {
            SettingsProvider.Companion.Group.HEADER -> "prefcat_header"
            SettingsProvider.Companion.Group.GOOGLE -> "prefcat_google_services"
            SettingsProvider.Companion.Group.OTHER -> "prefcat_other_services"
            SettingsProvider.Companion.Group.FOOTER -> "prefcat_footer"
        }
        return try {
            findPreference<PreferenceCategory>(categoryKey)?.addPreference(preference)?.let {
                if (it) createdPreferences.add(preference)
                preference
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed adding preference $key", e)
            null
        }
    }

    private fun Preference.fillFromEntry(entry: SettingsProvider.Companion.Entry): Preference {
        key = entry.key
        title = entry.title
        summary = entry.summary
        icon = entry.icon
        isPersistent = false
        isVisible = true
        setOnPreferenceClickListener {
            findNavController().navigate(context, entry.navigationId)
            true
        }
        return this
    }
}