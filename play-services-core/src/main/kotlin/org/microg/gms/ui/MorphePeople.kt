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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The people behind Morphe: who committed to it and who backs it, both put together by the
 * Morphe API. The same lists Morphe Manager and the patched apps show. The last answer is kept
 * on disk, so the lists show at once, are fetched again only once it has aged, and still show
 * while there is no network.
 */
object MorphePeople {
    private const val TAG = "MorphePeople"
    private const val PEOPLE_URL = "https://api.morphe.software/v2/people"
    private const val CACHE_FILE = "morphe_people.json"

    // The lists change a few times a day at most
    private const val FETCH_INTERVAL_MILLIS = 60 * 60 * 1000L
    private const val TIMEOUT_MILLIS = 15_000

    /**
     * @param contributors Everyone who committed, most first.
     * @param contributors6Months Who committed over the last 6 months.
     * @param contributors3Months Who committed over the last 3 months.
     * @param supportersTotal Every backer, including those not listed.
     */
    data class People(
        val contributors: List<Person>,
        val contributors6Months: List<Person>,
        val contributors3Months: List<Person>,
        val supporters: List<Person>,
        val supportersTotal: Int,
    )

    /**
     * @param avatar Null for someone without a picture, who goes by an initial.
     * @param mega Whether a supporter is a Mega Supporter. Always false for a contributor.
     */
    data class Person(val name: String, val url: String, val avatar: String?, val mega: Boolean)

    @Volatile
    private var loaded: People? = null
    private val mutex = Mutex()

    /** What is already known, from memory or the disk cache, without touching the network. */
    suspend fun cached(context: Context): People? = withContext(Dispatchers.IO) {
        loaded ?: readCache(context)?.also { loaded = it }
    }

    /**
     * Brings the lists up to date when the cached answer has aged.
     *
     * @return The newest lists known, fresh or cached, or null where there are none.
     */
    suspend fun refresh(context: Context): People? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (loaded == null) loaded = readCache(context)
            val file = cacheFile(context)
            if (System.currentTimeMillis() - file.lastModified() >= FETCH_INTERVAL_MILLIS) {
                fetch()?.let { (body, people) ->
                    loaded = people
                    runCatching { file.writeText(body) }.onFailure { Log.w(TAG, "Failed to cache people", it) }
                }
            }
            loaded
        }
    }

    private fun fetch(): Pair<String, People>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(PEOPLE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Failed to fetch people: ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            decode(body)?.let { body to it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch people", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun cacheFile(context: Context) = File(context.cacheDir, CACHE_FILE)

    private fun readCache(context: Context): People? {
        val file = cacheFile(context)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()?.let(::decode)
    }

    private fun decode(body: String): People? = try {
        val json = JSONObject(body)
        People(
            contributors = json.persons("contributors"),
            contributors6Months = json.persons("contributors_6_months"),
            contributors3Months = json.persons("contributors_3_months"),
            supporters = json.persons("supporters"),
            supportersTotal = json.optInt("supporters_total", 0),
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to decode people", e)
        null
    }

    private fun JSONObject.persons(key: String): List<Person> {
        val array: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Person(
                name = name,
                url = item.optString("url"),
                avatar = item.optString("avatar").takeIf { !item.isNull("avatar") && it.isNotBlank() },
                mega = item.optBoolean("mega", false),
            )
        }
    }
}
