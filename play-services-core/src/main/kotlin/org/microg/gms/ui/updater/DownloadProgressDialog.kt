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

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.Formatter
import android.view.View
import android.widget.TextView
import com.google.android.gms.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.NumberFormat

/**
 * Download progress for the updater, shown as a modal bottom sheet like the update sheet.
 * It cannot be dragged or tapped away; the download stops only through its Cancel button.
 *
 * Shows an indeterminate bar while connecting, then switches to a determinate bar with
 * "downloaded of total", the percentage, the current speed and the time left. [onProgress]
 * is called from the download thread; it samples the transfer rate (smoothed, so the speed
 * and time left do not jump around) and posts at most a few UI updates per second.
 *
 * @param expectedSize size reported by the release metadata, used when the server sends no
 *   Content-Length. 0 when unknown.
 */
internal class DownloadProgressDialog(
    private val activity: Activity,
    titleRes: Int,
    versionLabel: String?,
    private val expectedSize: Long,
    onCancel: () -> Unit,
) {
    private val view: View = activity.layoutInflater.inflate(R.layout.dialog_update_progress, null)
    private val bar: LinearProgressIndicator = view.findViewById(R.id.update_progress_bar)
    private val sizeText: TextView = view.findViewById(R.id.update_progress_size)
    private val percentText: TextView = view.findViewById(R.id.update_progress_percent)
    private val speedText: TextView = view.findViewById(R.id.update_progress_speed)
    private val handler = Handler(Looper.getMainLooper())
    private val percentFormat: NumberFormat = NumberFormat.getPercentInstance()

    // Touched only by the download thread.
    private var lastUiUpdate = 0L
    private var sampleTime = 0L
    private var sampleBytes = 0L
    private var smoothedRate = -1.0

    private val dialog = BottomSheetDialog(activity)

    init {
        view.findViewById<TextView>(R.id.update_progress_title).setText(titleRes)
        if (!versionLabel.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.update_progress_version).apply {
                text = versionLabel
                visibility = View.VISIBLE
            }
        }
        view.findViewById<View>(R.id.update_progress_cancel).setOnClickListener {
            onCancel()
            dismiss()
        }
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.isDraggable = false
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        if (dialog.isShowing && !activity.isFinishing) dialog.dismiss()
    }

    /** Download thread callback. [reportedTotal] is the Content-Length, or -1 if unknown. */
    fun onProgress(downloaded: Long, reportedTotal: Long) {
        val now = SystemClock.elapsedRealtime()
        if (sampleTime == 0L) {
            sampleTime = now
            sampleBytes = downloaded
        } else if (now - sampleTime >= RATE_SAMPLE_MS) {
            val instant = (downloaded - sampleBytes) * 1000.0 / (now - sampleTime)
            smoothedRate = if (smoothedRate < 0) instant else smoothedRate * 0.7 + instant * 0.3
            sampleTime = now
            sampleBytes = downloaded
        }
        val total = if (reportedTotal > 0) reportedTotal else expectedSize
        val finished = total in 1..downloaded
        if (!finished && now - lastUiUpdate < UI_UPDATE_MS) return
        lastUiUpdate = now
        val rate = smoothedRate
        handler.post { render(downloaded, total, rate) }
    }

    private fun render(downloaded: Long, total: Long, rate: Double) {
        if (!dialog.isShowing) return
        val downloadedText = Formatter.formatShortFileSize(activity, downloaded)
        if (total > 0) {
            val fraction = (downloaded.toDouble() / total).coerceIn(0.0, 1.0)
            // Switches the bar from indeterminate to determinate after its current cycle.
            bar.setProgressCompat((fraction * bar.max).toInt(), true)
            sizeText.text = activity.getString(
                R.string.update_download_progress,
                downloadedText,
                Formatter.formatShortFileSize(activity, total)
            )
            percentText.text = percentFormat.format(fraction)
            percentText.visibility = View.VISIBLE
        } else {
            sizeText.text = downloadedText
            percentText.visibility = View.GONE
        }
        // The speed line only appears once there is a rate to show, so the dialog does not
        // carry an empty row while connecting.
        val speed = if (rate > 0) Formatter.formatShortFileSize(activity, rate.toLong()) else null
        speedText.text = when {
            speed == null -> ""
            total > downloaded -> activity.getString(
                R.string.update_download_speed_eta,
                speed,
                timeLeft(((total - downloaded) / rate).toLong())
            )
            else -> activity.getString(R.string.update_download_speed, speed)
        }
        speedText.visibility = if (speed == null) View.GONE else View.VISIBLE
    }

    /** "5 seconds left" below a minute, otherwise rounded up to whole minutes. */
    private fun timeLeft(seconds: Long): String {
        val res = activity.resources
        return if (seconds < 60) {
            val s = seconds.coerceAtLeast(1).toInt()
            res.getQuantityString(R.plurals.update_download_seconds_left, s, s)
        } else {
            val m = ((seconds + 59) / 60).toInt()
            res.getQuantityString(R.plurals.update_download_minutes_left, m, m)
        }
    }

    private companion object {
        const val RATE_SAMPLE_MS = 500L
        const val UI_UPDATE_MS = 200L
    }
}
