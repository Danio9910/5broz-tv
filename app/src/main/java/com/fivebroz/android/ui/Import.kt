package com.fivebroz.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fivebroz.android.data.Api
import com.fivebroz.android.data.Db
import com.fivebroz.android.data.Net
import com.fivebroz.android.data.Prefs
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bring a life from Stremio or Nuvio into this profile: what you saved, where you stopped,
 * what you finished. Stremio is asked directly from this device (the password never reaches
 * 5broz and is not kept). Nuvio keeps its history on Trakt, so it comes through Trakt.
 */
object Importer {
    data class Got(val library: JSONArray, val progress: JSONArray, val watched: JSONArray, val addons: List<Pair<String, String>> = emptyList()) {
        val total get() = library.length() + progress.length() + watched.length() + addons.size
    }

    private const val STREMIO = "https://api.strem.io/api"

    /** Throws with a message the person can act on. */
    suspend fun stremio(email: String, password: String): Got {
        val login = runCatching {
            Net.postObj("$STREMIO/login", JSONObject().put("type", "Login").put("email", email.trim()).put("password", password).put("facebook", false))
        }.getOrElse { throw IllegalStateException("ما قدرنا نوصل لستريميو. تأكد من النت وجرّب مرة ثانية.") }
        val key = login.optJSONObject("result")?.optString("authKey")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("الإيميل أو كلمة المرور غلط في ستريميو.")
        try {
            val items = Net.postObj(
                "$STREMIO/datastoreGet",
                JSONObject().put("authKey", key).put("collection", "libraryItem").put("ids", JSONArray()).put("all", true),
            ).optJSONArray("result") ?: JSONArray()
            val library = JSONArray(); val progress = JSONArray(); val watched = JSONArray()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val id = it.optString("_id")
                if (!id.startsWith("tt")) continue
                val type = if (it.optString("type") == "series") "series" else "movie"
                val name = it.optString("name")
                val poster = it.optString("poster").takeIf { p -> p.startsWith("http") }
                val st = it.optJSONObject("state") ?: JSONObject()
                if (!it.optBoolean("removed") && !it.optBoolean("temp")) {
                    library.put(JSONObject().put("id", id).put("type", type).put("name", name).put("poster", poster ?: JSONObject.NULL))
                }
                val offset = st.optLong("timeOffset") / 1000
                val duration = st.optLong("duration") / 1000
                val video = st.optString("video_id").takeIf { v -> v.startsWith("tt") } ?: id
                if (offset > 30 && duration > 0 && offset < duration * 0.92) {
                    progress.put(
                        JSONObject().put("metaId", id).put("videoId", video).put("type", type).put("name", name)
                            .put("poster", poster ?: JSONObject.NULL).put("position", offset).put("duration", duration),
                    )
                }
                if (type == "movie" && (st.optInt("timesWatched") > 0 || st.optInt("flaggedWatched") == 1)) {
                    watched.put(JSONObject().put("metaId", id).put("videoId", id).put("type", type).put("name", name).put("poster", poster ?: JSONObject.NULL))
                }
            }
            // the addons too, like Nuvio does (Stremio's own built-in ones are left out)
            val addons = runCatching {
                val a = Net.postObj("$STREMIO/addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", key).put("update", true))
                    .optJSONObject("result")?.optJSONArray("addons") ?: JSONArray()
                (0 until a.length()).mapNotNull { i ->
                    val x = a.optJSONObject(i) ?: return@mapNotNull null
                    val url = x.optString("transportUrl")
                    val m = x.optJSONObject("manifest")
                    val mid = m?.optString("id").orEmpty()
                    if (!url.startsWith("http") || mid == "org.stremio.local" || mid == "com.linvo.cinemeta") null
                    else url to (m?.optString("name")?.takeIf { it.isNotBlank() } ?: url)
                }
            }.getOrDefault(emptyList())
            return Got(library, progress, watched, addons)
        } finally {
            runCatching { Net.postObj("$STREMIO/logout", JSONObject().put("type", "Logout").put("authKey", key)) }
        }
    }

    /** Null when this profile isn't linked to Trakt yet. */
    suspend fun trakt(): Got? {
        val st = Api.trakt("status") ?: throw IllegalStateException("ما قدرنا نوصل لـ Trakt. جرّب بعد شوي.")
        if (!st.optBoolean("configured")) throw IllegalStateException("الربط مع Trakt مو مفعّل في الموقع بعد. قل لعدنان.")
        if (!st.optBoolean("connected")) return null
        val o = Api.trakt("import") ?: throw IllegalStateException("ما قدرنا نوصل لـ Trakt. جرّب بعد شوي.")
        return Got(
            o.optJSONArray("library") ?: JSONArray(),
            o.optJSONArray("progress") ?: JSONArray(),
            o.optJSONArray("watched") ?: JSONArray(),
        )
    }

    /** Returns how many addons were new to the house. */
    suspend fun save(g: Got): Int {
        Db.importAll(g.library, g.progress, g.watched)
        if (g.addons.isEmpty()) return 0
        val have = Db.addonUrls().toSet()
        var n = 0
        for ((url, name) in g.addons) if (url !in have && Db.addAddon(url, name)) n++
        return n
    }
}

@Composable
fun ImportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(source: String, block: suspend () -> Importer.Got?) {
        if (busy != null) return
        busy = source; error = null; result = null
        scope.launch {
            try {
                val g = block()
                if (g == null) {
                    error = "اربط هذا الملف بحساب Trakt أولاً (الإعدادات ← Trakt)، نفس الحساب اللي مربوط في نوفيو."
                } else if (g.total == 0) {
                    error = "ما لقينا شي في الحساب نستورده."
                } else {
                    val newAddons = Importer.save(g)
                    ItemEvents.libraryVersion++
                    ItemEvents.resumeVersion++
                    result = "تم: ${g.library.length()} في قائمتي، ${g.progress.length()} تكمّلها من وين وقفت، ${g.watched.length()} شفتها قبل" +
                        (if (g.addons.isNotEmpty()) "، و$newAddons إضافة جديدة للبيت." else ".")
                    pass = ""
                }
            } catch (e: Exception) {
                error = e.message ?: "صار خلل، جرّب مرة ثانية."
            }
            busy = null
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ink950)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Focusable(onBack, shape = CircleShape, lift = 1f, ring = false) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ic(Icons.Back, 22.dp) }
                }
                Text("انقل من ستريميو أو نوفيو", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineSmall)
            }
            VSpace(6.dp)
            Text(
                "قائمتك، ووين وقفت في كل فيلم وحلقة، واللي شفته قبل، تنتقل لملف «${Prefs.profileName}» وتطلع على كل أجهزتك.",
                color = C.Muted, style = MaterialTheme.typography.bodyMedium,
            )

            VSpace(22.dp)
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().background(C.Ink900, RoundedCornerShape(20.dp)).padding(18.dp)) {
                Text("ستريميو", style = MaterialTheme.typography.titleLarge)
                Text("بإيميل وكلمة مرور حسابك في ستريميو: القائمة، ووين وقفت، واللي شفته، والإضافات. نستخدمها مرة وحدة للنقل، وما نحفظها.", color = C.Muted, style = MaterialTheme.typography.bodySmall)
                VSpace(12.dp)
                Field(email, { email = it }, "الإيميل", {}, plainText = true, center = false)
                VSpace(8.dp)
                Field(pass, { pass = it }, "كلمة المرور", { if (email.isNotBlank() && pass.isNotBlank()) run("stremio") { Importer.stremio(email, pass) } }, secret = true, center = false)
                VSpace(12.dp)
                GoldButton(
                    if (busy == "stremio") "ننقل…" else "انقل من ستريميو",
                    { if (email.isNotBlank() && pass.isNotBlank()) run("stremio") { Importer.stremio(email, pass) } },
                    Modifier.fillMaxWidth(), fill = true,
                )
            }

            VSpace(14.dp)
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().background(C.Ink900, RoundedCornerShape(20.dp)).padding(18.dp)) {
                Text("نوفيو", style = MaterialTheme.typography.titleLarge)
                Text("نوفيو يحفظ مشاهدتك في Trakt. اربط هذا الملف بنفس حساب Trakt من الإعدادات، وبعدها اضغط هنا.", color = C.Muted, style = MaterialTheme.typography.bodySmall)
                VSpace(12.dp)
                GoldButton(if (busy == "trakt") "ننقل…" else "انقل من نوفيو (Trakt)", { run("trakt") { Importer.trakt() } }, Modifier.fillMaxWidth(), fill = true, filled = false)
            }

            result?.let {
                VSpace(18.dp)
                Text(it, color = C.Gold, style = MaterialTheme.typography.titleMedium)
            }
            error?.let {
                VSpace(18.dp)
                Text(it, color = C.Danger, style = MaterialTheme.typography.bodyMedium)
            }
            if (LocalIsTv.current) {
                VSpace(18.dp)
                Text("أسهل من الجوال: النقل يصير لملفك، ويطلع هنا على التلفزيون تلقائياً.", color = C.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
