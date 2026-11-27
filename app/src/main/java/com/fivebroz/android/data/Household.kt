package com.fivebroz.android.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** A device in the house (phone, tablet or TV) that other devices can send to. */
data class Device(val id: String, val name: String, val kind: String, val profileName: String?, val lastSeenMs: Long)

/** Something one device asks another to do: press a key, type, open a title, continue playing. */
data class Signal(val id: Long, val fromName: String?, val kind: String, val payload: JSONObject)

/**
 * Devices in the house talk through two small tables: who is here (household_devices) and
 * what one asks another to do (household_signals). Used for "phone as remote" and
 * "continue on the TV".
 */
object Household {
    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")
    private val H get() = Db.headers

    /** This install's id (stable), created on first use. */
    fun myId(ctx: Context): String {
        val sp = ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE)
        sp.getString("device_id", null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString().take(12)
        sp.edit().putString("device_id", id).apply()
        return id
    }

    fun myName(ctx: Context, tv: Boolean): String {
        val sp = ctx.getSharedPreferences("5broz", Context.MODE_PRIVATE)
        sp.getString("device_name", null)?.let { return it }
        val model = Build.MODEL.orEmpty().trim()
        return if (tv) "تلفزيون ${model.take(18)}".trim() else model.ifBlank { "جوال" }
    }

    /** "I'm here" — called when the app opens and every minute while it's open. */
    suspend fun heartbeat(ctx: Context, tv: Boolean) {
        runCatching {
            val row = JSONObject()
                .put("id", myId(ctx))
                .put("name", myName(ctx, tv))
                .put("kind", if (tv) "tv" else "phone")
                .put("profile_name", Prefs.profileName.ifBlank { JSONObject.NULL })
                .put("last_seen", java.time.Instant.now().toString())
            Net.send("POST", "${Db.URL}/household_devices", JSONArray().put(row).toString(), H + mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        }
    }

    /** TVs that were open in the last few minutes. */
    suspend fun tvs(ctx: Context): List<Device> = runCatching {
        val since = java.time.Instant.now().minusSeconds(300).toString()
        JSONArray(Net.get("${Db.URL}/household_devices?select=*&kind=eq.tv&last_seen=gte.${q(since)}&order=last_seen.desc", H))
            .mapObjects { o ->
                val id = o.str("id") ?: return@mapObjects null
                if (id == myId(ctx)) return@mapObjects null
                Device(id, o.str("name") ?: "تلفزيون", "tv", o.str("profile_name"), runCatching { java.time.Instant.parse(o.str("last_seen")).toEpochMilli() }.getOrDefault(0))
            }
    }.getOrDefault(emptyList())

    suspend fun device(id: String): Device? = runCatching {
        JSONArray(Net.get("${Db.URL}/household_devices?select=*&id=eq.${q(id)}", H)).mapObjects { o ->
            Device(o.str("id") ?: return@mapObjects null, o.str("name") ?: "تلفزيون", o.str("kind") ?: "tv", o.str("profile_name"), 0)
        }.firstOrNull()
    }.getOrNull()

    suspend fun send(to: String, kind: String, payload: JSONObject = JSONObject()): Boolean = runCatching {
        val row = JSONObject().put("to_device", to).put("from_name", Prefs.profileName.ifBlank { JSONObject.NULL })
            .put("kind", kind).put("payload", payload)
        Net.send("POST", "${Db.URL}/household_signals", JSONArray().put(row).toString(), H + mapOf("Prefer" to "return=minimal"))
        true
    }.getOrDefault(false)

    /** Waiting requests for this device; each is taken (deleted) as it's read. */
    suspend fun take(ctx: Context): List<Signal> = runCatching {
        val me = myId(ctx)
        val list = JSONArray(Net.get("${Db.URL}/household_signals?select=*&to_device=eq.${q(me)}&order=id.asc&limit=40", H)).mapObjects { o ->
            val at = runCatching { java.time.Instant.parse(o.str("created_at")!!.replace(" ", "T")).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            Signal(o.optLong("id"), o.str("from_name"), o.str("kind") ?: return@mapObjects null, o.optJSONObject("payload") ?: JSONObject())
                .takeIf { System.currentTimeMillis() - at < 90_000 }
        }
        if (list.isNotEmpty()) {
            Net.send("DELETE", "${Db.URL}/household_signals?to_device=eq.${q(me)}&id=lte.${list.last().id}", null, H)
        }
        // Old requests (device was off) were dropped above rather than replayed.
        list.filterNotNull()
    }.getOrDefault(emptyList())
}
