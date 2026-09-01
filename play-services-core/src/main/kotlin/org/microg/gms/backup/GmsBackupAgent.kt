/*
 * SPDX-FileCopyrightText: 2026 Murena SAS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.IOException

class GmsBackupAgent : BackupAgent() {

    companion object {
        private const val TAG = "GmsBackupAgent"
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        // Full Backup is used instead.
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        // Full Backup is used instead.
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        Log.d(TAG, "onFullBackup starting")
        createAccountsBackupFile()
        try {
            super.onFullBackup(data)
        } finally {
            deleteAccountsBackupFile()
        }
        Log.d(TAG, "onFullBackup completed")
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor?,
        size: Long,
        destination: File?,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        super.onRestoreFile(data, size, destination, type, mode, mtime)
        if (destination != null && destination.name == AccountBackup.FILE_NAME) {
            Log.d(TAG, "Restoring accounts from file: ${destination.absolutePath}")
            try {
                restoreAccountsFromFile(destination)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security error restoring accounts", e)
            } catch (e: IOException) {
                Log.e(TAG, "IO error restoring accounts", e)
            } finally {
                destination.delete()
            }
        }
    }

    private fun createAccountsBackupFile() {
        val file = File(filesDir, AccountBackup.FILE_NAME)
        try {
            val json = AccountBackup.serializeAccounts(this)
            file.writeText(json)
            Log.d(TAG, "Created accounts backup file")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error creating accounts backup file", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO error creating accounts backup file", e)
        }
    }

    private fun deleteAccountsBackupFile() {
        val file = File(filesDir, AccountBackup.FILE_NAME)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Deleted temporary accounts backup file")
        }
    }

    private fun restoreAccountsFromFile(file: File) {
        if (!file.exists()) return
        val text = file.readText()
        if (text.isEmpty()) return

        AccountBackup.restoreAccounts(this, text)
        deleteAccountsBackupFile()
    }
}
