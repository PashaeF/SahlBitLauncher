package ir.sahlbit.launcher

import org.json.JSONArray
import android.graphics.Bitmap
import android.util.LruCache
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


/**
 * Bounded in-memory cache for scaled contact photos. The revision is supplied
 * by the owning screen so photos can be refreshed after returning from Android.
 * Call from a background dispatcher.
 */
private val scaledContactPhotoCache = object : LruCache<String, Bitmap>(8 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

internal fun decodeCachedScaledBitmap(
    context: android.content.Context,
    uri: android.net.Uri,
    targetSizePx: Int = 256,
    cacheRevision: Int = 0
): Bitmap? {
    val key = "$uri|$targetSizePx|$cacheRevision"
    synchronized(scaledContactPhotoCache) {
        scaledContactPhotoCache.get(key)?.let { return it }
    }
    val decoded = decodeScaledBitmap(context, uri, targetSizePx) ?: return null
    synchronized(scaledContactPhotoCache) {
        val existing = scaledContactPhotoCache.get(key)
        if (existing != null) {
            if (decoded !== existing && !decoded.isRecycled) decoded.recycle()
            return existing
        }
        scaledContactPhotoCache.put(key, decoded)
    }
    return decoded
}


/**
 * Bounded cache for application icons shared by the home screen and app lists.
 * The package's last-update timestamp invalidates the cached icon after an app update.
 * Call this function from a background dispatcher.
 */
private val applicationIconCache = object : LruCache<String, Bitmap>(4 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}

internal fun decodeCachedApplicationIcon(
    context: android.content.Context,
    packageName: String,
    targetSizePx: Int = 96
): Bitmap? = runCatching {
    val packageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val target = targetSizePx.coerceIn(48, 192)
    val key = packageName + "|" + packageInfo.lastUpdateTime + "|" + target

    synchronized(applicationIconCache) {
        applicationIconCache.get(key)?.let { return it }
    }

    val drawable = packageManager.getApplicationIcon(packageName)
    val bitmap = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, target, target)
    drawable.draw(canvas)

    synchronized(applicationIconCache) {
        val existing = applicationIconCache.get(key)
        if (existing != null) {
            if (bitmap !== existing && !bitmap.isRecycled) bitmap.recycle()
            existing
        } else {
            applicationIconCache.put(key, bitmap)
            bitmap
        }
    }
}.getOrNull()
