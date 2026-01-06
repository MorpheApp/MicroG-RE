package org.microg.gms.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.microg.gms.auth.AuthConstants
import org.microg.gms.auth.login.LoginActivity
import org.microg.gms.people.DatabaseHelper
import org.microg.gms.people.PeopleManager

class AccountsFragment : PreferenceFragmentCompat() {

    private val tag = AccountsFragment::class.java.simpleName

    private lateinit var fab: ExtendedFloatingActionButton

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_accounts)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground))

        addAccountFab()
        setupPreferenceListeners()
    }

    override fun onStart() {
        super.onStart()
        fab.show()
    }

    override fun onResume() {
        super.onResume()
        refreshAccountSettings()
    }

    override fun onStop() {
        super.onStop()
        fab.hide()
    }

    private fun setupPreferenceListeners() {
        findPreference<Preference>("pref_manage_accounts")?.setOnPreferenceClickListener {
            startActivitySafelyIntent(
                Intent(Settings.ACTION_SYNC_SETTINGS), "Failed to launch sync in device settings"
            )
            true
        }
        findPreference<Preference>("pref_privacy")?.setOnPreferenceClickListener {
            findNavController().navigate(requireContext(), R.id.privacyFragment)
            true
        }
        findPreference<Preference>("pref_manage_history")?.setOnPreferenceClickListener {
            openUrl("https://myactivity.google.com/product/youtube")
            true
        }
        findPreference<Preference>("pref_your_data")?.setOnPreferenceClickListener {
            openUrl("https://myaccount.google.com/yourdata/youtube")
            true
        }
    }

    private fun startActivitySafelyIntent(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(tag, errorMessage, e)
            showSnackbar(errorMessage)
        }
    }

    private fun startActivitySafely(activityClass: Class<*>, errorMessage: String) {
        startActivitySafelyIntent(Intent(requireContext(), activityClass), errorMessage)
    }

    private fun openUrl(url: String) {
        startActivitySafelyIntent(
            Intent(Intent.ACTION_VIEW, url.toUri()), "Failed to open URL: $url"
        )
    }

    private fun addAccountFab() {
        fab = requireActivity().findViewById(R.id.preference_fab)
        fab.text = getString(R.string.auth_add_account)
        fab.setIconResource(R.drawable.ic_add)
        fab.setOnClickListener {
            startActivitySafely(LoginActivity::class.java, "Failed to launch login activity")
        }
    }

    private fun refreshAccountSettings() {
        val context = requireContext()
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE).toList()

        clearAccountPreferences()

        val category = findPreference<PreferenceCategory>("prefcat_current_accounts") ?: return
        category.isVisible = accounts.isNotEmpty()
        if (accounts.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val quickBitmaps: List<Bitmap?> = withContext(Dispatchers.IO) {
                accounts.map { acc -> PeopleManager.getOwnerAvatarBitmap(context, acc.name, false) }
            }

            accounts.forEachIndexed { index, account ->
                val photo = quickBitmaps.getOrNull(index)
                category.addPreference(
                    createAccountPreference(
                        account, photo, index, accounts.size
                    )
                )

                if (photo == null) {
                    loadAndSetFullAvatar(account, category)
                }
            }
        }
    }

    private fun createAccountPreference(
        account: Account, photo: Bitmap?, index: Int, total: Int
    ): Preference {
        return Preference(requireContext()).apply {
            title = getDisplayName(account) ?: account.name
            summary = account.name
            key = "account:${account.name}"
            order = index
            icon = getCircleBitmapDrawable(photo)
            layoutResource = chooseLayoutForPosition(index, total)
            isIconSpaceReserved = photo != null
            setOnPreferenceClickListener {
                showAccountRemovalDialog(account.name)
                true
            }
        }
    }

    private fun loadAndSetFullAvatar(account: Account, category: PreferenceCategory) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp: Bitmap? = withContext(Dispatchers.IO) {
                PeopleManager.getOwnerAvatarBitmap(context, account.name, true)
            }
            bmp?.let {
                category.findPreference<Preference>("account:${account.name}")?.apply {
                    icon = getCircleBitmapDrawable(it)
                    isIconSpaceReserved = true
                }
            }
        }
    }

    private fun chooseLayoutForPosition(index: Int, total: Int): Int {
        return when {
            total <= 1 -> R.layout.preference_material_secondary_single
            total == 2 -> if (index == 0) R.layout.preference_material_secondary_top else R.layout.preference_material_secondary_bottom
            else -> when (index) {
                0 -> R.layout.preference_material_secondary_top
                total - 1 -> R.layout.preference_material_secondary_bottom
                else -> R.layout.preference_material_secondary_middle
            }
        }
    }

    private fun clearAccountPreferences() {
        findPreference<PreferenceCategory>("prefcat_current_accounts")?.removeAll()
    }

    private fun showAccountRemovalDialog(accountName: String) {
        val account = Account(accountName, AuthConstants.DEFAULT_ACCOUNT_TYPE)
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_remove_account, null)

        dialogView.findViewById<MaterialTextView>(R.id.account_name).text =
            getDisplayName(account) ?: accountName
        dialogView.findViewById<MaterialTextView>(R.id.account_email).text = accountName
        dialogView.findViewById<MaterialTextView>(R.id.dialog_title).text =
            getString(R.string.dialog_title_remove_account)
        dialogView.findViewById<MaterialTextView>(R.id.dialog_message).text =
            getString(R.string.dialog_message_remove_account)
        dialogView.findViewById<MaterialButton>(R.id.positive_button).text =
            getString(R.string.dialog_confirm_button)
        dialogView.findViewById<MaterialButton>(R.id.negative_button).text =
            getString(R.string.dialog_cancel_button)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PeopleManager.getOwnerAvatarBitmap(requireContext(), accountName, true)?.let { bmp ->
                val circular =
                    RoundedBitmapDrawableFactory.create(resources, bmp).apply { isCircular = true }
                withContext(Dispatchers.Main) {
                    dialogView.findViewById<ShapeableImageView>(R.id.account_avatar)
                        .setImageDrawable(circular)
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()

        dialogView.findViewById<MaterialButton>(R.id.positive_button).setOnClickListener {
            removeAccount(accountName)
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.negative_button).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun removeAccount(accountName: String) {
        val rootView = view ?: return
        val accountManager = AccountManager.get(requireContext())
        val accountToRemove = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)
            .firstOrNull { it.name == accountName }

        accountToRemove?.let {
            var cancelRemoval = false
            val snackbar = Snackbar.make(
                rootView,
                getString(R.string.snackbar_remove_account, accountName),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.snackbar_undo_button)) { cancelRemoval = true }

            snackbar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (!cancelRemoval) {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            if (accountManager.removeAccountExplicitly(it)) {
                                withContext(Dispatchers.Main) {
                                    refreshAccountSettings()
                                }
                            }
                        }
                    }
                }
            })
            snackbar.show()
        }
    }

    private fun getDisplayName(account: Account): String? {
        val dbHelper = DatabaseHelper(requireContext())
        val cursor = dbHelper.getOwner(account.name)
        return try {
            if (cursor.moveToNext()) {
                val index = cursor.getColumnIndex("display_name")
                if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
            } else null
        } finally {
            cursor.close()
            dbHelper.close()
        }
    }

    private fun getCircleBitmapDrawable(bitmap: Bitmap?) = bitmap?.let {
        RoundedBitmapDrawableFactory.create(resources, it).apply { isCircular = true }
    }

    private fun showSnackbar(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }
}