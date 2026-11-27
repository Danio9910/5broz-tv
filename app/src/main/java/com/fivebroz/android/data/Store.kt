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
    private var dir: java.io.File? = null

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE)
        dir = ctx.applicationContext.filesDir
    }

    val testAddon: String? get() = sp.getString("test_addon", null)
    // Debug-only switches the e2e runs use to photograph seasonal / evening moments.
    private val dbg get() = com.fivebroz.android.BuildConfig.DEBUG
    val forcedSeason: String? get() = if (dbg) sp.getString("force_season", null) else null
    val forceMovieNight: Boolean get() = dbg && sp.getBoolean("force_night", false)
    val forceRecap: Boolean get() = dbg && sp.getBoolean("force_recap", false)

    fun cacheFile(name: String): java.io.File? = dir?.let { java.io.File(it, name) }

    /** This profile's recent searches, newest first. */
    var recentSearches: List<String>
        get() = (sp.getString(k("recent"), "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString(k("recent"), v.take(8).joinToString("\n")).apply()

    fun rememberSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentSearches = listOf(t) + recentSearches.filter { !it.equals(t, ignoreCase = true) }
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
        com.fivebroz.android.ui.Look.load() // each profile has its own colours
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

    /** Subtitle delay chosen for one video (Nuvio keeps it per profile and per video). */
    fun subDelay(videoId: String): Float = sp.getFloat(k("delay.$videoId"), 0f)
    fun setSubDelay(videoId: String, v: Float) = sp.edit().putFloat(k("delay.$videoId"), v).apply()

    /** Subtitle look: text colour, a dark box behind it, height above the bottom (0–0.35). */
    var subColor: String
        get() = sp.getString(k("subColor"), "white") ?: "white"
        set(v) = sp.edit().putString(k("subColor"), v).apply()
    var subBox: Boolean
        get() = sp.getBoolean(k("subBox"), false)
        set(v) = sp.edit().putBoolean(k("subBox"), v).apply()
    var subPos: Float
        get() = sp.getFloat(k("subPos"), 0.08f)
        set(v) = sp.edit().putFloat(k("subPos"), v).apply()
    /** TV: switch the screen to the film's frame rate (smoother motion). */
    var matchFrameRate: Boolean
        get() = sp.getBoolean("matchFps", true)
        set(v) = sp.edit().putBoolean("matchFps", v).apply()
    var nightMode: Boolean
        get() = sp.getBoolean(k("night"), false)
        set(v) = sp.edit().putBoolean(k("night"), v).apply()

    /** One-time hints ("hold for 2×"…) shown once per device. */
    fun tipSeen(key: String) = sp.getBoolean("tip.$key", false)
    fun setTipSeen(key: String) = sp.edit().putBoolean("tip.$key", true).apply()

    // ---- look & behaviour (Settings) ----
    /** -1 = the profile's default (gold; warm amber for kids). */
    var accent: Int
        get() = sp.getInt(k("accent"), -1)
        set(v) = sp.edit().putInt(k("accent"), v).apply()
    var amoled: Boolean
        get() = sp.getBoolean(k("amoled"), false)
        set(v) = sp.edit().putBoolean(k("amoled"), v).apply()
    /** Home cards: "poster" (tall) or "wide" (landscape). */
    var cardStyle: String
        get() = sp.getString(k("cards"), "poster") ?: "poster"
        set(v) = sp.edit().putString(k("cards"), v).apply()
    var hiddenRows: Set<String>
        get() = sp.getStringSet(k("hiddenRows"), emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(k("hiddenRows"), v).apply()
    /** Home row titles in the order the person chose (others keep their place after). */
    var rowOrder: List<String>
        get() = (sp.getString(k("rowOrder"), "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString(k("rowOrder"), v.joinToString("\n")).apply()
    /** "auto" = fastest source starts by itself; "manual" = always show the list. */
    var streamMode: String
        get() = sp.getString(k("streamMode"), "auto") ?: "auto"
        set(v) = sp.edit().putString(k("streamMode"), v).apply()
    /** Try the source that worked last time for this video first. */
    fun lastSource(videoId: String): String? = sp.getString("last.$videoId", null)
    fun setLastSource(videoId: String, key: String) = sp.edit().putString("last.$videoId", key).apply()
    var trailerBackground: Boolean
        get() = sp.getBoolean("trailerBg", false)
        set(v) = sp.edit().putBoolean("trailerBg", v).apply()
    var heroTrailer: Boolean
        get() = sp.getBoolean("heroTrailer", true)
        set(v) = sp.edit().putBoolean("heroTrailer", v).apply()
    var screensaver: Boolean
        get() = sp.getBoolean("saver", true)
        set(v) = sp.edit().putBoolean("saver", v).apply()

    /** Blur pictures of episodes not watched yet (no spoilers). */
    var spoilerBlur: Boolean
        get() = sp.getBoolean(k("spoiler"), false)
        set(v) = sp.edit().putBoolean(k("spoiler"), v).apply()

    fun forgetSearch(q: String) {
        recentSearches = recentSearches.filter { it != q }
    }

    /** Show English under the Arabic line. */
    var subSecond: Boolean
        get() = sp.getBoolean(k("subSecond"), false)
        set(v) = sp.edit().putBoolean(k("subSecond"), v).apply()
    /** Audio language picked for a series, kept for its next episodes. */
    fun audioLang(metaId: String): String? = sp.getString(k("audio.$metaId"), null)
    fun setAudioLang(metaId: String, v: String?) = sp.edit().putString(k("audio.$metaId"), v).apply()
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

    data class YearRecap(val year: Int, val titles: Int, val episodes: Int, val hours: Int, val top: Preview?, val topCount: Int, val films: Int)

    /** "ملخص 5broz": what this profile watched since 1 January. */
    suspend fun yearRecap(): YearRecap? {
        val p = pid ?: return null
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return try {
            val rows = select(
                "watch_progress?select=meta_id,video_id,type,name,poster,position_sec,duration_sec,finished" +
                    "&profile_id=eq.${q(p)}&updated_at=gte.$year-01-01&limit=2000",
            )
            var secs = 0L
            var episodes = 0
            var films = 0
            val perTitle = HashMap<String, Pair<Preview, Int>>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val meta = r.str("meta_id") ?: continue
                val pos = r.optLong("position_sec", 0)
                if (pos < 120) continue
                secs += if (r.optBoolean("finished")) maxOf(pos, r.optLong("duration_sec", pos)) else pos
                val series = r.str("type") == "series"
                if (series) episodes++ else films++
                val prev = perTitle[meta]
                perTitle[meta] = (prev?.first ?: Preview(meta, if (series) "series" else "movie", r.str("name") ?: "", r.str("poster"))) to ((prev?.second ?: 0) + 1)
            }
            if (perTitle.isEmpty()) return YearRecap(year, 0, 0, 0, null, 0, 0)
            val top = perTitle.values.maxByOrNull { it.second }
            YearRecap(year, perTitle.size, episodes, (secs / 3600).toInt(), top?.first, top?.second ?: 0, films)
        } catch (_: Exception) {
            null
        }
    }

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

    /** Whether this title is in the profile's continue-watching row. */
    suspend fun hasResume(metaId: String): Boolean {
        val p = pid ?: return false
        return try {
            select("watch_progress?select=video_id&profile_id=eq.${q(p)}&meta_id=eq.${q(metaId)}&finished=eq.false&position_sec=gte.30&limit=1").length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Takes a title out of continue watching (keeps what was finished — episode check marks stay). */
    suspend fun clearResume(metaId: String): Boolean {
        val p = pid ?: return false
        return try {
            Net.send(
                "PATCH",
                "$URL/watch_progress?profile_id=eq.${q(p)}&meta_id=eq.${q(metaId)}&finished=eq.false",
                JSONObject().put("position_sec", 0).toString(),
                headers + mapOf("Prefer" to "return=minimal"),
            )
            true
        } catch (_: Exception) {
            false
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

    // ---------- addons (shared by the whole household, like Stremio / Nuvio) ----------

    data class Addon(val url: String, val name: String, val enabled: Boolean = true, val position: Int = 0, val catalogs: Boolean = true)

    @Volatile private var addonCache: Pair<Long, List<String>>? = null

    suspend fun addons(): List<Addon> = try {
        select("household_addons?select=url,name,enabled,position,catalogs&order=position.asc,added_at.asc").mapObjects { r ->
            Addon(
                r.str("url") ?: return@mapObjects null, r.str("name") ?: "",
                enabled = r.optBoolean("enabled", true), position = r.optInt("position", 0), catalogs = r.optBoolean("catalogs", true),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Addon URLs for stream requests (cached a few minutes). */
    suspend fun addonUrls(): List<String> {
        addonCache?.let { (at, list) -> if (System.currentTimeMillis() - at < 5 * 60_000) return list }
        var list = addons().filter { it.enabled }.map { it.url }
        // CI only: a debug build can be pointed at a test addon without touching the household list.
        if (com.fivebroz.android.BuildConfig.DEBUG) Prefs.testAddon?.let { list = list + it }
        addonCache = System.currentTimeMillis() to list
        return list
    }

    suspend fun addAddon(url: String, name: String): Boolean = try {
        upsert(
            "household_addons", "url",
            JSONObject().put("url", url).put("name", name).put("added_by", Prefs.profileName.ifBlank { null } ?: JSONObject.NULL),
        )
        addonCache = null
        true
    } catch (_: Exception) {
        false
    }

    /** Turn an addon on/off, show its catalogs on the home page or not, or move it. */
    suspend fun updateAddon(url: String, enabled: Boolean? = null, position: Int? = null, catalogs: Boolean? = null) {
        val o = JSONObject()
        enabled?.let { o.put("enabled", it) }
        position?.let { o.put("position", it) }
        catalogs?.let { o.put("catalogs", it) }
        try {
            Net.send("PATCH", "$URL/household_addons?url=eq.${q(url)}", o.toString(), headers + mapOf("Prefer" to "return=minimal"))
        } catch (_: Exception) {
        }
        addonCache = null
    }

    /** "Watched": closes it in continue watching and ticks its episodes. */
    suspend fun markWatched(item: Preview): Boolean {
        val p = pid ?: return false
        return try {
            Net.send(
                "PATCH", "$URL/watch_progress?profile_id=eq.${q(p)}&meta_id=eq.${q(item.id)}&finished=eq.false",
                JSONObject().put("finished", true).toString(), headers + mapOf("Prefer" to "return=minimal"),
            )
            if (item.type == "movie") saveProgress(item.id, item.id, item.type, item.name, item.poster, 1, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun removeAddon(url: String) {
        try {
            Net.send("DELETE", "$URL/household_addons?url=eq.${q(url)}", null, headers)
        } catch (_: Exception) {
        }
        addonCache = null
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
