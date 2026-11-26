package com.fivebroz.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One shared HTTP client for the whole app. */
object Net {
    const val SITE = "https://5broz.com"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url)
            headers.forEach { (k, v) -> b.header(k, v) }
            client.newCall(b.build()).execute().use { r ->
                if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
                r.body?.string() ?: ""
            }
        }

    suspend fun send(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        b.method(method, body?.toRequestBody(JSON))
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    suspend fun getObj(url: String) = JSONObject(get(url))
    suspend fun postObj(url: String, body: JSONObject) = JSONObject(send("POST", url, body.toString()))
}

// Small JSON helpers — org.json returns "" / 0 for missing values, we want nulls.
fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null

fun JSONObject.bool(key: String): Boolean = optBoolean(key, false)

inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        f(o)?.let(out::add)
    }
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}
