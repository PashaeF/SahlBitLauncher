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
    val contactLookupUri: String? = null,
    val phoneNumber: String? = null,
    val photoUri: String? = null,
    val photoSource: String = "contact",
    val photoSourceUri: String? = null,
    val manualEdit: Boolean = false,
    val packageName: String? = null
)

object LauncherEntryStore {
    private const val KEY = "launcher_entries_v1"

    fun load(prefs: android.content.SharedPreferences): List<LauncherEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val photoUri = o.optString("photoUri").takeIf(String::isNotBlank)
                val photoSourceUri = o.optString("photoSourceUri").takeIf(String::isNotBlank)
                val photoSource = when {
                    photoSourceUri != null -> "contact"
                    photoUri != null -> o.optString("photoSource", "contact").ifBlank { "contact" }
                    else -> "none"
                }
                add(LauncherEntry(
                    id = o.optString("id"),
                    kind = o.optString("kind"),
                    title = o.optString("title"),
                    page = o.optInt("page", 0),
                    contactUri = o.optString("contactUri").takeIf(String::isNotBlank),
                    contactLookupUri = o.optString("contactLookupUri").takeIf(String::isNotBlank),
                    phoneNumber = o.optString("phoneNumber").takeIf(String::isNotBlank),
                    photoUri = photoUri,
                    photoSource = photoSource,
                    photoSourceUri = photoSourceUri,
                    manualEdit = o.optBoolean("manualEdit", false),
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
                put("contactUri", e.contactUri ?: ""); put("contactLookupUri", e.contactLookupUri ?: ""); put("phoneNumber", e.phoneNumber ?: "")
                put("photoUri", e.photoUri ?: ""); put("photoSource", e.photoSource); put("photoSourceUri", e.photoSourceUri ?: ""); put("manualEdit", e.manualEdit); put("packageName", e.packageName ?: "")
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}


/**
 * Decode a photo close to the size used by the UI instead of loading the original
 * full-resolution camera image into memory for a small contact avatar.
 * Call this function from a background dispatcher.
 */
internal fun decodeScaledBitmap(
    context: android.content.Context,
    uri: android.net.Uri,
    targetSizePx: Int = 256
): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, bounds)
    } ?: return@runCatching null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val target = targetSizePx.coerceAtLeast(64)
    var sampleSize = 1
    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (longestSide / (sampleSize * 2) >= target) sampleSize *= 2

    val options = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, options)
    }
}.getOrNull()
