package com.fivebroz.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

data class Party(
    val id: String,
    val code: String,
    val host: String?,
    val metaId: String?,
    val type: String?,
    val videoId: String?,
    val positionSec: Double,
    val isPlaying: Boolean,
    val updatedAt: Long, // epoch millis
)

data class PartyMessage(val id: String, val sender: String?, val body: String)

/**
 * Watch parties — the same rows the website uses, so phone, TV and browser can
 * share one room. The app syncs by polling (simple and robust on any network).
 */
object Parties {
    private const val URL = "https://tnaswphvdieoifaiudhz.supabase.co/rest/v1"
    private const val KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRuYXN3cGh2ZGllb2lmYWl1ZGh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM1NTc3MTksImV4cCI6MjEwOTEzMzcxOX0.e88g7l60jaYGJXmgavGtKq2zz8gUdr1K7DNP6AEynWs"
    private val headers = mapOf("apikey" to KEY, "Authorization" to "Bearer $KEY")
    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun parse(o: JSONObject): Party? = Party(
        id = o.str("id") ?: return null,
        code = o.str("code") ?: "",
        host = o.str("host"),
        metaId = o.str("meta_id"),
        type = o.str("type"),
        videoId = o.str("video_id"),
        positionSec = o.optDouble("position_sec", 0.0).let { if (it.isNaN()) 0.0 else it },
        isPlaying = o.bool("is_playing"),
        updatedAt = o.str("updated_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
    )

    private fun makeCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun create(host: String, metaId: String, type: String, videoId: String): Party? = try {
        val body = JSONObject()
            .put("code", makeCode())
            .put("host", host)
            .put("meta_id", metaId)
            .put("type", type)
            .put("video_id", videoId)
            .put("position_sec", 0)
            .put("is_playing", true)
        JSONArray(Net.send("POST", "$URL/watch_parties", JSONArray().put(body).toString(), headers + mapOf("Prefer" to "return=representation")))
            .optJSONObject(0)?.let(::parse)
    } catch (_: Exception) {
        null
    }

    suspend fun join(code: String): Party? = try {
        JSONArray(Net.get("$URL/watch_parties?code=eq.${q(code.trim().uppercase())}&limit=1", headers))
            .optJSONObject(0)?.let(::parse)
    } catch (_: Exception) {
        null
    }

    suspend fun get(id: String): Party? = try {
        JSONArray(Net.get("$URL/watch_parties?id=eq.${q(id)}&limit=1", headers)).optJSONObject(0)?.let(::parse)
    } catch (_: Exception) {
        null
    }

    suspend fun beat(id: String, positionSec: Double, playing: Boolean, videoId: String? = null) {
        try {
            val body = JSONObject()
                .put("position_sec", positionSec)
                .put("is_playing", playing)
                .put("updated_at", Instant.now().toString())
            if (videoId != null) body.put("video_id", videoId)
            Net.send("PATCH", "$URL/watch_parties?id=eq.${q(id)}", body.toString(), headers + mapOf("Prefer" to "return=minimal"))
        } catch (_: Exception) {
        }
    }

    suspend fun messages(partyId: String): List<PartyMessage> = try {
        JSONArray(Net.get("$URL/party_messages?select=id,sender,body&party_id=eq.${q(partyId)}&order=created_at.asc&limit=100", headers))
            .mapObjects { o -> PartyMessage(o.str("id") ?: return@mapObjects null, o.str("sender"), o.optString("body")) }
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun send(partyId: String, sender: String, body: String) {
        try {
            Net.send(
                "POST", "$URL/party_messages",
                JSONArray().put(JSONObject().put("party_id", partyId).put("sender", sender).put("body", body)).toString(),
                headers + mapOf("Prefer" to "return=minimal"),
            )
        } catch (_: Exception) {
        }
    }
}
