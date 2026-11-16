package ir.sahlbit.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentCallRecord(
    val id: Long, val number: String, val name: String, val type: Int, val date: Long, val durationSeconds: Long
)
data class AndroidCallContact(val name: String, val photoUri: String?)

fun readRecentCallRecords(context: Context, limit: Int = 30): List<RecentCallRecord> = runCatching {
    val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
    val limitedUri = CallLog.Calls.CONTENT_URI.buildUpon()
        .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.coerceIn(1, 100).toString())
        .build()
    context.contentResolver.query(limitedUri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
        val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        buildList {
            while (cursor.moveToNext() && size < limit) add(RecentCallRecord(
                cursor.getLong(idIndex), cursor.getString(numberIndex).orEmpty(), cursor.getString(nameIndex).orEmpty(),
                cursor.getInt(typeIndex), cursor.getLong(dateIndex), cursor.getLong(durationIndex)
            ))
        }
    } ?: emptyList()
}.getOrDefault(emptyList())

/**
 * Read Android phone contacts once for the recent-calls list.
 * This avoids issuing a separate PhoneLookup provider query from every visible call card.
 * Must be called from a background dispatcher.
 */
internal fun readAndroidCallContacts(context: Context): Map<String, AndroidCallContact> = runCatching {
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        ),
        null,
        null,
        null
    )?.use { cursor ->
        val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
        val contacts = LinkedHashMap<String, AndroidCallContact>()
        while (cursor.moveToNext()) {
            val key = normalizeRecentNumber(cursor.getString(numberIndex).orEmpty())
            if (key.isNotBlank()) {
                val name = cursor.getString(nameIndex).orEmpty()
                val photo = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                val existing = contacts[key]
                if (existing == null || (existing.name.isBlank() && name.isNotBlank())) {
                    contacts[key] = AndroidCallContact(name, photo)
                }
            }
        }
        contacts
    } ?: emptyMap()
}.getOrDefault(emptyMap())

/** Reuses provider snapshots while navigating between SahlBit screens. A new
 * activity-resume revision invalidates the snapshot so external changes are seen. */
private object RecentCallsSnapshotCache {
    private val recordsLock = Any()
    private val contactsLock = Any()
    private var recordsRevision = Int.MIN_VALUE
    private var recordsSnapshot: List<RecentCallRecord> = emptyList()
    private var contactsRevision = Int.MIN_VALUE
    private var contactsSnapshot: Map<String, AndroidCallContact> = emptyMap()

    fun records(context: Context, revision: Int): List<RecentCallRecord> = synchronized(recordsLock) {
        if (recordsRevision != revision) {
            recordsSnapshot = readRecentCallRecords(context)
            recordsRevision = revision
        }
        recordsSnapshot
    }

    fun contacts(context: Context, revision: Int): Map<String, AndroidCallContact> = synchronized(contactsLock) {
        if (contactsRevision != revision) {
            contactsSnapshot = readAndroidCallContacts(context)
            contactsRevision = revision
        }
        contactsSnapshot
    }
}

internal fun readRecentCallRecordsCached(context: Context, revision: Int): List<RecentCallRecord> =
    RecentCallsSnapshotCache.records(context, revision)

internal fun readAndroidCallContactsCached(context: Context, revision: Int): Map<String, AndroidCallContact> =
    RecentCallsSnapshotCache.contacts(context, revision)

private fun normalizeRecentNumber(number: String): String = number.map { ch ->
    when (ch) {
        in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
        in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
        else -> ch
    }
}.filter(Char::isDigit).joinToString("")

private fun toPersianRecentDigits(value: String): String = value.map { ch ->
    if (ch in "0123456789") ('۰'.code + ch.code - '0'.code).toChar() else ch
}.joinToString("")

@Composable
fun RecentCallCard(call: RecentCallRecord, entries: List<LauncherEntry>, customContacts: List<CustomContact>, prefs: android.content.SharedPreferences, androidContacts: Map<String, AndroidCallContact> = emptyMap(), photoRevision: Int = 0, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val normalized = remember(call.number) { normalizeRecentNumber(call.number) }
    val matchingEntry = remember(call.number, entries) { entries.firstOrNull { it.kind == "contact" && normalizeRecentNumber(it.phoneNumber.orEmpty()) == normalized } }
    val matchingCustom = remember(call.number, customContacts) {
        customContacts.asSequence().flatMap { contact -> contact.phones.asSequence().map { phone -> contact to phone } }
            .firstOrNull { normalizeRecentNumber(it.second.number) == normalized }
    }
    val contactsGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val allowAndroid = matchingCustom == null && prefs.getBoolean("recent_show_android_unknown", false)
    val androidContact = androidContacts[normalized].takeIf {
        contactsGranted && (allowAndroid || !matchingCustom?.second?.contactUri.isNullOrBlank())
    }
    val customName = matchingCustom?.first?.name.orEmpty()
    val androidName = call.name.ifBlank { androidContact?.name.orEmpty() }
    val showAndroidData = matchingCustom != null || allowAndroid
    val availableAndroidName = if (showAndroidData) androidName else ""
    val nameSource = prefs.getString("recent_name_source", "custom") ?: "custom"
    val resolvedName = when (nameSource) {
        "android" -> availableAndroidName.ifBlank { customName }
        "custom_first" -> customName.ifBlank { availableAndroidName }
        "android_first" -> availableAndroidName.ifBlank { customName }
        else -> customName.ifBlank { availableAndroidName }
    }
    val customPhoto = matchingCustom?.second?.takeIf { it.contactUri.isNullOrBlank() }?.photoUri ?: matchingEntry?.takeIf { it.photoSource != "contact" }?.photoUri
    val androidPhoto = matchingCustom?.second?.takeIf { !it.contactUri.isNullOrBlank() }?.photoUri ?: matchingEntry?.takeIf { it.photoSource == "contact" }?.photoSourceUri ?: androidContact?.photoUri
    val photoSource = prefs.getString("recent_photo_source", "custom") ?: "custom"
    val resolvedPhoto = if (!showAndroidData) customPhoto else when (photoSource) {
        "android" -> androidPhoto
        "both_android_first" -> androidPhoto ?: customPhoto
        "both_custom_first" -> customPhoto ?: androidPhoto
        else -> customPhoto
    }
    val showPhoto = prefs.getBoolean("recent_show_photos", true)
    val photoBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, resolvedPhoto, showPhoto, photoRevision) {
        value = if (showPhoto && !resolvedPhoto.isNullOrBlank()) withContext(Dispatchers.IO) {
            runCatching { decodeCachedScaledBitmap(context, Uri.parse(resolvedPhoto), 384, photoRevision)?.asImageBitmap() }.getOrNull()
        } else null
    }
    val showName = prefs.getBoolean("recent_show_names", true) && resolvedName.isNotBlank()
    val showNumber = prefs.getBoolean("recent_show_numbers", true) && call.number.isNotBlank()
    val showStatusIcon = prefs.getBoolean("recent_show_status_icon", true)
    val showStatusText = prefs.getBoolean("recent_show_status_text", false)
    val avatarSize = prefs.getInt("recent_avatar_size", 64).coerceIn(40, 112)
    val avatarRadius = prefs.getInt("recent_avatar_radius", 20).coerceIn(0, 50)
    val nameSize = prefs.getInt("recent_name_size", 15).coerceIn(11, 24)
    val statusSize = prefs.getInt("recent_status_icon_size", 24).coerceIn(16, 48)
    val callIconSize = prefs.getInt("recent_call_icon_size", 26).coerceIn(16, 48)
    val callTextSize = prefs.getInt("recent_call_text_size", 14).coerceIn(10, 24)
    val buttonMode = prefs.getString("recent_call_button_mode", "both") ?: "both"
    val spacing = prefs.getInt("recent_card_spacing", 18).coerceIn(4, 30)
    val (statusSymbol, statusColor, statusLabel) = if (
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && call.type == 5
    ) {
        Triple("×", Color(0xFFB3261E), "تماس ورودی ردشده")
    } else {
        when (call.type) {
            CallLog.Calls.MISSED_TYPE -> Triple("×", Color(0xFFB3261E), "تماس ورودی بدون پاسخ")
            CallLog.Calls.INCOMING_TYPE -> Triple("↙", Color(0xFF168A45), "تماس ورودی پاسخ‌داده‌شده")
            CallLog.Calls.OUTGOING_TYPE -> if (call.durationSeconds > 0) Triple("↗", Color(0xFF1976D2), "تماس خروجی با برقراری مکالمه") else Triple("↗", Color(0xFFB3261E), "تماس خروجی بدون برقراری مکالمه")
            else -> Triple("☎", Color(0xFF64748B), "وضعیت تماس نامشخص")
        }
    }
    val displayNumber = "⁦${call.number.map { ch -> if (ch in '0'..'9') ('۰'.code + ch.code - '0'.code).toChar() else ch }}⁩"
    Card(Modifier.fillMaxWidth().padding(bottom = spacing.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showPhoto) {
                Box(Modifier.size(avatarSize.dp).clip(RoundedCornerShape(avatarRadius.dp)).background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) {
                    if (photoBitmap != null) Image(photoBitmap!!, contentDescription = resolvedName, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(avatarRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, tint = Color(0xFF64748B), modifier = Modifier.size((avatarSize * .62f).dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (showName || showNumber) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (showName) Text(toPersianRecentDigits(resolvedName), color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = nameSize.sp, maxLines = 2, lineHeight = (nameSize + 4).sp)
                    if (showNumber) Text(displayNumber, color = Color(0xFF475569), fontSize = 13.sp, lineHeight = 18.sp)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compactLayout = maxWidth < 176.dp
                    val effectiveIconSize = minOf(callIconSize.dp, if (compactLayout) 20.dp else if (maxWidth < 220.dp) 32.dp else 48.dp)
                    val effectiveTextSize = minOf(callTextSize, if (compactLayout) 11 else if (maxWidth < 220.dp) 16 else 24).sp

                    @Composable
                    fun StatusArea(modifier: Modifier) {
                        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (showStatusIcon) Text(statusSymbol, color = statusColor, fontSize = statusSize.sp, fontWeight = FontWeight.Bold)
                            if (showStatusText) Text(statusLabel, color = statusColor, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2)
                        }
                    }

                    @Composable
                    fun CallAction(modifier: Modifier) {
                        OutlinedButton(
                            onClick = { onCall(call.number) },
                            modifier = modifier,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = if (compactLayout) 8.dp else 10.dp, vertical = 6.dp)
                        ) {
                            if (buttonMode != "text") Icon(Icons.Default.Call, null, modifier = Modifier.size(effectiveIconSize).scale(scaleX = -1f, scaleY = 1f))
                            if (buttonMode == "both") Spacer(Modifier.width(6.dp))
                            if (buttonMode != "icon") Text("تماس", fontSize = effectiveTextSize, maxLines = 1, softWrap = false)
                        }
                    }

                    if (compactLayout) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (showStatusIcon || showStatusText) StatusArea(Modifier.fillMaxWidth())
                            CallAction(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusArea(Modifier.weight(1f))
                            CallAction(Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentCallsPage(prefs: android.content.SharedPreferences, entries: List<LauncherEntry>, customContacts: List<CustomContact>, resumeRevision: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) }
    var contactsGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    LaunchedEffect(resumeRevision) {
        permissionGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        contactsGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionGranted = granted }
    val records by produceState(initialValue = emptyList<RecentCallRecord>(), key1 = permissionGranted, key2 = resumeRevision) {
        value = if (permissionGranted) withContext(Dispatchers.IO) { readRecentCallRecordsCached(context, resumeRevision) } else emptyList()
    }
    val needsAndroidContacts = prefs.getBoolean("recent_show_android_unknown", false) ||
        customContacts.any { contact -> contact.phones.any { !it.contactUri.isNullOrBlank() } }
    val androidContacts by produceState(initialValue = emptyMap<String, AndroidCallContact>(), key1 = contactsGranted, key2 = needsAndroidContacts, key3 = resumeRevision) {
        value = if (contactsGranted && needsAndroidContacts) withContext(Dispatchers.IO) { readAndroidCallContactsCached(context, resumeRevision) } else emptyMap()
    }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF8E7)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = Color(0xFF2563EB)) }
            Text("تماس‌های اخیر", color = Color(0xFF2563EB), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (!permissionGranted) {
            Spacer(Modifier.height(20.dp))
            Text("برای نمایش فهرست تماس‌های اخیر، دسترسی به سابقه تماس‌ها لازم است.", color = Color(0xFF172033), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) permissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                else android.widget.Toast.makeText(context, "این اجازه در اندروید ۵ هنگام نصب برنامه درخواست می‌شود.", android.widget.Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("فعال‌سازی دسترسی سابقه تماس‌ها") }
        } else if (records.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("تماسی برای نمایش وجود ندارد.", color = Color(0xFF172033), textAlign = TextAlign.Center) }
        } else {
            if (prefs.getBoolean("recent_show_saved_count", false)) Text("مخاطبان ذخیره‌شده: ${customContacts.size.toString().map { if (it in '0'..'9') ('۰'.code + it.code - '0'.code).toChar() else it }}", color = Color(0xFF475569), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 8.dp)) {
                items(records, key = { it.id }) { call ->
                    RecentCallCard(call, entries, customContacts, prefs, androidContacts, photoRevision = resumeRevision) { number -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) } }
                }
            }
        }
    }
}
