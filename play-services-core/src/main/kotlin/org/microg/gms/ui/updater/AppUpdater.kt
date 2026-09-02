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

package org.microg.gms.ui.updater

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.BuildConfig
import com.google.android.gms.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.microg.gms.ui.MainSettingsActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

/**
 * In-app updater for MicroG-RE.
 *
 * Checks GitHub for the latest release, prompts the user on launch (or via a notification
 * when running in the background), and can download and install the new APK through the
 * system package installer.
 */
object AppUpdater {

    /** Intent extra used by the update notification to force the update prompt. */
    const val EXTRA_CHECK_UPDATE = "extra_check_update"

    private const val RELEASES_URL = "https://api.github.com/repos/MorpheApp/MicroG-RE/releases"
    private const val UPDATE_URL = "$RELEASES_URL/latest"
    private const val PREFS_NAME = "org.microg.gms_updater"
    private const val PREFS_IGNORED_VERSION = "ignored_version"
    private const val PREFS_LAST_CHECK = "last_check"

    /** Settings switch key (default SharedPreferences): include pre-release / dev builds. */
    const val PREFS_INCLUDE_PRERELEASE = "pref_include_prerelease"
    private const val NOTIFICATION_CHANNEL = "updates"
    private const val NOTIFICATION_ID = 0x5555
    private const val MAX_NOTES_LENGTH = 8000
    private const val DOWNLOAD_DIR = "updater"
    private const val DOWNLOAD_FILE = "microg-re-update.apk"
    private const val USER_AGENT = "MicroG-RE-Updater"
    private const val TIMEOUT_MS = 15000
    private const val TAG = "AppUpdater"

    /** Automatic checks run at most once every [UPDATE_COOLDOWN_MS]; manual checks always run. */
    private const val UPDATE_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    class UpdateInfo {
        var tagName: String = ""
        var version: String = ""
        var releaseNotes: String? = null
        var assetName: String = ""
        var assetUrl: String = ""
        var assetSize: Long = 0
        var prerelease: Boolean = false
    }

    // -----------------------------------------------------------------------------------
    // Public API (Java-friendly)
    // -----------------------------------------------------------------------------------

    /** Called when the settings activity launches. Checks for updates and prompts if needed. */
    @JvmStatic
    fun checkOnLaunch(activity: Activity) {
        val intent = activity.intent
        val force = intent?.getBooleanExtra(EXTRA_CHECK_UPDATE, false) == true
        intent?.removeExtra(EXTRA_CHECK_UPDATE)
        if (force) {
            check(activity, force = true)
            return
        }
        if (System.currentTimeMillis() - prefs(activity).getLong(PREFS_LAST_CHECK, 0) < UPDATE_COOLDOWN_MS) {
            return
        }
        check(activity, force = false)
    }

    /** Manual "Check for updates" action: always runs and reports the result. */
    @JvmStatic
    fun checkManually(activity: Activity) {
        check(activity, force = true, manual = true)
    }

    /** Downloads [update] and hands the APK to the system installer. */
    @JvmStatic
    fun runUpdateFlow(activity: Activity, update: UpdateInfo?) {
        val url = update?.assetUrl ?: run {
            toast(activity, R.string.update_download_failed)
            return
        }
        requestNotificationPermissionIfNeeded(activity)

        var isCancelled = false
        var activeConnection: HttpURLConnection? = null

        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            val padding = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_downloading_title)
            .setCancelable(false)
            .setView(progress)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                isCancelled = true
                Thread {
                    try { activeConnection?.disconnect() } catch (_: Exception) {}
                }.start()
            }
            .create()
        dialog.show()

        Thread {
            val file = downloadUrlCancellable(url, File(activity.cacheDir, "$DOWNLOAD_DIR/$DOWNLOAD_FILE"), { isCancelled }) { conn ->
                activeConnection = conn
            }
            runOnUiThread(activity) {
                dialog.dismiss()
                if (isCancelled) return@runOnUiThread
                if (file == null) {
                    Log.e(TAG, "Update download failed: $url")
                    showErrorDialog(activity, activity.getString(R.string.update_download_failed))
                } else {
                    Log.i(TAG, "Update downloaded: ${file.length()} bytes -> ${file.absolutePath}")
                    launchInstaller(activity, file)
                }
            }
        }.start()
    }

    /** Downloads the 'noicon' build variant and prompts the user to update the app. */
    @JvmStatic
    fun downloadAndInstallNoIconVariant(activity: Activity) {
        requestNotificationPermissionIfNeeded(activity)

        var isCancelled = false
        var activeConnection: HttpURLConnection? = null

        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            val padding = (16 * activity.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.hide_launcher_icon_updating_title)
            .setView(progress)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                isCancelled = true
                Thread {
                    try { activeConnection?.disconnect() } catch (_: Exception) {}
                }.start()
            }
            .setOnCancelListener {
                isCancelled = true
                Thread {
                    try { activeConnection?.disconnect() } catch (_: Exception) {}
                }.start()
            }
            .create()
        dialog.show()

        Thread {
            val candidateUrls = mutableListOf<String>()

            // 1. Query GitHub API for release assets
            val update = fetchLatestUpdate(
                noiconVariant = true,
                includePrerelease = includePrerelease(activity),
                strictVariantMatch = true
            )
            if (isCancelled) return@Thread

            if (update?.assetUrl?.isNotEmpty() == true && update.assetName.lowercase().contains("noicon")) {
                candidateUrls.add(update.assetUrl)
            }

            // 2. Direct GitHub release asset URL fallback
            val cleanVersion = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")
            val tag = "v$cleanVersion"
            val isArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
            val assetFileName = if (isArm64) "microg-$cleanVersion-noicon-arm64-v8a.apk" else "microg-$cleanVersion-noicon.apk"

            val directUrlTag = "https://github.com/MorpheApp/MicroG-RE/releases/download/$tag/$assetFileName"
            val directUrlLatest = "https://github.com/MorpheApp/MicroG-RE/releases/latest/download/$assetFileName"

            if (!candidateUrls.contains(directUrlTag)) candidateUrls.add(directUrlTag)
            if (!candidateUrls.contains(directUrlLatest)) candidateUrls.add(directUrlLatest)

            val targetFile = File(activity.cacheDir, "$DOWNLOAD_DIR/microg-re-noicon-update.apk")
            var downloadedFile: File? = null

            for (url in candidateUrls) {
                if (isCancelled) break
                Log.i(TAG, "Attempting to download no-icon variant from: $url")
                val file = downloadUrlCancellable(url, targetFile, { isCancelled }) { conn ->
                    activeConnection = conn
                }
                if (file != null && file.length() > 0L) {
                    downloadedFile = file
                    break
                }
            }

            runOnUiThread(activity) {
                dialog.dismiss()
                if (isCancelled) return@runOnUiThread
                if (downloadedFile == null) {
                    Log.e(TAG, "No-icon variant download failed for all candidate URLs")
                    showErrorDialog(activity, activity.getString(R.string.update_download_failed))
                } else {
                    Log.i(TAG, "No-icon variant downloaded: ${downloadedFile.length()} bytes -> ${downloadedFile.absolutePath}")
                    launchInstaller(activity, downloadedFile)
                }
            }
        }.start()
    }

    /** Posts a notification that an update is available (used by the background worker). */
    @JvmStatic
    fun postUpdateNotification(context: Context, update: UpdateInfo) {
        ensureNotificationChannel(context)
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainSettingsActivity::class.java)
                .putExtra(EXTRA_CHECK_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, update.version))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    /** Dismisses the "update available" notification (e.g. when the user ignores the update). */
    @JvmStatic
    fun dismissUpdateNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** True when [update] is newer than the installed version. */
    @JvmStatic
    fun isNewerThanInstalled(update: UpdateInfo): Boolean {
        return compareVersions(update.version, BuildConfig.VERSION_NAME) > 0
    }

    // -----------------------------------------------------------------------------------
    // Core logic
    // -----------------------------------------------------------------------------------

    private fun check(activity: Activity, force: Boolean, manual: Boolean = false) {
        if (!force) {
            prefs(activity).edit().putLong(PREFS_LAST_CHECK, System.currentTimeMillis()).apply()
        }
        Thread {
            val update = fetchLatestUpdate(
                activity.resources.getBoolean(R.bool.hide_launcher_icon_available),
                includePrerelease(activity)
            )
            runOnUiThread(activity) {
                if (update == null) {
                    if (manual) toast(activity, R.string.update_check_failed)
                    return@runOnUiThread
                }
                if (!isNewerThanInstalled(update)) {
                    if (manual) showUpToDateDialog(activity, update)
                    return@runOnUiThread
                }
                if (!force) {
                    val ignored = prefs(activity).getString(PREFS_IGNORED_VERSION, null)
                    if (ignored != null && compareVersions(update.version, ignored) <= 0) {
                        return@runOnUiThread
                    }
                }
                showUpdateDialog(activity, update)
            }
        }.start()
    }

    private fun showUpdateDialog(activity: Activity, update: UpdateInfo) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        var current = update
        val titleView = view.findViewById<android.widget.TextView>(R.id.update_title)
        val messageView = view.findViewById<android.widget.TextView>(R.id.update_message)
        val notesView = view.findViewById<android.widget.TextView>(R.id.update_notes)
        val notesHeader = view.findViewById<android.widget.TextView>(R.id.update_notes_header)
        val notesScroll = view.findViewById<android.widget.ScrollView>(R.id.update_notes_scroll)

        fun refreshDialog(u: UpdateInfo) {
            titleView.setText(
                if (u.prerelease) R.string.update_available_prerelease_title
                else R.string.update_available_title
            )
            messageView.text =
                activity.getString(R.string.update_available_message, u.version, BuildConfig.VERSION_NAME)
            val notes = u.releaseNotes?.let { renderReleaseNotes(it) }
            if (!notes.isNullOrBlank()) {
                notesView.text = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT)
                notesView.movementMethod = LinkMovementMethod.getInstance()
                notesView.visibility = android.view.View.VISIBLE
                notesHeader.visibility = android.view.View.VISIBLE
                notesScroll.visibility = android.view.View.VISIBLE
                // Cap the notes area after layout so long changelogs scroll instead of pushing
                // the action buttons off the bottom of the dialog.
                notesScroll.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                notesScroll.post {
                    val capPx = (280 * activity.resources.displayMetrics.density).toInt()
                    if (notesView.height > capPx) {
                        notesScroll.layoutParams.height = capPx
                        notesScroll.requestLayout()
                    }
                }
            } else {
                notesView.visibility = android.view.View.GONE
                notesHeader.visibility = android.view.View.GONE
                notesScroll.visibility = android.view.View.GONE
            }
        }
        refreshDialog(current)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .create()
        view.findViewById<android.widget.Button>(R.id.update_cancel)
            .setOnClickListener { dialog.dismiss() }
        view.findViewById<android.widget.Button>(R.id.update_ignore)
            .setOnClickListener {
                prefs(activity).edit().putString(PREFS_IGNORED_VERSION, current.version).apply()
                dismissUpdateNotification(activity)
                dialog.dismiss()
            }
        view.findViewById<android.widget.Button>(R.id.update_now)
            .setOnClickListener {
                dialog.dismiss()
                runUpdateFlow(activity, current)
            }

        // Pre-release / dev channel toggle: switching it re-checks with the new channel and
        // swaps the offer in place if a newer build is available there.
        val switch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.update_prerelease_switch)
        switch.isChecked = includePrerelease(activity)
        switch.setOnCheckedChangeListener { _, checked ->
            setPrereleasePref(activity, checked)
            Thread {
                val candidate = fetchLatestUpdate(
                    activity.resources.getBoolean(R.bool.hide_launcher_icon_available),
                    checked
                )
                runOnUiThread(activity) {
                    if (candidate == null || !isNewerThanInstalled(candidate)) {
                        // Nothing newer on the newly selected channel. If the user turned the
                        // toggle off, the pre-release offer no longer applies: dismiss it.
                        if (!checked) dialog.dismiss()
                        return@runOnUiThread
                    }
                    current = candidate
                    refreshDialog(current)
                }
            }.start()
        }
        dialog.show()
    }

    /**
     * Renders GitHub-style markdown release notes into HTML for the dialog via a real
     * CommonMark parser, turning headings, lists, code blocks, emphasis and links into
     * clickable [android.text.style.URLSpan]s through [HtmlCompat]. Instead of the old
     * hand-rolled regex "sanitizer" that mangled bullets and code blocks.
     */
    private fun renderReleaseNotes(raw: String): String {
        var s = raw.trim()
        if (s.length > MAX_NOTES_LENGTH) s = s.take(MAX_NOTES_LENGTH) + "\n\u2026"
        // Escape HTML first so raw markup in the notes is never interpreted.
        s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val parser = org.commonmark.parser.Parser.builder().build()
        val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()
        var html = renderer.render(parser.parse(s))
        // Bare URLs (not inside markdown links) aren't auto-linked by plain CommonMark,
        // so wrap the remaining ones that aren't already inside an href attribute.
        html = html.replace(
            Regex("""(?<!["=])(https?://[^\s<>"]+)""", RegexOption.IGNORE_CASE),
            """<a href="$1">$1</a>"""
        )
        // Render inline code and code blocks in monospace (Html maps <font face> to a span).
        html = html.replace(
            Regex("""<code[^>]*>""", RegexOption.IGNORE_CASE),
            """<font face="monospace">"""
        ).replace("</code>", "</font>")
        // Android's Html only draws list bullets in the margin right next to the text with
        // no gap, so turn <li> into explicit "• " text with a line break instead.
        html = html.replace("<li>", "\u2022 ")
            .replace("</li>", "<br>")
            .replace("<ul>", "")
            .replace("</ul>", "")
            .replace("<ol>", "")
            .replace("</ol>", "")
        return html
    }

    private fun showUpToDateDialog(activity: Activity, update: UpdateInfo) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_up_to_date_title)
            .setMessage(activity.getString(R.string.update_up_to_date_message, update.version))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showErrorDialog(context: Context, message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_failed_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun launchInstaller(context: Context, file: File) {
        if (!checkSignaturesMatch(context, file)) {
            Log.e(TAG, "Signature mismatch between installed app and downloaded APK")
            showErrorDialog(context, context.getString(R.string.update_signature_mismatch))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            Log.w(TAG, "canRequestPackageInstalls is false, prompting user to grant permission")
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_permission_required_title)
                .setMessage(R.string.update_permission_required_message)
                .setPositiveButton(R.string.update_open_settings) { _, _ ->
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.i(TAG, "Launching installer for $uri")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Installer launch failed", e)
            showErrorDialog(context, context.getString(R.string.update_download_failed))
        }
    }

    private fun checkSignaturesMatch(context: Context, apkFile: File): Boolean {
        val pm = context.packageManager
        val installedSignatures = getSignatures(pm, context.packageName, null)
        val apkSignatures = getSignatures(pm, null, apkFile.absolutePath)

        if (installedSignatures.isEmpty() || apkSignatures.isEmpty()) {
            return true
        }

        return apkSignatures.any { apkSig ->
            installedSignatures.any { installedSig ->
                apkSig.toByteArray().contentEquals(installedSig.toByteArray())
            }
        }
    }

    private fun getSignatures(pm: PackageManager, packageName: String?, apkPath: String?): List<android.content.pm.Signature> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val pkgInfo = if (apkPath != null) {
                pm.getPackageArchiveInfo(apkPath, flags)
            } else if (packageName != null) {
                pm.getPackageInfo(packageName, flags)
            } else null
            val signingInfo = pkgInfo?.signingInfo
            return when {
                signingInfo == null -> emptyList()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.toList()
                else -> signingInfo.signingCertificateHistory.toList()
            }
        } else {
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_SIGNATURES
            val pkgInfo = if (apkPath != null) {
                pm.getPackageArchiveInfo(apkPath, flags)
            } else if (packageName != null) {
                pm.getPackageInfo(packageName, flags)
            } else null
            @Suppress("DEPRECATION")
            return pkgInfo?.signatures?.toList() ?: emptyList()
        }
    }

    private fun downloadUrlCancellable(
        initialUrl: String,
        target: File,
        isCancelled: () -> Boolean,
        onConnectionCreated: (HttpURLConnection) -> Unit
    ): File? {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 5

        while (redirects < maxRedirects) {
            if (isCancelled()) return null
            try {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                onConnectionCreated(conn)
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", USER_AGENT)

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308
                ) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc.isNullOrEmpty()) return null
                    currentUrl = loc
                    redirects++
                    continue
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return null
                }

                target.parentFile?.mkdirs()
                val input = conn.inputStream
                try {
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled()) {
                                conn.disconnect()
                                target.delete()
                                return null
                            }
                            out.write(buffer, 0, bytesRead)
                        }
                    }
                } finally {
                    try { input.close() } catch (_: Exception) {}
                    conn.disconnect()
                }

                return if (target.length() > 0L && !isCancelled()) target else null
            } catch (e: Exception) {
                target.delete()
                return null
            }
        }
        return null
    }

    /**
     * Fetches the newest releasable update from GitHub. Returns null on failure.
     *
     * With [includePrerelease] the whole release list is scanned (so pre-release / dev
     * builds are offered); otherwise only the latest stable release is considered.
     */
    @JvmStatic
    @JvmOverloads
    fun fetchLatestUpdate(
        noiconVariant: Boolean,
        includePrerelease: Boolean,
        strictVariantMatch: Boolean = false
    ): UpdateInfo? {
        return try {
            if (includePrerelease || strictVariantMatch) {
                val text = httpGet("$RELEASES_URL?per_page=20") ?: return null
                val all = Gson().fromJson(text, JsonArray::class.java) ?: return null
                all.asSequence().mapNotNull { el ->
                    el.asJsonObject?.let { parseReleaseObject(it, noiconVariant, strictVariantMatch) }
                }.maxWithOrNull { a, b ->
                    compareVersions(a.version, b.version)
                }
            } else {
                val text = httpGet(UPDATE_URL) ?: return null
                parseReleaseObject(
                    Gson().fromJson(text, JsonObject::class.java) ?: return null,
                    noiconVariant,
                    strictVariantMatch
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseReleaseObject(
        root: JsonObject,
        noiconVariant: Boolean,
        strictVariantMatch: Boolean = false
    ): UpdateInfo? {
        return try {
            val tag = root.get("tag_name")?.asString ?: return null
            val assets = root.getAsJsonArray("assets") ?: return null
            val asset = pickAsset(assets, noiconVariant, strictVariantMatch) ?: return null
            UpdateInfo().apply {
                tagName = tag
                version = normalizeVersion(tag)
                releaseNotes = root.get("body")?.asString
                assetName = asset.get("name")?.asString ?: ""
                assetUrl = asset.get("browser_download_url")?.asString ?: ""
                assetSize = asset.get("size")?.asLong ?: 0
                prerelease = root.get("prerelease")?.asBoolean ?: false
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Picks the APK matching the installed variant, so an update installs as an upgrade of
     * the same build: same ABI (arm64-v8a on 64-bit devices, universal otherwise) and the
     * same launcher-icon variant (normal vs -noicon).
     */
    private fun pickAsset(
        assets: JsonArray,
        noiconVariant: Boolean,
        strictVariantMatch: Boolean = false
    ): JsonObject? {
        val apks = assets.mapNotNull { it.asJsonObject }.filter { obj ->
            obj.get("name")?.asString?.lowercase()?.endsWith(".apk") == true
        }
        if (apks.isEmpty()) return null
        val arm64Preferred = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        fun nameOf(asset: JsonObject) = asset.get("name")?.asString?.lowercase() ?: ""
        val sameAbi = apks.filter {
            nameOf(it).contains("arm64-v8a") == arm64Preferred
        }
        val exact = sameAbi.firstOrNull { nameOf(it).contains("noicon") == noiconVariant }
        if (strictVariantMatch) {
            val anyAbiMatch = apks.firstOrNull { nameOf(it).contains("noicon") == noiconVariant }
            return exact ?: anyAbiMatch
        }
        val variantFallback = sameAbi.firstOrNull { nameOf(it).contains("noicon") != noiconVariant }
        val anyAbi = apks.firstOrNull { nameOf(it).contains("noicon") == noiconVariant }
        return exact ?: variantFallback ?: anyAbi
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            text
        } catch (e: Exception) {
            null
        }
    }

    // -----------------------------------------------------------------------------------
    // Version comparison
    // -----------------------------------------------------------------------------------

    private fun normalizeVersion(v: String): String = v.trim().removePrefix("v").removePrefix("V")

    /** Parses a version into (core numbers, pre-release identifiers) per semver. */
    private fun semverParts(v: String): Pair<List<Int>, List<String>> {
        val main = normalizeVersion(v).substringBefore("+") // drop build metadata
        val split = main.split("-", limit = 2)
        val core = split[0].split(".").mapNotNull { it.toIntOrNull() }
        val prerelease = if (split.size > 1) split[1].split(".") else emptyList()
        return Pair(core, prerelease)
    }

    /**
     * Compares two version strings semver-style ("v7.0.0" vs "7.0.0-dev.7" vs "7.0.1").
     * A pre-release of the same base version ranks below the release. Returns <0, 0 or >0.
     */
    @JvmStatic
    fun compareVersions(a: String, b: String): Int {
        val (ca, pa) = semverParts(a)
        val (cb, pb) = semverParts(b)
        for (i in 0 until maxOf(ca.size, cb.size)) {
            val x = ca.getOrElse(i) { 0 }
            val y = cb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        // Same core: a release beats its own pre-releases.
        if (pa.isEmpty() && pb.isNotEmpty()) return 1
        if (pa.isNotEmpty() && pb.isEmpty()) return -1
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { null } ?: return -1
            val y = pb.getOrElse(i) { null } ?: return 1
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val c = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1 // numeric identifiers sort below alphanumeric
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return 0
    }

    // -----------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when the "include pre-release builds" settings toggle is enabled. */
    private fun includePrerelease(context: Context): Boolean =
        prefsDefault(context).getBoolean(PREFS_INCLUDE_PRERELEASE, false)

    private fun setPrereleasePref(context: Context, value: Boolean) {
        prefsDefault(context).edit().putBoolean(PREFS_INCLUDE_PRERELEASE, value).apply()
    }

    private fun prefsDefault(context: Context): SharedPreferences =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

    private fun runOnUiThread(activity: Activity, block: () -> Unit) {
        Handler(Looper.getMainLooper()).post { block() }
    }

    private fun toast(context: Context, resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0x5555)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                context.getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }
}

