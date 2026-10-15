package com.geekfrend.intouch.lab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything the lab app remembers, kept in one small SharedPreferences file on the phone. */
object Store {
    private const val PREFS = "lab"
    private const val MAX_EVENTS = 60

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The contact chosen for testing, plus what WhatsApp registered for them in the address book. */
    data class Picked(
        val name: String,
        val number: String,
        val waDataId: Long?,
        val waMime: String?,
        val waMimes: List<String>,
    )

    fun savePicked(ctx: Context, picked: Picked) {
        prefs(ctx).edit()
            .putString("name", picked.name)
            .putString("number", picked.number)
            .putLong("waDataId", picked.waDataId ?: -1L)
            .putString("waMime", picked.waMime)
            .putString("waMimes", picked.waMimes.joinToString("\n"))
            .apply()
    }

    fun picked(ctx: Context): Picked? {
        val p = prefs(ctx)
        val number = p.getString("number", null) ?: return null
        val dataId = p.getLong("waDataId", -1L)
        return Picked(
            name = p.getString("name", "") ?: "",
            number = number,
            waDataId = if (dataId >= 0) dataId else null,
            waMime = p.getString("waMime", null),
            waMimes = (p.getString("waMimes", "") ?: "").split("\n").filter { it.isNotBlank() },
        )
    }

    /** Checklist answers: "yes", "no", or "" (not tried). */
    fun check(ctx: Context, id: String): String = prefs(ctx).getString("check_$id", "") ?: ""

    fun setCheck(ctx: Context, id: String, value: String) {
        prefs(ctx).edit().putString("check_$id", value).apply()
    }

    /** What happened the last time a button was pressed, e.g. "opened" or "failed: ...". */
    fun note(ctx: Context, key: String): String = prefs(ctx).getString("note_$key", "") ?: ""

    fun setNote(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString("note_$key", value).apply()
    }

    @Synchronized
    fun addEvent(ctx: Context, event: JSONObject) {
        val all = JSONArray(prefs(ctx).getString("events", "[]"))
        all.put(event)
        val kept = JSONArray()
        for (i in maxOf(0, all.length() - MAX_EVENTS) until all.length()) kept.put(all.get(i))
        prefs(ctx).edit().putString("events", kept.toString()).apply()
    }

    /** Newest first. */
    @Synchronized
    fun events(ctx: Context): List<JSONObject> {
        val all = JSONArray(prefs(ctx).getString("events", "[]"))
        return (0 until all.length()).map { all.getJSONObject(it) }.reversed()
    }

    @Synchronized
    fun clearEvents(ctx: Context) {
        prefs(ctx).edit().putString("events", "[]").apply()
    }
}
