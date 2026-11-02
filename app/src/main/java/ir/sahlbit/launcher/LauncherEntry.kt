package ir.sahlbit.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LauncherEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val title: String,
    val page: Int,
    val contactUri: String? = null,
    val phoneNumber: String? = null,
    val photoUri: String? = null,
    val packageName: String? = null
)

object LauncherEntryStore {
    private const val KEY = "launcher_entries_v1"

    fun load(prefs: android.content.SharedPreferences): List<LauncherEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(LauncherEntry(
                    id = o.optString("id"),
                    kind = o.optString("kind"),
                    title = o.optString("title"),
                    page = o.optInt("page", 0),
                    contactUri = o.optString("contactUri").takeIf(String::isNotBlank),
                    phoneNumber = o.optString("phoneNumber").takeIf(String::isNotBlank),
                    photoUri = o.optString("photoUri").takeIf(String::isNotBlank),
                    packageName = o.optString("packageName").takeIf(String::isNotBlank)
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun save(prefs: android.content.SharedPreferences, entries: List<LauncherEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id); put("kind", e.kind); put("title", e.title); put("page", e.page)
                put("contactUri", e.contactUri ?: ""); put("phoneNumber", e.phoneNumber ?: "")
                put("photoUri", e.photoUri ?: ""); put("packageName", e.packageName ?: "")
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}
