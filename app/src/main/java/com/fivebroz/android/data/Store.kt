package com.fivebroz.android.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/** Device-local settings: access gate + which profile is watching. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE)
    }

    var gatePassed: Boolean
        get() = sp.getBoolean("gate", false)
        set(v) = sp.edit().putBoolean("gate", v).apply()

    var profileId: String?
        get() = sp.getString("pid", null)
        set(v) = sp.edit().putString("pid", v).apply()

    var profileName: String
        get() = sp.getString("pname", "") ?: ""
        set(v) = sp.edit().putString("pname", v).apply()

    var profileKids: Boolean
        get() = sp.getBoolean("pkids", false)
        set(v) = sp.edit().putBoolean("pkids", v).apply()

    var profileFamily: Boolean
        get() = sp.getBoolean("pfamily", false)
        set(v) = sp.edit().putBoolean("pfamily", v).apply()

    var profileAvatar: String
        get() = sp.getString("pavatar", "") ?: ""
        set(v) = sp.edit().putString("pavatar", v).apply()

    var profileSymbol: String
        get() = sp.getString("psymbol", "") ?: ""
        set(v) = sp.edit().putString("psymbol", v).apply()

    /** The chosen profile, for drawing its avatar. */
    fun current() = Profile(profileId ?: "", profileName, profileAvatar, profileSymbol, profileKids, profileFamily, null)

    fun choose(p: Profile) {
        profileAvatar = p.avatar ?: ""
        profileSymbol = p.symbol ?: ""
        profileId = p.id
        profileName = p.name
        profileKids = p.isKids
        profileFamily = p.family
    }

    fun clearProfile() {
        sp.edit().remove("pid").remove("pname").remove("pkids").remove("pfamily")
            .remove("pavatar").remove("psymbol").apply()
    }

    var seenDebridNotice: Boolean
        get() = sp.getBoolean("seenDebrid", false)
        set(v) = sp.edit().putBoolean("seenDebrid", v).apply()

    // Per-profile player preferences.
    private fun k(name: String) = "${profileId ?: "x"}.$name"
    var subLang: String // "ar", "en" or "off"
        get() = sp.getString(k("subLang"), "ar") ?: "ar"
        set(v) = sp.edit().putString(k("subLang"), v).apply()
    var subScale: Float
        get() = sp.getFloat(k("subScale"), 1.1f)
        set(v) = sp.edit().putFloat(k("subScale"), v).apply()
}

data class Profile(
    val id: String,
    val name: String,
    val avatar: String?, // "linear-gradient(...)" or a data:image URL
    val symbol: String?,
    val isKids: Boolean,
    val family: Boolean,
    val pin: String?,
)

data class Suggestion(
    val id: String,
    val item: Preview,
    val addedBy: String?,
    val votes: Int,
    val votedByMe: Boolean,
)

data class ResumeItem(val item: Preview, val videoId: String, val progress: Float)

/**
 * The household database (Supabase PostgREST). Same tables the website uses,
 * so progress, lists and profiles are shared between web, phone and TV.
 */
object Db {
    private const val URL = "https://tnaswphvdieoifaiudhz.supabase.co/rest/v1"

    // Public anon key (already shipped in the website bundle).
    private const val KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRuYXN3cGh2ZGllb2lmYWl1ZGh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM1NTc3MTksImV4cCI6MjEwOTEzMzcxOX0.e88g7l60jaYGJXmgavGtKq2zz8gUdr1K7DNP6AEynWs"

    private val headers = mapOf("apikey" to KEY, "Authorization" to "Bearer $KEY")

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun select(path: String): JSONArray = JSONArray(Net.get("$URL/$path", headers))

    private suspend fun upsert(table: String, conflict: String, row: JSONObject) {
        Net.send(
            "POST",
            "$URL/$table?on_conflict=$conflict",
            JSONArray().put(row).toString(),
            headers + mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    private val pid: String? get() = Prefs.profileId?.takeIf { it != "guest" }

    suspend fun profiles(): List<Profile> = select(
        "profiles?select=id,name,avatar,symbol,is_kids,family,pin&order=created_at.asc",
    ).mapObjects { o ->
        Profile(
            id = o.str("id") ?: return@mapObjects null,
            name = o.str("name") ?: "",
            avatar = o.str("avatar"),
            symbol = o.str("symbol"),
            isKids = o.bool("is_kids"),
            family = o.bool("family"),
            pin = o.str("pin"),
        )
    }

    suspend fun createProfile(p: Profile): Profile? = try {
        val body = JSONObject()
            .put("name", p.name)
            .put("avatar", p.avatar ?: JSONObject.NULL)
            .put("symbol", p.symbol ?: JSONObject.NULL)
            .put("is_kids", p.isKids)
            .put("family", p.family)
            .put("pin", p.pin ?: JSONObject.NULL)
        val out = JSONArray(
            Net.send("POST", "$URL/profiles", JSONArray().put(body).toString(), headers + mapOf("Prefer" to "return=representation")),
        )
        out.optJSONObject(0)?.str("id")?.let { p.copy(id = it) }
    } catch (_: Exception) {
        null
    }

    suspend fun updateProfile(p: Profile): Boolean = try {
        val body = JSONObject()
            .put("name", p.name)
            .put("avatar", p.avatar ?: JSONObject.NULL)
            .put("symbol", p.symbol ?: JSONObject.NULL)
            .put("is_kids", p.isKids)
            .put("family", p.family)
            .put("pin", p.pin ?: JSONObject.NULL)
        Net.send("PATCH", "$URL/profiles?id=eq.${q(p.id)}", body.toString(), headers + mapOf("Prefer" to "return=minimal"))
        true
    } catch (_: Exception) {
        false
    }

    suspend fun deleteProfile(id: String): Boolean = try {
        Net.send("DELETE", "$URL/profiles?id=eq.${q(id)}", null, headers)
        true
    } catch (_: Exception) {
        false
    }

    // ---------- watch progress ----------

    suspend fun continueWatching(): List<ResumeItem> {
        val p = pid ?: return emptyList()
        return try {
            select(
                "watch_progress?select=meta_id,video_id,type,name,poster,position_sec,duration_sec" +
                    "&profile_id=eq.${q(p)}&finished=eq.false&order=updated_at.desc&limit=20",
            ).mapObjects { r ->
                val metaId = r.str("meta_id") ?: return@mapObjects null
                val pos = r.optInt("position_sec", 0)
                val dur = r.optInt("duration_sec", 0)
                if (pos < 30) return@mapObjects null // skip accidental opens
                ResumeItem(
                    item = Preview(
                        id = metaId,
                        type = if (r.str("type") == "series") "series" else "movie",
                        name = r.str("name") ?: "",
                        poster = r.str("poster"),
                    ),
                    videoId = r.str("video_id") ?: metaId,
                    progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resumePosition(videoId: String): Long {
        val p = pid ?: return 0
        return try {
            val a = select("watch_progress?select=position_sec,finished&profile_id=eq.${q(p)}&video_id=eq.${q(videoId)}")
            val o = a.optJSONObject(0) ?: return 0
            if (o.bool("finished")) 0 else o.optLong("position_sec", 0)
        } catch (_: Exception) {
            0
        }
    }

    suspend fun saveProgress(
        metaId: String,
        videoId: String,
        type: String,
        name: String,
        poster: String?,
        positionSec: Long,
        durationSec: Long,
    ) {
        val p = pid ?: return
        val finished = durationSec > 0 && positionSec >= durationSec * 0.92
        try {
            upsert(
                "watch_progress", "profile_id,video_id",
                JSONObject()
                    .put("profile_id", p)
                    .put("meta_id", metaId)
                    .put("video_id", videoId)
                    .put("type", type)
                    .put("name", name)
                    .put("poster", poster ?: JSONObject.NULL)
                    .put("position_sec", positionSec)
                    .put("duration_sec", if (durationSec > 0) durationSec else JSONObject.NULL)
                    .put("finished", finished)
                    .put("updated_at", Instant.now().toString()),
            )
        } catch (_: Exception) {
        }
    }

    /** Video ids this profile has finished (for episode check marks). */
    suspend fun finishedIds(metaId: String): Set<String> {
        val p = pid ?: return emptySet()
        return try {
            select("watch_progress?select=video_id&profile_id=eq.${q(p)}&meta_id=eq.${q(metaId)}&finished=eq.true")
                .mapObjects { it.str("video_id") }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // ---------- tonight: suggestions + votes (shared with the website) ----------

    suspend fun suggestions(): List<Suggestion> = try {
        val sugs = select("watch_suggestions?select=id,meta_id,type,name,poster,added_by&order=created_at.desc")
        val votes = select("watch_votes?select=suggestion_id,profile_id")
        val me = pid
        val counts = HashMap<String, Int>()
        val mine = HashSet<String>()
        for (i in 0 until votes.length()) {
            val v = votes.optJSONObject(i) ?: continue
            val sid = v.str("suggestion_id") ?: continue
            counts[sid] = (counts[sid] ?: 0) + 1
            if (me != null && v.str("profile_id") == me) mine += sid
        }
        sugs.mapObjects { o ->
            val id = o.str("id") ?: return@mapObjects null
            Suggestion(
                id = id,
                item = Preview(
                    id = o.str("meta_id") ?: return@mapObjects null,
                    type = if (o.str("type") == "series") "series" else "movie",
                    name = o.str("name") ?: "",
                    poster = o.str("poster"),
                ),
                addedBy = o.str("added_by"),
                votes = counts[id] ?: 0,
                votedByMe = id in mine,
            )
        }.sortedByDescending { it.votes }
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun suggest(item: Preview): Boolean = try {
        Net.send(
            "POST", "$URL/watch_suggestions?on_conflict=meta_id",
            JSONArray().put(
                JSONObject().put("meta_id", item.id).put("type", item.type).put("name", item.name)
                    .put("poster", item.poster ?: JSONObject.NULL)
                    .put("added_by", Prefs.profileName.ifBlank { null } ?: JSONObject.NULL),
            ).toString(),
            headers + mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"),
        )
        true
    } catch (_: Exception) {
        false
    }

    suspend fun removeSuggestion(id: String) {
        try {
            Net.send("DELETE", "$URL/watch_suggestions?id=eq.${q(id)}", null, headers)
        } catch (_: Exception) {
        }
    }

    suspend fun toggleVote(suggestionId: String, voted: Boolean) {
        val p = pid ?: return
        try {
            if (voted) {
                Net.send("DELETE", "$URL/watch_votes?suggestion_id=eq.${q(suggestionId)}&profile_id=eq.${q(p)}", null, headers)
            } else {
                Net.send(
                    "POST", "$URL/watch_votes",
                    JSONArray().put(JSONObject().put("suggestion_id", suggestionId).put("profile_id", p)).toString(),
                    headers + mapOf("Prefer" to "return=minimal"),
                )
            }
        } catch (_: Exception) {
        }
    }

    // ---------- ratings: 1 = liked, -1 = not for me ----------

    suspend fun rating(metaId: String): Int {
        val p = pid ?: return 0
        return try {
            select("ratings?select=value&profile_id=eq.${q(p)}&meta_id=eq.${q(metaId)}").optJSONObject(0)?.optInt("value") ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun setRating(metaId: String, value: Int) {
        val p = pid ?: return
        try {
            if (value == 0) Net.send("DELETE", "$URL/ratings?profile_id=eq.${q(p)}&meta_id=eq.${q(metaId)}", null, headers)
            else upsert("ratings", "profile_id,meta_id", JSONObject().put("profile_id", p).put("meta_id", metaId).put("value", value))
        } catch (_: Exception) {
        }
    }

    // ---------- my list ----------

    suspend fun library(): List<Preview> {
        val p = pid ?: return emptyList()
        return try {
            select("library_items?select=meta_id,type,name,poster&profile_id=eq.${q(p)}&order=added_at.desc")
                .mapObjects { r ->
                    Preview(
                        id = r.str("meta_id") ?: return@mapObjects null,
                        type = if (r.str("type") == "series") "series" else "movie",
                        name = r.str("name") ?: "",
                        poster = r.str("poster"),
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun inLibrary(metaId: String): Boolean {
        val p = pid ?: return false
        return try {
            select("library_items?select=id&profile_id=eq.${q(p)}&meta_id=eq.${q(metaId)}").length() > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setInLibrary(item: Preview, saved: Boolean) {
        val p = pid ?: return
        try {
            if (saved) {
                upsert(
                    "library_items", "profile_id,meta_id",
                    JSONObject()
                        .put("profile_id", p)
                        .put("meta_id", item.id)
                        .put("type", item.type)
                        .put("name", item.name)
                        .put("poster", item.poster ?: JSONObject.NULL)
                        .put("added_at", Instant.now().toString()),
                )
            } else {
                Net.send("DELETE", "$URL/library_items?profile_id=eq.${q(p)}&meta_id=eq.${q(item.id)}", null, headers)
            }
        } catch (_: Exception) {
        }
    }
}
