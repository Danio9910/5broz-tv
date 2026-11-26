package com.fivebroz.android.data

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
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val overview: String?,
    val thumbnail: String?,
    val released: String?,
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
)

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
)

data class Subtitle(val id: String, val url: String, val lang: String)

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
    )
}

object Api {
    private const val B = Net.SITE

    suspend fun checkCode(code: String): Boolean = try {
        Net.postObj("$B/api/gate", JSONObject().put("code", code)).optBoolean("ok")
    } catch (_: Exception) {
        false
    }

    suspend fun home(kids: Boolean): Home {
        val o = Net.getObj(if (kids) "$B/api/home?kids=1" else "$B/api/home")
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
                )
            },
            related = m.optJSONArray("related").mapObjects(::parsePreview),
            collectionName = col?.str("name"),
            collection = (col?.optJSONArray("items")).mapObjects(::parsePreview),
        )
    }

    suspend fun search(q: String): List<Preview> =
        Net.getObj("$B/api/search?q=${enc(q)}").optJSONArray("results").mapObjects(::parsePreview)

    suspend fun streams(type: String, videoId: String): List<Stream> {
        val body = JSONObject().put("type", type).put("id", videoId)
            .put("addons", org.json.JSONArray()).put("p2p", true)
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
            )
        }
    }

    suspend fun subtitles(type: String, videoId: String): List<Subtitle> = try {
        val body = JSONObject().put("type", type).put("id", videoId).put("addons", org.json.JSONArray())
        Net.postObj("$B/api/subtitles", body).optJSONArray("subtitles").mapObjects { s ->
            val url = s.str("url") ?: return@mapObjects null
            Subtitle(s.str("id") ?: url, url, s.str("lang") ?: "sub")
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Subtitles are proxied through the site, which converts SRT to WebVTT. */
    fun subtitleProxy(url: String) = "$B/api/sub?u=${enc(url)}"

    data class AppVersion(val code: Int, val name: String, val url: String, val notes: String?)

    suspend fun appVersion(): AppVersion? = try {
        val o = Net.getObj("$B/api/appversion")
        AppVersion(o.optInt("versionCode"), o.optString("versionName"), o.optString("url"), o.str("notes"))
    } catch (_: Exception) {
        null
    }
}
