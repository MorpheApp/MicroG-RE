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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.microg.gms.ui.updater.AppUpdater
import org.microg.tools.ui.ResourceSettingsFragment
import java.util.Locale

/**
 * Options about the app itself: language, launcher icon and update check. These used to sit on
 * the start page; the preference keys and behavior are unchanged.
 */
class AppSettingsFragment : ResourceSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Switch state is controlled exclusively by the launcher alias enabled state.
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            remove(PREF_HIDE_LAUNCHER_ICON)
        }
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.apply {
            isPersistent = false
            isChecked = isLauncherIconHidden()
            setOnPreferenceChangeListener { _, newValue ->
                val shouldHide = newValue as Boolean
                if (resources.getBoolean(R.bool.hide_launcher_icon_available)) {
                    toggleLauncherIconVisibility(hide = shouldHide)
                    true
                } else {
                    if (shouldHide) {
                        showHideLauncherIconConfirmationDialog()
                    }
                    false
                }
            }
        }
        // Saved by the preference itself; the screen is recreated so the new background shows.
        findPreference<SwitchPreferenceCompat>(PureBlack.PREF_KEY)?.setOnPreferenceChangeListener { _, _ ->
            view?.post { activity?.recreate() }
            true
        }
        findPreference<Preference>(PREF_UPDATE)?.setOnPreferenceClickListener {
            AppUpdater.checkManually(requireActivity())
            true
        }
        setupLanguagePreference()
    }

    override fun onResume() {
        super.onResume()
        updateLauncherIconSwitchState()
        updateLanguagePreferenceSummary()
    }

    // Launcher icon

    private fun toggleLauncherIconVisibility(hide: Boolean) {
        val ctx = context ?: return
        LauncherEntries.setIconHidden(ctx, hide)
    }

    private fun updateLauncherIconSwitchState() {
        findPreference<SwitchPreferenceCompat>(PREF_HIDE_LAUNCHER_ICON)?.apply {
            isPersistent = false
            isChecked = isLauncherIconHidden()
        }
    }

    private fun isLauncherIconHidden(): Boolean {
        val ctx = context ?: return false
        return LauncherEntries.isIconHidden(ctx)
    }

    private fun showHideLauncherIconConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.hide_launcher_icon_switch_title)
            .setMessage(R.string.hide_launcher_icon_switch_message)
            .setPositiveButton(R.string.hide_launcher_icon_switch_action) { _, _ ->
                AppUpdater.downloadAndInstallNoIconVariant(requireActivity())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Language

    private fun setupLanguagePreference() {
        val preference = findPreference<ListPreference>(PREF_LANGUAGE) ?: return
        val tags = resources.getStringArray(R.array.pref_language_values)
        val entries = arrayOfNulls<String>(tags.size + 1)
        val values = arrayOfNulls<String>(tags.size + 1)
        entries[0] = getString(R.string.pref_language_system_default)
        values[0] = ""
        for (i in tags.indices) {
            values[i + 1] = tags[i]
            entries[i + 1] = displayName(tags[i])
        }
        preference.entries = entries
        preference.entryValues = values
        preference.setOnPreferenceChangeListener { _, newValue ->
            val tag = newValue as String
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            )
            updateLanguagePreferenceSummary()
            true
        }
        updateLanguagePreferenceSummary()
    }

    // ListPreference asks for its dialog from Preference.onClick(), which runs before any click
    // listener, so the bottom sheet replaces the dialog here.
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference && preference.key == PREF_LANGUAGE) {
            showLanguageSheet(preference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun showLanguageSheet(preference: ListPreference) {
        val values = preference.entryValues?.map { it.toString() } ?: return
        val names = preference.entries?.map { it.toString() } ?: return
        val selected = indexOfLanguage(values, preference.value)
        val uiLocale = resources.configuration.let {
            if (android.os.Build.VERSION.SDK_INT >= 24) it.locales[0] else @Suppress("DEPRECATION") it.locale
        }
        // Second line: the language as named in the current app language, e.g. "Deutsch" over
        // "German". For system default, the language that default currently resolves to.
        val secondary = values.mapIndexed { i, tag ->
            val local = if (tag.isEmpty()) {
                val system = LocaleListCompat.getAdjustedDefault()[0]
                system?.getDisplayName(system)
            } else {
                Locale.forLanguageTag(displayTag(tag)).getDisplayName(uiLocale)
            }
            local?.takeUnless { it.equals(names[i], ignoreCase = true) }
        }

        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_language, null)
        val list = view.findViewById<RecyclerView>(R.id.language_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = LanguageAdapter(names, secondary, selected) { index ->
            sheet.dismiss()
            val value = values[index]
            if (value != preference.value && preference.callChangeListener(value)) {
                preference.value = value
            }
        }
        list.scrollToPosition(selected.coerceAtLeast(0))
        sheet.setContentView(view)
        sheet.behavior.maxHeight = (resources.displayMetrics.heightPixels * 0.8f).toInt()
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
    }

    private class LanguageAdapter(
        private val names: List<String>,
        private val secondary: List<String?>,
        private val selected: Int,
        private val onPick: (Int) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val radio: RadioButton = view.findViewById(R.id.language_radio)
            val name: TextView = view.findViewById(R.id.language_name)
            val local: TextView = view.findViewById(R.id.language_name_local)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false))

        override fun getItemCount() = names.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val isSelected = position == selected
            holder.name.text = names[position]
            holder.local.text = secondary[position]
            holder.local.visibility = if (secondary[position] == null) View.GONE else View.VISIBLE
            holder.radio.isChecked = isSelected
            holder.radio.jumpDrawablesToCurrentState()
            holder.itemView.isSelected = isSelected
            holder.itemView.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) onPick(index)
            }
        }
    }

    /**
     * Row for the current app language. The saved tag may carry a region the list does not
     * (e.g. "en-US" for "en") or use the modern code for a legacy one ("id" for "in"), so an
     * exact match falls back to the language alone. -1 when nothing in the list fits.
     */
    private fun indexOfLanguage(values: List<String>, tag: String?): Int {
        if (tag.isNullOrEmpty()) return 0
        fun normalize(t: String) = displayTag(t.replace('_', '-')).lowercase(Locale.ROOT)
        val wanted = normalize(tag)
        val exact = values.indexOfFirst { it.isNotEmpty() && normalize(it) == wanted }
        if (exact >= 0) return exact
        val language = wanted.substringBefore('-')
        return values.indexOfFirst { it.isNotEmpty() && normalize(it).substringBefore('-') == language }
    }

    private fun updateLanguagePreferenceSummary() {
        val preference = findPreference<ListPreference>(PREF_LANGUAGE) ?: return
        val current = AppCompatDelegate.getApplicationLocales().get(0)
        if (current == null) {
            preference.value = ""
            preference.summary = getString(R.string.pref_language_system_default)
        } else {
            val tag = current.toLanguageTag()
            preference.value = tag
            preference.summary = displayName(tag)
        }
    }

    private fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(displayTag(tag))
        return locale.getDisplayName(locale)
    }

    // "in"/"iw" are legacy ISO codes; use the modern ones for display only.
    private fun displayTag(tag: String) = when {
        tag == "in" || tag.startsWith("in-") -> "id" + tag.substring(2)
        tag == "iw" || tag.startsWith("iw-") -> "he" + tag.substring(2)
        else -> tag
    }

    companion object {
        const val PREF_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"
        const val PREF_UPDATE = "pref_update"
        const val PREF_LANGUAGE = "pref_language"
    }

    init {
        preferencesResource = R.xml.preferences_app_settings
    }
}
