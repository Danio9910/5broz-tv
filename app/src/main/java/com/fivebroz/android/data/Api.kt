package com.fivebroz.android.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.URLEncoder

data class Preview(
    val id: String,
    val type: String, // "movie" | "series"
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val logo: String? = null, // the title's own artwork (wordmark)
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val overview: String?,
    val thumbnail: String?,
    val released: String?,
    val rating: Double? = null,
)

data class Detail(
    val base: Preview,
    val genres: List<String>,
    val runtime: String?,
    val cast: List<String>,
    val director: List<String>,
    val certification: String?,
    val episodes: List<Episode>,
    val related: List<Preview>,
    val collectionName: String?,
    val collection: List<Preview>,
    val people: List<CastMember> = emptyList(),
    val trailer: String? = null, // YouTube key
    /** Scores from other sites (MDBList), e.g. "tomatoes" -> 91. */
    val ratings: Map<String, Double> = emptyMap(),
)

data class CastMember(val id: String, val name: String, val character: String?, val photo: String?)

data class Row(val title: String, val items: List<Preview>)

data class Home(
    val heroes: List<Preview>,
    val top10: List<Preview>,
    val rows: List<Row>,
)

data class Stream(
    val url: String?, // direct link (debrid) — or null for a torrent
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val trackers: List<String> = emptyList(),
    val name: String,
    val title: String,
    val source: String,
    val filename: String?,
    val sizeBytes: Long?,
    /** Headers some addons require for their direct links (Referer, User-Agent…). */
    val headers: Map<String, String> = emptyMap(),
)

data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val hash: Boolean = false, // made for this exact file — perfect timing
    val fps: Double? = null,
    val file: String? = null, // the release it was timed for
) {
    val isArabic get() = lang.lowercase() in setOf("ara", "ar", "arabic", "العربية")
    val isEnglish get() = lang.lowercase() in setOf("eng", "en", "english")
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private fun parsePreview(o: JSONObject): Preview? {
    val id = o.str("id") ?: return null
    return Preview(
        id = id,
        type = if (o.str("type") == "series") "series" else "movie",
        name = o.str("name") ?: "",
        poster = o.str("poster"),
        background = o.str("background"),
        description = o.str("description"),
        year = o.str("releaseInfo"),
        rating = o.str("imdbRating"),
        logo = o.str("logo"),
    )
}

object Api {
    private const val B = Net.SITE

    suspend fun checkCode(code: String): Boolean = try {
        Net.postObj("$B/api/gate", JSONObject().put("code", code)).optBoolean("ok")
    } catch (_: Exception) {
        false
    }

    /** The last home page this device loaded, so the app opens instantly while the fresh one arrives. */
    fun cachedHome(kids: Boolean): Home? = try {
        Prefs.cacheFile(if (kids) "home-kids.json" else "home.json")?.takeIf { it.exists() }?.readText()?.let { parseHome(JSONObject(it)) }
    } catch (_: Exception) {
        null
    }

    suspend fun home(kids: Boolean): Home {
        val raw = Net.get(if (kids) "$B/api/home?kids=1" else "$B/api/home")
        val h = parseHome(JSONObject(raw))
        if (h.heroes.isNotEmpty() || h.rows.isNotEmpty()) {
            runCatching { Prefs.cacheFile(if (kids) "home-kids.json" else "home.json")?.writeText(raw) }
        }
        return h
    }

    private fun parseHome(o: JSONObject): Home {
        val heroes = o.optJSONArray("heroes").mapObjects(::parsePreview).ifEmpty {
            listOfNotNull(o.optJSONObject("hero")?.let(::parsePreview))
        }
        return Home(
            heroes = heroes,
            top10 = o.optJSONArray("top10").mapObjects(::parsePreview),
            rows = o.optJSONArray("rows").mapObjects { r ->
                val items = r.optJSONArray("items").mapObjects(::parsePreview)
                if (items.isEmpty()) null else Row(r.str("title") ?: "", items)
            },
        )
    }

    suspend fun detail(type: String, id: String): Detail? {
        val m = Net.getObj("$B/api/detail?type=${enc(type)}&id=${enc(id)}").optJSONObject("meta")
            ?: return null
        val base = parsePreview(m) ?: return null
        val col = m.optJSONObject("collection")
        return Detail(
            base = base,
            genres = m.optJSONArray("genres").strings(),
            runtime = m.str("runtime"),
            cast = m.optJSONArray("cast").strings(),
            director = m.optJSONArray("director").strings(),
            certification = m.str("certification"),
            episodes = m.optJSONArray("videos").mapObjects { v ->
                val vid = v.str("id") ?: return@mapObjects null
                Episode(
                    id = vid,
                    title = v.str("title") ?: "",
                    season = v.optInt("season", 1),
                    episode = v.optInt("episode", 1),
                    overview = v.str("overview"),
                    thumbnail = v.str("thumbnail"),
                    released = v.str("released"),
                    rating = v.optDouble("rating").takeIf { !it.isNaN() },
                )
            },
            related = m.optJSONArray("related").mapObjects(::parsePreview),
            collectionName = col?.str("name"),
            collection = (col?.optJSONArray("items")).mapObjects(::parsePreview),
            people = m.optJSONArray("castPhotos").mapObjects { c ->
                val pid = c.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapObjects null
                CastMember(pid, c.str("name") ?: return@mapObjects null, c.str("character"), c.str("photo"))
            },
            trailer = m.str("trailer"),
            ratings = m.optJSONObject("ratings")?.let { r -> r.keys().asSequence().associateWith { r.optDouble(it) }.filterValues { !it.isNaN() } } ?: emptyMap(),
        )
    }

    private fun kidsParam() = if (Prefs.profileKids) "&kids=1" else ""

    suspend fun search(q: String): List<Preview> =
        Net.getObj("$B/api/search?q=${enc(q)}${kidsParam()}").optJSONArray("results").mapObjects(::parsePreview)

    suspend fun trending(): List<Preview> = try {
        Net.getObj("$B/api/search?trending=1${kidsParam()}").optJSONArray("results").mapObjects(::parsePreview)
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun genre(id: String, type: String): List<Preview> = try {
        Net.getObj("$B/api/search?genre=${enc(id)}&type=${enc(type)}${kidsParam()}").optJSONArray("results").mapObjects(::parsePreview)
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun logo(type: String, id: String): String? = try {
        Net.getObj("$B/api/detail?logo=1&type=${enc(type)}&id=${enc(id)}").str("logo")
    } catch (_: Exception) {
        null
    }

    data class Person(val name: String, val photo: String?, val bio: String?, val born: String?, val works: List<Preview>)

    suspend fun person(id: String): Person? = try {
        val o = Net.getObj("$B/api/detail?person=${enc(id)}")
        Person(o.optString("name"), o.str("photo"), o.str("bio"), o.str("born"), o.optJSONArray("works").mapObjects(::parsePreview))
    } catch (_: Exception) {
        null
    }

    suspend fun streams(type: String, videoId: String): List<Stream> {
        val body = JSONObject().put("type", type).put("id", videoId)
            .put("addons", org.json.JSONArray(Db.addonUrls())).put("p2p", true)
        return Net.postObj("$B/api/streams", body).optJSONArray("streams").mapObjects { s ->
            val url = s.str("url")
            val hash = s.str("infoHash")
            if (url == null && hash == null) return@mapObjects null
            val hints = s.optJSONObject("behaviorHints")
            Stream(
                url = url,
                infoHash = hash,
                fileIdx = s.int("fileIdx"),
                trackers = s.optJSONArray("sources").strings()
                    .filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") },
                name = s.str("name") ?: "",
                title = s.str("title") ?: s.str("description") ?: "",
                source = s.str("_source") ?: "",
                filename = hints?.str("filename"),
                sizeBytes = hints?.optLong("videoSize")?.takeIf { it > 0 },
                headers = hints?.optJSONObject("proxyHeaders")?.optJSONObject("request")?.let { h ->
                    h.keys().asSequence().associateWith { k -> h.optString(k) }.filterValues { it.isNotBlank() }
                } ?: emptyMap(),
            )
        }
    }

    suspend fun subtitles(
        type: String,
        videoId: String,
        videoHash: String? = null,
        videoSize: Long? = null,
        filename: String? = null,
    ): List<Subtitle> = try {
        val body = JSONObject().put("type", type).put("id", videoId).put("addons", org.json.JSONArray(Db.addonUrls()))
        if (videoHash != null) body.put("videoHash", videoHash)
        if (videoSize != null) body.put("videoSize", videoSize)
        if (filename != null) body.put("filename", filename)
        Net.postObj("$B/api/subtitles", body).optJSONArray("subtitles").mapObjects { s ->
            val url = s.str("url") ?: return@mapObjects null
            Subtitle(
                s.str("id") ?: url, url, s.str("lang") ?: "sub",
                hash = s.optBoolean("hash"),
                fps = s.optDouble("fps").takeIf { !it.isNaN() },
                file = s.str("file"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Subtitles are proxied through the site, which converts SRT to WebVTT. */
    fun subtitleProxy(
        url: String,
        offsetSec: Float = 0f,
        ref: String? = null, // AutoSync reference (made for this file)
        second: String? = null, // a second language shown under the first
        translate: Boolean = false, // machine-translate to Arabic
    ) = "$B/api/sub?u=${enc(url)}" +
        (if (offsetSec != 0f) "&o=$offsetSec" else "") +
        (ref?.let { "&ref=${enc(it)}" } ?: "") +
        (second?.let { "&u2=${enc(it)}" } ?: "") +
        (if (translate) "&tr=ar" else "")

    /** Home rows from the household's addons' own catalogs (like Stremio / Nuvio). */
    suspend fun addonRows(): List<Row> = try {
        coroutineScope {
            Db.addons().filter { it.enabled && it.catalogs }.map { a ->
                async {
                    val m = runCatching { Net.getObj("${a.url}/manifest.json") }.getOrNull() ?: return@async emptyList<Row>()
                    val name = m.optString("name").ifBlank { a.name }
                    m.optJSONArray("catalogs").mapObjects { c ->
                        val type = c.optString("type")
                        val id = c.optString("id")
                        val extras = c.optJSONArray("extra")
                        val needsInput = (0 until (extras?.length() ?: 0)).any { extras!!.optJSONObject(it)?.optBoolean("isRequired") == true } ||
                            (c.optJSONArray("extraRequired")?.length() ?: 0) > 0
                        if ((type != "movie" && type != "series") || id.isBlank() || needsInput) null else Triple(type, id, c.optString("name"))
                    }.take(4).map { (type, id, title) ->
                        async {
                            val items = runCatching {
                                Net.getObj("${a.url}/catalog/$type/${enc(id)}.json").optJSONArray("metas").mapObjects { o ->
                                    parsePreview(o)?.takeIf { it.id.startsWith("tt") || it.id.startsWith("tmdb:") }
                                }
                            }.getOrDefault(emptyList())
                            if (items.size < 3) null else Row(listOf(title, name).filter { it.isNotBlank() }.distinct().joinToString(" — "), items.take(30))
                        }
                    }.mapNotNull { it.await() }
                }
            }.flatMap { it.await() }.take(10)
        }
    } catch (_: Exception) {
        emptyList()
    }

    // ---------- Trakt (per profile) ----------

    suspend fun trakt(action: String, extra: JSONObject = JSONObject()): JSONObject? = try {
        Net.postObj("$B/api/trakt", extra.put("action", action).put("profile", Prefs.profileId ?: ""))
    } catch (_: Exception) {
        null
    }

    suspend fun traktRows(): List<Row> {
        val o = trakt("rows") ?: return emptyList()
        return o.optJSONArray("rows").mapObjects { r ->
            val items = r.optJSONArray("items").mapObjects(::parsePreview)
            if (items.isEmpty()) null else Row(r.str("title") ?: "", items)
        }
    }

    /** A timed part of an episode from IntroDB (community timestamps, no key needed). */
    data class Segment(val start: Double, val end: Double)
    data class Segments(val intro: Segment?, val recap: Segment?, val outro: Segment?, val noIntro: Boolean)

    suspend fun segments(videoId: String, isMovie: Boolean): Segments? = try {
        val p = videoId.split(":")
        val q = if (isMovie || p.size < 3) "imdb_id=${enc(p[0])}&is_movie=true" else "imdb_id=${enc(p[0])}&season=${p[1]}&episode=${p[2]}"
        val o = Net.getObj("https://api.introdb.app/segments?$q")
        fun seg(k: String) = o.optJSONObject(k)?.let { s ->
            val a = s.optDouble("start_sec"); val b = s.optDouble("end_sec")
            if (a.isNaN() || b.isNaN() || b <= a) null else Segment(a, b)
        }
        Segments(seg("intro"), seg("recap"), seg("outro"), o.optBoolean("no_intro"))
    } catch (_: Exception) {
        null
    }

    data class Health(val ok: Boolean, val catalog: Boolean, val torrents: Boolean, val debridActive: Boolean, val daysLeft: Int?, val translate: Boolean = false)

    suspend fun health(): Health? = try {
        val o = Net.getObj("$B/api/health")
        val d = o.optJSONObject("debrid")
        Health(
            ok = o.optBoolean("ok", true),
            catalog = o.optBoolean("catalog", true),
            torrents = o.optBoolean("torrents", true),
            debridActive = d?.optBoolean("active", false) ?: false,
            daysLeft = d?.let { if (it.isNull("daysLeft")) null else it.optInt("daysLeft") },
            translate = o.optBoolean("translate", false),
        )
    } catch (_: Exception) {
        null
    }

    data class AppVersion(val code: Int, val name: String, val url: String, val notes: String?)

    suspend fun appVersion(): AppVersion? = try {
        val o = Net.getObj("$B/api/appversion")
        AppVersion(o.optInt("versionCode"), o.optString("versionName"), o.optString("url"), o.str("notes"))
    } catch (_: Exception) {
        null
    }
}
