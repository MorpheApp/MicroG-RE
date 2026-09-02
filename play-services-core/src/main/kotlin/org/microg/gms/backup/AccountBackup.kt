/*
 * SPDX-FileCopyrightText: 2026 Murena SAS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.backup

import android.accounts.AccountManager
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import org.microg.gms.auth.AuthConstants

data class AccountBackupData(
    val name: String,
    val type: String,
    val password: String?,
    val userData: Map<String, String>,
    val authTokens: Map<String, String>
)

/**
 * Serialization of microG Google accounts to/from the JSON format shared by the
 * system backup agent (GmsBackupAgent) and the manual export/import feature in
 * the account settings screen.
 */
object AccountBackup {
    const val FILE_NAME = "accounts_backup.json"

    val USER_DATA_KEYS = listOf(
        "flags",
        "services",
        "oauthAccessToken",
        "firstName",
        "lastName",
        "GoogleUserId"
    )

    val AUTH_TOKEN_KEYS = listOf(
        "SID",
        "LSID"
    )

    /** Serializes all accounts of the default account type to JSON. */
    fun serializeAccounts(context: Context): String {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)
        val backups = accounts.map { account ->
            val userData = USER_DATA_KEYS.mapNotNull { key ->
                accountManager.getUserData(account, key)?.let { key to it }
            }.toMap()
            val authTokens = AUTH_TOKEN_KEYS.mapNotNull { key ->
                accountManager.peekAuthToken(account, key)?.let { key to it }
            }.toMap()
            AccountBackupData(account.name, account.type, accountManager.getPassword(account), userData, authTokens)
        }
        return Gson().toJson(backups)
    }

    /** Parses the account backup JSON, or null if it is invalid or empty. */
    fun parseAccounts(json: String): List<AccountBackupData>? {
        if (json.isBlank()) return null
        return try {
            Gson().fromJson(json, object : TypeToken<List<AccountBackupData>>() {}.type)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    /**
     * Restores accounts from the backup JSON. Returns the number of accounts
     * restored, or -1 if the JSON could not be parsed.
     */
    fun restoreAccounts(context: Context, json: String): Int {
        val accountBackups = parseAccounts(json) ?: return -1
        val accountManager = AccountManager.get(context)
        var restored = 0
        for (account in accountBackups) {
            val newAccount = android.accounts.Account(account.name, account.type)
            val exists = accountManager.getAccountsByType(account.type).any { it.name == account.name }
            if (exists) continue
            if (!accountManager.addAccountExplicitly(newAccount, account.password, null)) continue
            for ((key, value) in account.userData) {
                accountManager.setUserData(newAccount, key, value)
            }
            for ((key, value) in account.authTokens) {
                accountManager.setAuthToken(newAccount, key, value)
            }
            restored++
        }
        return restored
    }
}
