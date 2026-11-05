package ir.sahlbit.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.ContactsContract
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import ir.sahlbit.launcher.ui.AnalogClock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private fun Int.toPersianDigits(): String = toString().map { ch ->
    if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
}.joinToString("" )
private fun String.toEnglishDigits(): String = map { ch ->
    when (ch) { in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar(); in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar(); else -> ch }
}.joinToString("")
private fun String.toPersianDigitsText(): String = map { ch -> if (ch in '0'..'9') ('۰'.code + ch.code - '0'.code).toChar() else ch }.joinToString("")

private val Forest = Color(0xFF2563EB)
private val Cream = Color(0xFFF8FAFC)
private val Ink = Color(0xFF172033)
private val Teal = Color(0xFF1D4ED8)

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)
private val SahlBitTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge=t.displayLarge.copy(fontFamily=Vazirmatn), displayMedium=t.displayMedium.copy(fontFamily=Vazirmatn), displaySmall=t.displaySmall.copy(fontFamily=Vazirmatn),
        headlineLarge=t.headlineLarge.copy(fontFamily=Vazirmatn), headlineMedium=t.headlineMedium.copy(fontFamily=Vazirmatn), headlineSmall=t.headlineSmall.copy(fontFamily=Vazirmatn),
        titleLarge=t.titleLarge.copy(fontFamily=Vazirmatn), titleMedium=t.titleMedium.copy(fontFamily=Vazirmatn), titleSmall=t.titleSmall.copy(fontFamily=Vazirmatn),
        bodyLarge=t.bodyLarge.copy(fontFamily=Vazirmatn), bodyMedium=t.bodyMedium.copy(fontFamily=Vazirmatn), bodySmall=t.bodySmall.copy(fontFamily=Vazirmatn),
        labelLarge=t.labelLarge.copy(fontFamily=Vazirmatn), labelMedium=t.labelMedium.copy(fontFamily=Vazirmatn), labelSmall=t.labelSmall.copy(fontFamily=Vazirmatn)
    )
}

class MainActivity : ComponentActivity() {
    private val battery = mutableIntStateOf(100)
    private val charging = mutableStateOf(false)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) battery.intValue = (level * 100 / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging.value = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
    }
    private val homeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val prefs = getSharedPreferences("sahlbit_local", MODE_PRIVATE)
        val recoveryPrefs = getSharedPreferences(SahlBitApplication.PREFS_NAME, MODE_PRIVATE)
        val pending = recoveryPrefs.getBoolean(SahlBitApplication.KEY_CRASH_RECOVERY_PENDING, false)
        if (pending) recoveryPrefs.edit().remove(SahlBitApplication.KEY_CRASH_RECOVERY_PENDING).remove(SahlBitApplication.KEY_CRASH_TIME).commit()
        setContent {
            MaterialTheme(typography = SahlBitTypography, colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White, primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A8A), secondary = Teal, onSecondary = Color.White, background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF475569), outline = Color(0xFFE2E8F0), error = Color(0xFFB3261E))) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Vazirmatn)
                ) {
                    SahlBitApp(
                        prefs = prefs,
                        battery = battery.intValue,
                        charging = charging.value,
                        crashRecovery = pending,
                        onSelectLauncher = { selectLauncher() },
                        onSelectDialer = { selectDialer() },
                        onAndroidSettings = { openAndroidSettings() },
                        onImmersiveChange = { enabled -> applyImmersiveMode(enabled) }
                    )
                }
            }
        }
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        @Suppress("DEPRECATION")
        if (enabled) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        } else { window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
    }

    private fun selectLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                if (manager.isRoleHeld(RoleManager.ROLE_HOME)) openHomeSettings()
                else homeRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        openHomeSettings()
    }
    private val dialerRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val telecom = getSystemService(android.telecom.TelecomManager::class.java)
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        android.widget.Toast.makeText(this, if (selected || telecom?.defaultDialerPackage == packageName) "سهل‌بیت به‌عنوان برنامهٔ تلفن پیش‌فرض انتخاب شد." else "برای نمایش رابط تماس سهل‌بیت، باید آن را برنامهٔ تلفن پیش‌فرض انتخاب کنید.", android.widget.Toast.LENGTH_LONG).show()
    }
    private fun selectDialer() { requestDialerRole() }
    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                dialerRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                startActivity(
                    Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                )
            }.onFailure { openAndroidSettings() }
        } else {
            android.widget.Toast.makeText(this, "در اندروید ۵ رابط تماس سهل‌بیت فعال نیست و برنامهٔ تلفن خود گوشی تماس‌ها را مدیریت می‌کند.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    private fun openHomeSettings() { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.onFailure { openAndroidSettings() } }
    private fun openAndroidSettings() { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode(true)
    }
    override fun onDestroy() { runCatching { unregisterReceiver(receiver) }; super.onDestroy() }
}

@Composable
private fun SahlBitApp(
    prefs: android.content.SharedPreferences,
    battery: Int,
    charging: Boolean,
    crashRecovery: Boolean,
    onSelectLauncher: () -> Unit,
    onSelectDialer: () -> Unit,
    onAndroidSettings: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit
) {
    var initialized by remember { mutableStateOf(prefs.getBoolean("initialized", false)) }
    var pinHash by remember { mutableStateOf(prefs.getString("admin_pin_hash", null)) }
    var route by remember { mutableStateOf(if (!initialized || pinHash == null) "welcome" else if (crashRecovery) "recovery" else "home") }
    var pinInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var immersive by remember { mutableStateOf(true) }
    var backgroundChoice by remember { mutableStateOf((prefs.getString("background", "cream") ?: "cream").let { if (it == "forest") "cream" else it }) }
    var settingsTapThreshold by remember { mutableIntStateOf(prefs.getInt("settings_taps_threshold", 7).coerceIn(3, 15)) }
    var pageCount by remember { mutableIntStateOf(prefs.getInt("page_count", 5).coerceIn(1, 12)) }
    var pageNames by remember { mutableStateOf(runCatching { org.json.JSONArray(prefs.getString("page_names", "[]") ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList()).let { saved -> (0 until prefs.getInt("page_count", 5).coerceIn(1, 12)).map { saved.getOrNull(it)?.ifBlank { "صفحه ${(it + 1).toPersianDigits()}" } ?: "صفحه ${(it + 1).toPersianDigits()}" } }) }
    var clockSizeDp by remember { mutableIntStateOf(prefs.getInt("clock_size_dp", 300).coerceIn(180, 360)) }
    var clockDigitSizeSp by remember { mutableIntStateOf(prefs.getInt("clock_digit_size_sp", 40).coerceIn(20, 56)) }
    var clockDigitLanguage by remember { mutableStateOf(prefs.getString("clock_digit_language", "english") ?: "english") }
    var clockHourLength by remember { mutableFloatStateOf(prefs.getFloat("clock_hour_hand_length", 0.48f).coerceIn(0.25f, 0.70f)) }
    var clockMinuteLength by remember { mutableFloatStateOf(prefs.getFloat("clock_minute_hand_length", 0.69f).coerceIn(0.35f, 0.88f)) }
    var clockSecondLength by remember { mutableFloatStateOf(prefs.getFloat("clock_second_hand_length", 0.76f).coerceIn(0.40f, 0.92f)) }
    var showSecondHand by remember { mutableStateOf(prefs.getBoolean("clock_show_seconds", false)) }
    var adminAuthenticated by remember { mutableStateOf(false) }
    var failedPins by remember { mutableIntStateOf(prefs.getInt("pin_failed_attempts", 0)) }
    var lockUntil by remember { mutableLongStateOf(prefs.getLong("pin_lock_until", 0L)) }
    var mathA by remember { mutableIntStateOf(0) }
    var mathB by remember { mutableIntStateOf(0) }
    var mathAnswer by remember { mutableStateOf("") }
    var mathOperation by remember { mutableStateOf("+") }
    var selectedPhotoEntryId by remember { mutableStateOf<String?>(null) }
    var editingContactId by remember { mutableStateOf<String?>(null) }
    var editContactName by remember { mutableStateOf("") }
    var editContactPhone by remember { mutableStateOf("") }
    var oldPinInput by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(LauncherEntryStore.load(prefs)) }
    var selectedPage by remember { mutableIntStateOf(0) }
    var confirmPageDeletion by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableIntStateOf(prefs.getInt("grid_columns", 2).coerceIn(1, 4)) }
    fun loadRowsPerPage(page: Int): Int = runCatching {
        val configured = org.json.JSONObject(prefs.getString("row_counts_per_page", "{}") ?: "{}").opt(page.toString())
        when (configured) {
            is Number -> configured.toInt().coerceIn(1, 6)
            else -> {
                val legacyRows = org.json.JSONObject(prefs.getString("row_layouts", "{}") ?: "{}").optJSONArray(page.toString())
                (legacyRows?.length() ?: prefs.getInt("grid_rows", 3)).coerceIn(1, 6)
            }
        }
    }.getOrDefault(3)
    fun saveRowsPerPage(page: Int, count: Int) {
        val root = runCatching { org.json.JSONObject(prefs.getString("row_counts_per_page", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        root.put(page.toString(), count.coerceIn(1, 6))
        prefs.edit().putString("row_counts_per_page", root.toString()).apply()
    }
    var gridRows by remember(selectedPage) { mutableIntStateOf(loadRowsPerPage(selectedPage)) }
    var gridAlignment by remember { mutableStateOf(prefs.getString("grid_alignment", "center") ?: "center") }
    fun loadRowCounts(page: Int): List<Int> = runCatching {
        val root = org.json.JSONObject(prefs.getString("row_layouts", "{}") ?: "{}")
        val a = root.optJSONArray(page.toString()) ?: org.json.JSONArray()
        (0 until a.length()).map { a.optInt(it, gridColumns).coerceIn(1, 4) }
    }.getOrDefault(emptyList()).ifEmpty { List(loadRowsPerPage(page)) { gridColumns } }
    var rowCardCounts by remember(selectedPage) { mutableStateOf(loadRowCounts(selectedPage).take(gridRows).let { it + List((gridRows - it.size).coerceAtLeast(0)) { gridColumns } }) }
    fun loadRowAlignments(page: Int): List<String> = runCatching {
        val root = org.json.JSONObject(prefs.getString("row_alignments", "{}") ?: "{}")
        when (val saved = root.opt(page.toString())) {
            is org.json.JSONArray -> (0 until saved.length()).map { saved.optString(it, "center") }
            is String -> List(gridRows) { row -> if (row == gridRows - 1) saved else gridAlignment }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
    var rowAlignments by remember(selectedPage) { mutableStateOf(loadRowAlignments(selectedPage).take(gridRows).let { it + List((gridRows - it.size).coerceAtLeast(0)) { "center" } }) }
    fun persistRowLayout() {
        val root = runCatching { org.json.JSONObject(prefs.getString("row_layouts", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        root.put(selectedPage.toString(), org.json.JSONArray(rowCardCounts))
        val aligns = runCatching { org.json.JSONObject(prefs.getString("row_alignments", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        aligns.put(selectedPage.toString(), org.json.JSONArray(rowAlignments))
        prefs.edit().putString("row_layouts", root.toString()).putString("row_alignments", aligns.toString()).apply()
    }
    var screenTimeoutSeconds by remember { mutableIntStateOf(prefs.getInt("screen_timeout_seconds", 60).coerceIn(15, 1800)) }
    var timeoutMinutesText by remember { mutableStateOf((screenTimeoutSeconds / 60).toPersianDigits()) }
    var timeoutSecondsText by remember { mutableStateOf((screenTimeoutSeconds % 60).toPersianDigits()) }
    var photoSourceDialogEntry by remember { mutableStateOf<LauncherEntry?>(null) }
    var pendingCall by remember { mutableStateOf<LauncherEntry?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val callPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (!number.isNullOrBlank()) {
            val callIntent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            runCatching { context.startActivity(callIntent) }.onFailure {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
            }
        }
    }
    var contactPickerPending by remember { mutableStateOf(false) }
    var pendingContactName by remember { mutableStateOf("") }
    var pendingContactLookup by remember { mutableStateOf<String?>(null) }
    var pendingContactPhotoSource by remember { mutableStateOf<String?>(null) }
    var pendingContactOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun addPickedContact(name: String, number: String, phoneUri: String, lookupUri: String?, photoSource: String?) {
        val photo = photoSource?.let { source -> copyContactPhoto(context, source) }
        val item = LauncherEntry(kind = "contact", title = name.ifBlank { "مخاطب" }, page = selectedPage, contactUri = phoneUri, contactLookupUri = lookupUri, phoneNumber = number, photoUri = photo, photoSource = if (photo != null) "contact" else "none", photoSourceUri = photoSource)
        entries = entries + item
        LauncherEntryStore.save(prefs, entries)
    }
    val contactPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { contactCursor ->
                    if (contactCursor.moveToFirst()) {
                        val idColumn = contactCursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameColumn = contactCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val photoColumn = contactCursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                        val contactId = if (idColumn >= 0) contactCursor.getLong(idColumn) else -1L
                        val name = if (nameColumn >= 0) contactCursor.getString(nameColumn).orEmpty() else "مخاطب"
                        val photoSource = if (photoColumn >= 0) contactCursor.getString(photoColumn) else null
                        if (contactId >= 0L) {
                            val lookup = ContactsContract.Contacts.getLookupUri(context.contentResolver, android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId))?.toString()
                            val options = mutableListOf<Pair<String, String>>()
                            context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                                arrayOf(contactId.toString()),
                                null
                            )?.use { phoneCursor ->
                                val idIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                                val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                while (phoneCursor.moveToNext()) {
                                    val number = if (numberIndex >= 0) phoneCursor.getString(numberIndex).orEmpty().trim() else ""
                                    val phoneId = if (idIndex >= 0) phoneCursor.getLong(idIndex) else -1L
                                    if (number.isNotBlank() && phoneId >= 0L) {
                                        val phoneUri = android.content.ContentUris.withAppendedId(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, phoneId).toString()
                                        options.add(number to phoneUri)
                                    }
                                }
                            }
                            val uniqueOptions = options.distinctBy { option -> option.first.filter { it.isDigit() || it == '+' } }
                            when (uniqueOptions.size) {
                                0 -> android.widget.Toast.makeText(context, "برای این مخاطب شمارهٔ تلفنی ثبت نشده است", android.widget.Toast.LENGTH_LONG).show()
                                1 -> addPickedContact(name, uniqueOptions[0].first, uniqueOptions[0].second, lookup, photoSource)
                                else -> {
                                    pendingContactName = name
                                    pendingContactLookup = lookup
                                    pendingContactPhotoSource = photoSource
                                    pendingContactOptions = uniqueOptions
                                }
                            }
                        }
                    }
                } ?: android.widget.Toast.makeText(context, "اطلاعات مخاطب قابل خواندن نیست", android.widget.Toast.LENGTH_LONG).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "خواندن اطلاعات مخاطب ناموفق بود", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val contactsPermission = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && contactPickerPending) contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
        else if (!granted) android.widget.Toast.makeText(context, "برای انتخاب مخاطب، اجازهٔ دسترسی به مخاطبان لازم است", android.widget.Toast.LENGTH_LONG).show()
        contactPickerPending = false
    }
    val galleryPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val targetId = selectedPhotoEntryId
        if (uri != null && targetId != null) {
            val target = java.io.File(context.filesDir, "contact_${java.util.UUID.randomUUID()}.jpg")
            val copied = runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: error("تصویر قابل خواندن نیست")
                input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                check(target.exists() && target.length() > 0L) { "فایل تصویر خالی است" }
                true
            }.getOrDefault(false)
            if (copied) {
                val oldPhoto = entries.firstOrNull { it.id == targetId }?.photoUri
                entries = entries.map { if (it.id == targetId) it.copy(photoUri = Uri.fromFile(target).toString(), photoSource = "gallery", photoSourceUri = null) else it }
                LauncherEntryStore.save(prefs, entries)
                oldPhoto?.takeIf { old -> entries.none { it.photoUri == old } }?.let { old -> deleteOwnedContactPhoto(context, old) }
            } else target.delete()
        }
        selectedPhotoEntryId = null
    }

    var resumeRevision by remember { mutableIntStateOf(0) }
    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeRevision++ }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(resumeRevision) {
        if (resumeRevision > 0) {
            val refreshed = entries.map { entry ->
                if (entry.kind != "contact" || entry.contactUri.isNullOrBlank()) entry
                else runCatching {
                    context.contentResolver.query(Uri.parse(entry.contactUri), arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val currentSourceUri = c.getString(2)
                            val photoChanged = entry.photoSource == "contact" && currentSourceUri != entry.photoSourceUri
                            val freshPhoto = if (entry.photoSource == "gallery" || !photoChanged) entry.photoUri else currentSourceUri?.let { src ->
                                copyContactPhoto(context, src)
                            }
                            entry.copy(title = if (entry.manualEdit) entry.title else (c.getString(0) ?: entry.title), phoneNumber = if (entry.manualEdit) entry.phoneNumber else (c.getString(1) ?: entry.phoneNumber), photoUri = freshPhoto, photoSourceUri = if (entry.photoSource == "contact") currentSourceUri else null)
                        } else entry
                    } ?: entry
                }.getOrDefault(entry)
            }
            if (refreshed != entries) {
                val oldPhotos = entries.mapNotNull { it.photoUri }.toSet()
                entries = refreshed
                LauncherEntryStore.save(prefs, entries)
                oldPhotos.filter { old -> entries.none { it.photoUri == old } }.forEach { deleteOwnedContactPhoto(context, it) }
            }
        }
    }

    LaunchedEffect(immersive) { onImmersiveChange(immersive) }
    BackHandler(enabled = route != "home" && route != "welcome") {
        route = when (route) {
            "dashboard" -> { adminAuthenticated = false; "home" }
            "pinchange", "pinverify" -> "security"
            "mathrecovery" -> "login"
            "login" -> "home"
            "setpin" -> "welcome"
            else -> "dashboard"
        }
    }

    val protectedRoutes = setOf("dashboard", "pages", "contacts", "allapps", "appearance", "clock", "audio", "security", "permissions", "about", "pinchange", "pinverify")
    LaunchedEffect(route, adminAuthenticated) {
        if (route in protectedRoutes && !adminAuthenticated) route = "login"
    }

    when (route) {
        "welcome" -> WelcomeScreen(
            onContinue = { route = "setpin" }
        )
        "setpin" -> PinSetupScreen(newPin, { newPin = it.filter(Char::isDigit).take(12) }, confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, error) {
            if (newPin.length < 4) error = "پین باید حداقل ۴ رقم داشته باشد."
            else if (newPin != confirmPin) error = "دو پین یکسان نیستند."
            else {
                val salt = createSalt()
                val hash = hashPin(newPin, salt)
                prefs.edit().putString("admin_pin_salt", salt).putString("admin_pin_hash", hash)
                    .remove("recovery_salt").remove("recovery_hash")
                    .putBoolean("initialized", true).putInt("pin_failed_attempts", 0).putLong("pin_lock_until", 0L).apply()
                pinHash = hash; initialized = true; route = "dashboard"; adminAuthenticated = true
                newPin = ""; confirmPin = ""; error = ""
            }
        }
        "home" -> HomeScreen(
            battery = battery, charging = charging, background = backgroundChoice,
            onAdminEntry = { pinInput = ""; error = ""; route = "login" },
            pageCount = pageCount, pageNames = pageNames,
            entries = entries, gridColumns = gridColumns, gridRows = gridRows, gridAlignment = gridAlignment, rowCardCounts = rowCardCounts, rowAlignments = rowAlignments,
            onEntryClick = { entry -> if (entry.kind == "contact") pendingCall = entry else entry.packageName?.let { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg)?.let { launchIntent -> context.startActivity(launchIntent) } } } },
            onConfirmCall = { entry ->
                entry.phoneNumber?.takeIf { it.isNotBlank() }?.let { number ->
                    pendingCallNumber = number
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        pendingCallNumber = null
                        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
                        runCatching { context.startActivity(callIntent) }.onFailure {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                        }
                    } else callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                }
                pendingCall = null
            },
            pendingCall = pendingCall, onCancelCall = { pendingCall = null }
        )
        "login" -> PinLoginScreen(pinInput, { pinInput = it.filter(Char::isDigit).take(12) }, error,
            onBack = { pinInput = ""; error = ""; route = "home" },
            onForgot = { mathA = SecureRandom().nextInt(8) + 2; mathB = SecureRandom().nextInt(8) + 2; mathOperation = if (SecureRandom().nextBoolean()) "+" else "−"; mathAnswer = ""; error = ""; route = "mathrecovery" }) {
            val now = System.currentTimeMillis()
            if (now < lockUntil) {
                error = "ورود موقتاً قفل است؛ کمی بعد دوباره تلاش کنید."; pinInput = ""
            } else if (pinHash != null && constantTimeEquals(hashPin(pinInput, prefs.getString("admin_pin_salt", "") ?: ""), pinHash!!)) {
                failedPins = 0; lockUntil = 0L
                prefs.edit().putInt("pin_failed_attempts", 0).putLong("pin_lock_until", 0L).apply()
                error = ""; pinInput = ""; adminAuthenticated = true; route = "dashboard"
            } else {
                failedPins += 1
                if (failedPins >= 5) { lockUntil = now + 30_000L; failedPins = 0 }
                prefs.edit().putInt("pin_failed_attempts", failedPins).putLong("pin_lock_until", lockUntil).apply()
                error = if (lockUntil > now) "۵ تلاش ناموفق؛ ورود برای ۳۰ ثانیه قفل شد." else "پین صحیح نیست."
                pinInput = ""
            }
        }
        "dashboard" -> DashboardScreen(
            onExit = { adminAuthenticated = false; route = "home" },
            onPages = { route = "pages" }, onContacts = { route = "contacts" }, onAllApps = { route = "allapps" },
            onAppearance = { route = "appearance" }, onClock = { route = "clock" }, onSecurity = { route = "security" },
            onPermissions = { route = "permissions" }, onAbout = { route = "about" }, onAudio = { route = "audio" },
            onSelectLauncher = onSelectLauncher, onAndroidSettings = onAndroidSettings
        )
        "audio" -> AudioSettingsPage(prefs = prefs, onBack = { route = "dashboard" })
        "allapps" -> AllAppsScreen(onBack = { route = "dashboard" }, onLaunch = { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } } })
        "clock" -> SettingsPage("سفارشی‌سازی ساعت", "اندازهٔ صفحهٔ ساعت، اندازه و زبان اعداد و نمایش ثانیه‌شمار را تنظیم کنید.", { route = "dashboard" }) {
            Text("اندازهٔ ساعت: ${clockSizeDp.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
            Slider(value = clockSizeDp.toFloat(), onValueChange = { v -> clockSizeDp = v.toInt(); prefs.edit().putInt("clock_size_dp", clockSizeDp).apply() }, valueRange = 180f..360f)
            Text("اندازهٔ اعداد ساعت: ${clockDigitSizeSp.toPersianDigits()} sp", color = Ink, fontWeight = FontWeight.SemiBold)
            Slider(value = clockDigitSizeSp.toFloat(), onValueChange = { v -> clockDigitSizeSp = v.toInt(); prefs.edit().putInt("clock_digit_size_sp", clockDigitSizeSp).apply() }, valueRange = 20f..56f)
            Text("زبان اعداد ساعت", color = Ink, fontWeight = FontWeight.Bold)
            listOf("english" to "انگلیسی (123)", "persian" to "فارسی (۱۲۳)").forEach { (key, label) -> Row(Modifier.fillMaxWidth().clickable { clockDigitLanguage = key; prefs.edit().putString("clock_digit_language", key).apply() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(clockDigitLanguage == key, { clockDigitLanguage = key; prefs.edit().putString("clock_digit_language", key).apply() }); Text(label, color = Ink) } }
            Text("طول عقربهٔ ساعت‌شمار: ${(clockHourLength * 100).toInt().toPersianDigits()}٪", color = Ink, fontWeight = FontWeight.SemiBold)
            Slider(value = clockHourLength, onValueChange = { clockHourLength = it; prefs.edit().putFloat("clock_hour_hand_length", it).apply() }, valueRange = 0.25f..0.70f)
            Text("طول عقربهٔ دقیقه‌شمار: ${(clockMinuteLength * 100).toInt().toPersianDigits()}٪", color = Ink, fontWeight = FontWeight.SemiBold)
            Slider(value = clockMinuteLength, onValueChange = { clockMinuteLength = it; prefs.edit().putFloat("clock_minute_hand_length", it).apply() }, valueRange = 0.35f..0.88f)
            Text("طول عقربهٔ ثانیه‌شمار: ${(clockSecondLength * 100).toInt().toPersianDigits()}٪", color = Ink, fontWeight = FontWeight.SemiBold)
            Slider(value = clockSecondLength, onValueChange = { clockSecondLength = it; prefs.edit().putFloat("clock_second_hand_length", it).apply() }, valueRange = 0.40f..0.92f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("نمایش ثانیه‌شمار", modifier = Modifier.weight(1f), color = Ink); Switch(showSecondHand, { showSecondHand = it; prefs.edit().putBoolean("clock_show_seconds", it).apply() }) }
            Text("پیش‌نمایش ساعت صفحهٔ اصلی", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7)), shape = RoundedCornerShape(16.dp)) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    AnalogClock(Modifier.size(minOf(clockSizeDp, LocalConfiguration.current.screenWidthDp - 64).coerceAtLeast(180).dp), digitLanguage = clockDigitLanguage, digitSizeSp = clockDigitSizeSp, showSecondHand = showSecondHand, hourHandLength = clockHourLength, minuteHandLength = clockMinuteLength, secondHandLength = clockSecondLength)
                }
            }
        }
        "pages" -> SettingsPage("مدیریت صفحه‌ها", "نام‌گذاری، افزودن، حذف و جابه‌جایی صفحه‌ها و تنظیم تعداد و چینش کارت‌ها. با حذف صفحه، کارت‌های آن به صفحهٔ قبلی منتقل می‌شوند؛ اگر صفحهٔ اول باشد، کارت‌ها در صفحهٔ اولِ باقی‌مانده حفظ می‌شوند.", { route = "dashboard" }) {
            PageDropdown("صفحهٔ در حال ویرایش", selectedPage, pageNames) { selectedPage = it }
            NumberSetting("تعداد صفحه‌ها", pageCount, 1, 12) { requested ->
                val next = requested.coerceIn(1, 12)
                if (next < pageCount) {
                    entries = entries.map { e -> if (e.page >= next) e.copy(page = next - 1) else e }
                    remapPageLayoutPreferences(prefs, (0 until next).associateWith { it })
                }
                if (next > pageCount) pageNames = pageNames + (pageCount until next).map { "صفحه ${(it + 1).toPersianDigits()}" }
                pageNames = pageNames.take(next)
                pageCount = next; selectedPage = selectedPage.coerceAtMost(next - 1)
                LauncherEntryStore.save(prefs, entries); prefs.edit().putInt("page_count", next).putString("page_names", org.json.JSONArray(pageNames).toString()).apply()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (pageCount < 12) {
                        val copyItems = entries.filter { it.page == selectedPage }.map { it.copy(id = java.util.UUID.randomUUID().toString(), page = pageCount) }
                        entries = entries + copyItems
                        remapPageLayoutPreferences(prefs, (0 until pageCount).associateWith { it }, selectedPage to pageCount)
                        pageNames = pageNames + "${pageNames[selectedPage]} - کپی"
                        pageCount += 1; selectedPage = pageCount - 1
                        LauncherEntryStore.save(prefs, entries); prefs.edit().putInt("page_count", pageCount).putString("page_names", org.json.JSONArray(pageNames).toString()).apply()
                    }
                }, enabled = pageCount < 12) { Text("تکثیر صفحه") }
                OutlinedButton(onClick = { confirmPageDeletion = true }, enabled = pageCount > 1) { Text("حذف صفحه") }
            }
            Text("نام صفحهٔ انتخاب‌شده", color = Ink, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = pageNames.getOrElse(selectedPage) { "صفحه ${(selectedPage + 1).toPersianDigits()}" }, onValueChange = { value -> pageNames = pageNames.toMutableList().also { it[selectedPage] = value.take(28) }; prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (selectedPage > 0) { val names = pageNames.toMutableList(); val n = names[selectedPage]; names[selectedPage] = names[selectedPage - 1]; names[selectedPage - 1] = n; pageNames = names; entries = entries.map { e -> when (e.page) { selectedPage -> e.copy(page = selectedPage - 1); selectedPage - 1 -> e.copy(page = selectedPage); else -> e } }; remapPageLayoutPreferences(prefs, (0 until pageCount).associateWith { old -> when (old) { selectedPage -> selectedPage - 1; selectedPage - 1 -> selectedPage; else -> old } }); selectedPage--; LauncherEntryStore.save(prefs, entries); prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() } }, enabled = selectedPage > 0) { Text("انتقال صفحه به بالا") }
                OutlinedButton(onClick = { if (selectedPage < pageCount - 1) { val names = pageNames.toMutableList(); val n = names[selectedPage]; names[selectedPage] = names[selectedPage + 1]; names[selectedPage + 1] = n; pageNames = names; entries = entries.map { e -> when (e.page) { selectedPage -> e.copy(page = selectedPage + 1); selectedPage + 1 -> e.copy(page = selectedPage); else -> e } }; remapPageLayoutPreferences(prefs, (0 until pageCount).associateWith { old -> when (old) { selectedPage -> selectedPage + 1; selectedPage + 1 -> selectedPage; else -> old } }); selectedPage++; LauncherEntryStore.save(prefs, entries); prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() } }, enabled = selectedPage < pageCount - 1) { Text("انتقال صفحه به پایین") }
            }
            if (confirmPageDeletion) {
                AlertDialog(
                    onDismissRequest = { confirmPageDeletion = false },
                    title = { Text("حذف صفحهٔ ${(selectedPage + 1).toPersianDigits()}؟") },
                    text = { Text("کارت‌های این صفحه حذف نمی‌شوند و به صفحهٔ قبلی منتقل خواهند شد. اگر صفحهٔ اول را حذف کنید، کارت‌ها در صفحهٔ اولِ باقی‌مانده می‌مانند.") },
                    confirmButton = { Button(onClick = {
                        entries = entries.map { e ->
                            when {
                                selectedPage > 0 && e.page == selectedPage -> e.copy(page = selectedPage - 1)
                                e.page > selectedPage -> e.copy(page = e.page - 1)
                                else -> e
                            }
                        }
                        remapPageLayoutPreferences(prefs, (0 until pageCount).filter { it != selectedPage }.associate { old -> old to if (old > selectedPage) old - 1 else old })
                        pageNames = pageNames.filterIndexed { index, _ -> index != selectedPage }
                        pageCount -= 1; selectedPage = selectedPage.coerceAtMost(pageCount - 1)
                        LauncherEntryStore.save(prefs, entries); prefs.edit().putInt("page_count", pageCount).putString("page_names", org.json.JSONArray(pageNames).toString()).apply()
                        confirmPageDeletion = false
                    }) { Text("حذف قطعی") } },
                    dismissButton = { TextButton(onClick = { confirmPageDeletion = false }) { Text("انصراف") } }
                )
            }
            NumberSetting("تعداد ردیف‌ها", gridRows, 1, 6) { value ->
                gridRows = value; saveRowsPerPage(selectedPage, value)
                rowCardCounts = (rowCardCounts.take(value) + List((value - rowCardCounts.size).coerceAtLeast(0)) { gridColumns })
                rowAlignments = (rowAlignments.take(value) + List((value - rowAlignments.size).coerceAtLeast(0)) { "center" })
                persistRowLayout()
            }
            val selectedPageEntryCount = entries.count { it.page == selectedPage }
            if (selectedPageEntryCount > rowCardCounts.sum()) Text("توجه: این صفحه ${selectedPageEntryCount.toPersianDigits()} مورد دارد، اما ظرفیت چیدمان فعلی ${rowCardCounts.sum().toPersianDigits()} کارت است. برای نمایش همهٔ موارد، تعداد ردیف‌ها یا کارت‌های هر ردیف را افزایش دهید.", color = Color(0xFFB3261E), fontSize = 13.sp)
            Text("تنظیم تعداد کارت و تراز هر ردیف", color = Ink, fontWeight = FontWeight.Bold)
            rowCardCounts.forEachIndexed { row, count ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumberSetting("تعداد کارت در ردیف ${(row + 1).toPersianDigits()}", count, 1, 4) { value -> rowCardCounts = rowCardCounts.toMutableList().also { it[row] = value }; persistRowLayout() }
                        Text("تراز ردیف ${(row + 1).toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
                        listOf("right" to "راست", "center" to "وسط", "left" to "چپ").forEach { (key, label) -> Row(Modifier.fillMaxWidth().clickable { rowAlignments = rowAlignments.toMutableList().also { it[row] = key }; persistRowLayout() }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(rowAlignments.getOrElse(row) { "center" } == key, { rowAlignments = rowAlignments.toMutableList().also { it[row] = key }; persistRowLayout() }); Text(label, color = Ink) } }
                    }
                }
            }
            Text("پیش‌نمایش زندهٔ چیدمان", color = Forest, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    var n = 1
                    rowCardCounts.forEachIndexed { row, count ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = when (rowAlignments.getOrElse(row) { "center" }) { "left" -> Arrangement.Absolute.Left; "right" -> Arrangement.Absolute.Right; else -> Arrangement.Center }) {
                            val previewWidth = when (count) { 1 -> 120.dp; 2 -> 120.dp; 3 -> 90.dp; else -> 70.dp }
                            repeat(count) {
                                Box(Modifier.width(previewWidth).padding(horizontal = 2.dp).height(48.dp).clip(RoundedCornerShape(9.dp)).background(Teal.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text("${(n++).toPersianDigits()}", color = Forest, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
        "contacts" -> SettingsPage("مخاطبان و برنامه‌های صفحه اصلی", "مدیر می‌تواند مخاطب واقعی را از دفترچه تلفن یا برنامه را از فهرست برنامه‌های قابل‌اجرا انتخاب کند. انتخاب‌ها فقط در صفحه اصلی سهل‌بیت نمایش داده می‌شوند.", { route = "dashboard" }) {
            PageDropdown("صفحه‌ای که می‌خواهید ویرایش کنید", selectedPage, pageNames) { selectedPage = it }
            val capacityForSelectedPage = loadRowCounts(selectedPage).sum()
            val selectedCountForPage = entries.count { it.page == selectedPage }
            if (selectedCountForPage > capacityForSelectedPage) Text("توجه: این صفحه ${selectedCountForPage.toPersianDigits()} مورد دارد، اما ظرفیت چیدمان فعلی ${capacityForSelectedPage.toPersianDigits()} مورد است. تعداد ردیف‌ها یا کارت‌های هر ردیف را در بخش مدیریت صفحه‌ها افزایش دهید.", color = Color(0xFFB45309), fontSize = 13.sp)
            Button(onClick = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
                } else {
                    contactPickerPending = true
                    contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب مخاطب از دفترچه تلفن") }
            Spacer(Modifier.height(8.dp))
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val installedApps by produceState(initialValue = emptyList<android.content.pm.ResolveInfo>(), key1 = resumeRevision) {
                value = withContext(Dispatchers.IO) {
                    runCatching { pm.queryIntentActivities(launcherIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() } }.getOrDefault(emptyList())
                }
            }
            var appSearch by remember { mutableStateOf("") }
            OutlinedTextField(value = appSearch, onValueChange = { appSearch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجوی برنامه") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            Text("افزودن برنامه به صفحهٔ انتخاب‌شده", color = Ink, fontWeight = FontWeight.Bold)
            val visibleApps = installedApps.filter { it.loadLabel(pm).toString().contains(appSearch, ignoreCase = true) }
            LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visibleApps, key = { it.activityInfo.packageName }) { resolve ->
                    val pkg = resolve.activityInfo.packageName
                    val label = resolve.loadLabel(pm).toString()
                    val alreadyAdded = entries.any { it.kind == "app" && it.packageName == pkg && it.page == selectedPage }
                    Row(Modifier.fillMaxWidth().clickable {
                        if (!alreadyAdded) {
                            entries = entries + LauncherEntry(kind = "app", title = label, page = selectedPage, packageName = pkg)
                            LauncherEntryStore.save(prefs, entries)
                        }
                    }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Ink, modifier = Modifier.weight(1f))
                        if (alreadyAdded) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "افزوده شده", tint = Color(0xFF168A45))
                            Spacer(Modifier.width(6.dp)); Text("افزوده شده", color = Color(0xFF168A45), fontSize = 12.sp)
                        } else Text("افزودن +", color = Teal)
                    }
                }
            }
            val pageEntries = entries.filter { it.page == selectedPage }
            Spacer(Modifier.height(10.dp)); Text("موارد انتخاب‌شده در ${pageNames.getOrElse(selectedPage) { "صفحه" }}", color = Ink, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pageEntries, key = { it.id }) { entry ->
                    val position = pageEntries.indexOfFirst { it.id == entry.id }
                    val globalPosition = entries.indexOfFirst { it.id == entry.id }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        val samePageIndices = entries.indices.filter { entries[it].page == entry.page }
                        val samePagePosition = samePageIndices.indexOf(globalPosition)
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(38.dp).height(88.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                Text((position + 1).toPersianDigits(), color = Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (entry.kind == "contact") {
                                    Text(entry.title, color = Ink, fontWeight = FontWeight.Bold)
                                    Text(entry.phoneNumber.orEmpty(), color = Color(0xFF64748B), fontSize = 13.sp)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { photoSourceDialogEntry = entry }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("انتخاب عکس", color = Teal) }
                                        Spacer(Modifier.weight(1f))
                                        Box(Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, enabled = samePagePosition > 0) { val m = entries.toMutableList(); val a = samePageIndices[samePagePosition]; val b = samePageIndices[samePagePosition - 1]; val x = m[a]; m[a] = m[b]; m[b] = x; entries = m; LauncherEntryStore.save(prefs, entries) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, "انتقال به بالا", tint = if (samePagePosition > 0) Forest else Color.LightGray) }
                                        Box(Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, enabled = samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) { val m = entries.toMutableList(); val a = samePageIndices[samePagePosition]; val b = samePageIndices[samePagePosition + 1]; val x = m[a]; m[a] = m[b]; m[b] = x; entries = m; LauncherEntryStore.save(prefs, entries) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowDown, "انتقال به پایین", tint = if (samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) Forest else Color.LightGray) }
                                        IconButton(onClick = { val removed = entries.firstOrNull { it.id == entry.id }; entries = entries.filterNot { it.id == entry.id }; LauncherEntryStore.save(prefs, entries); removed?.photoUri?.let { photo -> if (entries.none { it.photoUri == photo }) deleteOwnedContactPhoto(context, photo) } }) { Icon(Icons.Default.Close, "حذف مورد", tint = Color(0xFFB3261E)) }
                                    }
                                } else { Text(entry.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2) }
                            }
                        }
                    }
                }
            }
        }
        "appearance" -> SettingsPage("ظاهر و صفحه اصلی", "رنگ‌ها و پس‌زمینه به‌صورت محلی ذخیره می‌شوند.", { route = "dashboard" }) {
            Text("پس‌زمینه", fontWeight = FontWeight.Bold, color = Ink)
            listOf("cream" to "کِرِم", "white" to "سفید", "gray" to "طوسی", "blue" to "آبی ملایم", "sage" to "سبز مریم‌گلی", "lavender" to "یاسی ملایم", "mint" to "نعنایی", "sand" to "شنی روشن", "rose" to "صورتی آرام").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { backgroundChoice = key; prefs.edit().putString("background", key).apply() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(when (key) { "cream" -> Color(0xFFFFF8E7); "white" -> Color.White; "gray" -> Color(0xFFE6E7E8); "blue" -> Color(0xFFE8F1F5); "sage" -> Color(0xFFE5EDE5); "lavender" -> Color(0xFFEDE7F6); "mint" -> Color(0xFFE2F2EC); "sand" -> Color(0xFFF3EBDD); else -> Color(0xFFF7E7E9) }).border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(9.dp)))
                    Spacer(Modifier.width(10.dp))
                    RadioButton(selected = backgroundChoice == key, onClick = { backgroundChoice = key; prefs.edit().putString("background", key).apply() })
                    Text(label, color = Ink)
                }
            }
            Text("نوار وضعیت و نوار پیمایش اندروید در محیط سهل‌بیت مخفی می‌مانند.", color = Color(0xFF64748B), fontSize = 12.sp)
            NumberSetting("تعداد ضربه برای ورود مدیر", settingsTapThreshold, 3, 15) { value -> settingsTapThreshold = value; prefs.edit().putInt("settings_taps_threshold", value).apply() }
            var showAppNames by remember { mutableStateOf(prefs.getBoolean("show_app_names", false)) }
            var showContactNames by remember { mutableStateOf(prefs.getBoolean("show_contact_names", false)) }
            var numericBattery by remember { mutableStateOf(prefs.getBoolean("numeric_battery", false)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نمایش درصد عددی باتری به‌جای نشانگر گرافیکی", modifier = Modifier.weight(1f), color = Ink)
                Switch(numericBattery, { numericBattery = it; prefs.edit().putBoolean("numeric_battery", it).apply() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نمایش نام برنامه‌ها", modifier = Modifier.weight(1f), color = Ink)
                Switch(showAppNames, { showAppNames = it; prefs.edit().putBoolean("show_app_names", it).apply() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نمایش نام مخاطبان", modifier = Modifier.weight(1f), color = Ink)
                Switch(showContactNames, { showContactNames = it; prefs.edit().putBoolean("show_contact_names", it).apply() })
            }
            Divider()
            var windowBrightness by remember { mutableFloatStateOf(prefs.getFloat("window_brightness", 0.40f).coerceIn(0.05f, 1f)) }
            LaunchedEffect(windowBrightness) {
                (context as? ComponentActivity)?.window?.let { w -> val params = w.attributes; params.screenBrightness = windowBrightness; w.attributes = params }
            }
            Text("روشنایی پنجرهٔ سهل‌بیت", fontWeight = FontWeight.Bold, color = Ink)
            Text("${(windowBrightness * 100).toInt().toPersianDigits()}٪", color = Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("این کنترل فقط روشنایی داخل سهل‌بیت را تغییر می‌دهد و به تنظیم روشنایی کل گوشی دست نمی‌زند.", color = Ink, fontSize = 12.sp)
            Slider(value = windowBrightness, onValueChange = { value ->
                windowBrightness = value
                prefs.edit().putFloat("window_brightness", value).apply()
                (context as? ComponentActivity)?.window?.let { w ->
                    val params = w.attributes
                    params.screenBrightness = value.coerceIn(0.05f, 1f)
                    w.attributes = params
                }
            }, valueRange = 0.05f..1f)
            OutlinedButton(onClick = {
                windowBrightness = 0.40f
                prefs.edit().putFloat("window_brightness", 0.40f).apply()
                (context as? ComponentActivity)?.window?.let { w -> val params = w.attributes; params.screenBrightness = 0.40f; w.attributes = params }
            }, modifier = Modifier.fillMaxWidth()) { Text("بازنشانی روشنایی به ۴۰ درصد") }
            Divider()
            Text("خاموش‌شدن خودکار نمایشگر", fontWeight = FontWeight.Bold, color = Ink)
            Text("زمان را به‌صورت عددی وارد کنید. مقدار پیش‌فرض ۱ دقیقه و ۰ ثانیه است.", color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = timeoutMinutesText, onValueChange = { value ->
                    timeoutMinutesText = value.toPersianDigitsText().filter(Char::isDigit).take(2)
                    screenTimeoutSeconds = ((timeoutMinutesText.toEnglishDigits().toIntOrNull() ?: 0) * 60 + (timeoutSecondsText.toEnglishDigits().toIntOrNull() ?: 0)).coerceIn(15, 1800)
                    prefs.edit().putInt("screen_timeout_seconds", screenTimeoutSeconds).apply()
                }, modifier = Modifier.weight(1f), label = { Text("دقیقه") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = timeoutSecondsText, onValueChange = { value ->
                    val seconds = value.toEnglishDigits().filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0
                    timeoutSecondsText = seconds.toPersianDigits()
                    screenTimeoutSeconds = ((timeoutMinutesText.toEnglishDigits().toIntOrNull() ?: 0) * 60 + seconds).coerceIn(15, 1800)
                    prefs.edit().putInt("screen_timeout_seconds", screenTimeoutSeconds).apply()
                }, modifier = Modifier.weight(1f), label = { Text("ثانیه") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("زمان انتخاب‌شده: ${(screenTimeoutSeconds / 60).toPersianDigits()} دقیقه و ${(screenTimeoutSeconds % 60).toPersianDigits()} ثانیه", color = Teal)
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.System.canWrite(context)) {
                    val applied = runCatching { android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, screenTimeoutSeconds * 1000) }.getOrDefault(false)
                    if (!applied) android.widget.Toast.makeText(context, "تنظیم زمان خاموشی نمایشگر اعمال نشد", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "برای اعمال زمان خاموشی، اجازهٔ تغییر تنظیمات سیستمی لازم است", android.widget.Toast.LENGTH_LONG).show()
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("اعمال زمان خاموشی نمایشگر") }
            Text("برای اعمال این تنظیم، اجازهٔ تغییر تنظیمات سیستمی را در صفحهٔ اندروید فعال کن.", color = Ink, fontSize = 13.sp)
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
                else android.widget.Toast.makeText(context, "در اندروید ۵ این دسترسی از طریق مجوز زمان نصب مدیریت می‌شود.", android.widget.Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("اعطای اجازهٔ تنظیم زمان خاموشی") }
        }
        "security" -> SettingsPage("امنیت مدیر", "برای تغییر رمز ورود مدیر یا بازیابی دسترسی از گزینه‌های زیر استفاده کنید.", { route = "dashboard" }) {
            Button(onClick = { oldPinInput = ""; newPin = ""; confirmPin = ""; error = ""; route = "pinverify" }, modifier = Modifier.fillMaxWidth()) { Text("تغییر پین مدیر") }
        }
        "pinverify" -> SettingsPage("تأیید پین فعلی", "برای تغییر پین، ابتدا رمز فعلی مدیر را وارد کنید.", { route = "security" }) {
            PinField("پین فعلی", oldPinInput) { oldPinInput = it.filter(Char::isDigit).take(12) }
            if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E))
            Button(onClick = {
                val salt = prefs.getString("admin_pin_salt", "") ?: ""
                if (pinHash != null && constantTimeEquals(hashPin(oldPinInput, salt), pinHash!!)) { oldPinInput = ""; error = ""; route = "pinchange" }
                else { oldPinInput = ""; error = "پین فعلی نادرست است." }
            }, modifier = Modifier.fillMaxWidth()) { Text("تأیید و ادامه") }
        }
        "mathrecovery" -> SettingsPage("بازیابی دسترسی مدیر", "برای بازیابی دسترسی، مسئلهٔ زیر را حل کنید.", { route = "login" }) {
            val expected = if (mathOperation == "+") mathA + mathB else mathA - mathB
            Text("${mathA.toPersianDigits()} $mathOperation ${mathB.toPersianDigits()} = ؟", color = Forest, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = mathAnswer, onValueChange = { mathAnswer = it.filter(Char::isDigit).take(3) }, label = { Text("پاسخ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E))
            Button(onClick = {
                if (mathAnswer.toIntOrNull() == expected) { failedPins = 0; lockUntil = 0L; prefs.edit().putInt("pin_failed_attempts", 0).putLong("pin_lock_until", 0L).apply(); adminAuthenticated = true; newPin = ""; confirmPin = ""; error = ""; route = "pinchange" }
                else { error = "پاسخ درست نیست. دوباره تلاش کن."; mathA = SecureRandom().nextInt(8) + 2; mathB = SecureRandom().nextInt(8) + 2; mathAnswer = "" }
            }, modifier = Modifier.fillMaxWidth()) { Text("تأیید و تعیین پین جدید") }
        }
        "pinchange" -> PinSetupScreen(newPin, { newPin = it.filter(Char::isDigit).take(12) }, confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, error) {
            if (newPin.length < 4) error = "پین باید حداقل ۴ رقم داشته باشد."
            else if (newPin != confirmPin) error = "دو پین یکسان نیستند."
            else { val salt = createSalt(); val h = hashPin(newPin, salt); prefs.edit().putString("admin_pin_salt", salt).putString("admin_pin_hash", h).apply(); pinHash = h; route = "dashboard"; error = "" }
        }
        "permissions" -> SettingsPage("مجوزها و نقش‌های برنامه", "برای نمایش رابط تماس اختصاصی، سهل‌بیت باید برنامهٔ تلفن پیش‌فرض باشد. این قابلیت از اندروید ۶ به بعد فعال است؛ در اندروید ۵ برنامهٔ تلفن سیستم تماس‌ها را مدیریت می‌کند.", { route = "dashboard" }) {
            Button(onClick = onSelectLauncher, modifier = Modifier.fillMaxWidth()) { Text("انتخاب سهل‌بیت به‌عنوان صفحهٔ اصلی (Home)") }
            Text("برای نمایش صفحهٔ اصلی سهل‌بیت پس از فشردن دکمهٔ خانه استفاده می‌شود.", color = Color(0xFF64748B), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Button(onClick = onSelectDialer, modifier = Modifier.fillMaxWidth()) { Text("انتخاب سهل‌بیت به‌عنوان برنامهٔ تلفن پیش‌فرض (Dialer)") }
                Text("برای نمایش رابط تماس اختصاصی و مدیریت تماس‌ها در سهل‌بیت لازم است.", color = Color(0xFF64748B), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Text("دسترسی مخاطبان", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("برای خواندن نام کامل، شماره تلفن و تصویر مخاطب از دفترچه تلفن استفاده می‌شود.", color = Color(0xFF64748B), fontSize = 12.sp)
            OutlinedButton(onClick = { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) { Text("اعطای دسترسی مخاطبان") }
            Spacer(Modifier.height(8.dp))
            Text("اجازهٔ برقراری تماس", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("برای تماس مستقیم با مخاطب از صفحهٔ اصلی لازم است؛ در صورت رد اجازه، شماره‌گیر سیستم باز می‌شود.", color = Color(0xFF64748B), fontSize = 12.sp)
            OutlinedButton(onClick = { callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE) }, modifier = Modifier.fillMaxWidth()) { Text("اعطای اجازهٔ تماس تلفنی") }
            Spacer(Modifier.height(8.dp))
            Text("دسترسی آمار مصرف برنامه‌ها", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("برای مرتب‌سازی برنامه‌ها بر اساس زمان استفاده در ۳۰ روز اخیر استفاده می‌شود و محتوای برنامه‌ها را نمی‌خواند.", color = Color(0xFF64748B), fontSize = 12.sp)
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).putExtra("package", context.packageName).putExtra("android.provider.extra.APP_PACKAGE", context.packageName).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)) } }, modifier = Modifier.fillMaxWidth()) { Text("فعال‌سازی دسترسی آمار استفاده") }
            Spacer(Modifier.height(8.dp))
            Text("دسترسی تغییر تنظیمات سیستمی", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("برای انتخاب و ذخیرهٔ زنگ تماس، اعلان و هشدار و همچنین اعمال زمان خاموشی نمایشگر استفاده می‌شود.", color = Color(0xFF64748B), fontSize = 12.sp)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
                else android.widget.Toast.makeText(context, "در اندروید ۵ این دسترسی از طریق مجوز زمان نصب مدیریت می‌شود.", android.widget.Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("اجازهٔ تغییر تنظیمات سیستمی") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAndroidSettings, modifier = Modifier.fillMaxWidth()) { Text("بازکردن تنظیمات اندروید") }
        }
        "about" -> SettingsPage("درباره سهل‌بیت", "SahlBit Launcher\nنسخه ${BuildConfig.VERSION_NAME}\nتوسعه‌دهنده: امیرعلی پاشایی فرد\nوب‌سایت: pashaef.ir\nشماره تماس: ۰۹۱۴۵۸۸۰۸۱۴", { route = "dashboard" }) {
            Text("سهل‌بیت؛ محیطی ساده و قابل‌اعتماد برای دسترسی آسان به مخاطبان و برنامه‌های ضروری.", color = Ink, fontSize = 18.sp, lineHeight = 30.sp)
            Text("تلفن همراه: ۰۹۱۴۵۸۸۰۸۱۴", color = Forest, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        "recovery" -> SettingsPage("بازیابی سهل‌بیت", "اگر سهل‌بیت درست کار نمی‌کند، از دکمه زیر برای انتخاب لانچر جایگزین استفاده کن. سهل‌بیت به‌صورت خودکار لانچر گوشی را تغییر نمی‌دهد.", { route = "home" }) {
            Button(onClick = onSelectLauncher, modifier = Modifier.fillMaxWidth()) { Text("انتخاب لانچر اندروید / One UI Home") }
        }
    }
    if (pendingContactOptions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingContactOptions = emptyList() },
            title = { Text("انتخاب شمارهٔ تماس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("برای ${pendingContactName} چند شماره ثبت شده است. شمارهٔ موردنظر را انتخاب کنید.")
                    pendingContactOptions.forEach { option ->
                        OutlinedButton(onClick = {
                            addPickedContact(pendingContactName, option.first, option.second, pendingContactLookup, pendingContactPhotoSource)
                            pendingContactOptions = emptyList()
                        }, modifier = Modifier.fillMaxWidth()) { Text(option.first) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pendingContactOptions = emptyList() }) { Text("انصراف") } }
        )
    }
    if (photoSourceDialogEntry != null) {
        val photoEntry = photoSourceDialogEntry!!
        AlertDialog(
            onDismissRequest = { photoSourceDialogEntry = null },
            title = { Text("انتخاب تصویر مخاطب") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("روش دریافت تصویر را انتخاب کنید.")
                TextButton(onClick = {
                    val source = photoEntry.photoSourceUri ?: runCatching {
                        context.contentResolver.query(Uri.parse(photoEntry.contactUri ?: ""), arrayOf(ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull()
                    val copied = source?.let { copyContactPhoto(context, it) }
                    if (copied != null) {
                        val old = entries.firstOrNull { it.id == photoEntry.id }?.photoUri
                        entries = entries.map { if (it.id == photoEntry.id) it.copy(photoUri = copied, photoSource = "contact", photoSourceUri = source) else it }
                        LauncherEntryStore.save(prefs, entries)
                        old?.takeIf { entries.none { e -> e.photoUri == old } }?.let { deleteOwnedContactPhoto(context, it) }
                    } else android.widget.Toast.makeText(context, "تصویر مخاطب در دفترچه تلفن موجود نیست", android.widget.Toast.LENGTH_LONG).show()
                    photoSourceDialogEntry = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (photoEntry.photoSource == "contact") "✓ " else "") + "استفاده از عکس برنامهٔ مخاطبین اندروید")
                }
                TextButton(onClick = {
                    selectedPhotoEntryId = photoEntry.id
                    photoSourceDialogEntry = null
                    galleryPicker.launch("image/*")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (photoEntry.photoSource == "gallery") "✓ " else "") + "انتخاب تصویر از گالری")
                }
            } },
            confirmButton = { TextButton(onClick = { photoSourceDialogEntry = null }) { Text("بستن") } }
        )
    }
    if (editingContactId != null) {
            AlertDialog(onDismissRequest = { editingContactId = null }, title = { Text("ویرایش کارت مخاطب") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editContactName, onValueChange = { editContactName = it }, label = { Text("نام مخاطب") }, singleLine = true)
                    OutlinedTextField(value = editContactPhone, onValueChange = { editContactPhone = it }, label = { Text("شماره تلفن") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Text("این تغییرات روی کارت سهل‌بیت ذخیره می‌شوند و دفترچه تلفن اندروید را تغییر نمی‌دهند.", color = Teal, fontSize = 12.sp)
                }
            }, confirmButton = { Button(onClick = {
                entries = entries.map { e -> if (e.id == editingContactId) e.copy(title = editContactName.trim().ifBlank { e.title }, phoneNumber = editContactPhone.trim(), manualEdit = true) else e }
                LauncherEntryStore.save(prefs, entries); editingContactId = null
            }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = { editingContactId = null }) { Text("انصراف") } })
        }
}

@Composable
private fun AllAppsScreen(onBack: () -> Unit, onLaunch: (String) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    fun hasUsageAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
    var usageAccessGranted by remember { mutableStateOf(hasUsageAccess()) }
    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) usageAccessGranted = hasUsageAccess() }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer) }
    }
    val apps by produceState(initialValue = emptyList<android.content.pm.ResolveInfo>(), key1 = usageAccessGranted) {
        value = withContext(Dispatchers.IO) {
            val usageByPackage = if (usageAccessGranted) runCatching {
                val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                manager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000, System.currentTimeMillis())
                    .groupBy { it.packageName }.mapValues { (_, rows) -> rows.sumOf { it.totalTimeInForeground } }
            }.getOrDefault(emptyMap()) else emptyMap()
            runCatching {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0)
                    .distinctBy { it.activityInfo.packageName }
                    .sortedWith(compareByDescending<android.content.pm.ResolveInfo> { (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
                        .thenByDescending { usageByPackage[it.activityInfo.packageName] ?: 0L }
                        .thenBy { it.loadLabel(pm).toString().lowercase() })
            }.getOrDefault(emptyList())
        }
    }
    var search by remember { mutableStateOf("") }
    val filteredApps = apps.filter { it.loadLabel(pm).toString().contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(Cream).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = Forest) }; Text("برنامه‌های تلفن همراه", color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Text("برنامه‌های سیستمی ابتدا نمایش داده می‌شوند؛ سایر برنامه‌ها بر اساس زمان استفاده در ۳۰ روز اخیر مرتب شده‌اند.", color = Ink, modifier = Modifier.padding(bottom = 8.dp))
        if (!usageAccessGranted) {
            Text("برای مرتب‌سازی دقیق بر اساس میزان استفاده، دسترسی آمار مصرف برنامه‌ها را فعال کنید.", color = Teal, fontSize = 13.sp)
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).putExtra("package", context.packageName).putExtra("android.provider.extra.APP_PACKAGE", context.packageName).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)) } }, modifier = Modifier.fillMaxWidth()) { Text("فعال‌سازی دسترسی آمار استفاده") }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, label = { Text("جستجوی برنامه") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "پاک‌کردن جستجو", tint = Forest) } })
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredApps, key = { it.activityInfo.packageName }) { item ->
                val pkg = item.activityInfo.packageName
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Color(0xFFD8C9A8), RoundedCornerShape(14.dp)).clickable { onLaunch(pkg) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.loadLabel(pm).toString(), color = Ink, modifier = Modifier.weight(1f))
                    Text("اجرا", color = Teal, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AudioSettingsPage(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    fun volume(stream: Int) = audio.getStreamVolume(stream)
    fun maxVolume(stream: Int) = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    var ring by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_RING).toFloat()) }
    var voice by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_VOICE_CALL).toFloat()) }
    var media by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    var alarm by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_ALARM).toFloat()) }
    var notification by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_NOTIFICATION).toFloat()) }
    var selectedRingtoneType by remember { mutableIntStateOf(android.media.RingtoneManager.TYPE_RINGTONE) }
    val ringtonePicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.extras?.get(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
        if (picked != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)) {
                runCatching { android.media.RingtoneManager.setActualDefaultRingtoneUri(context, selectedRingtoneType, picked) }
            } else {
                prefs.edit().putString("pending_ringtone_uri", picked.toString()).putInt("pending_ringtone_type", selectedRingtoneType).apply()
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
            }
        }
    }
    val host = context as? ComponentActivity
    DisposableEffect(host) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context))) {
                prefs.getString("pending_ringtone_uri", null)?.let { raw ->
                    val pendingType = prefs.getInt("pending_ringtone_type", android.media.RingtoneManager.TYPE_RINGTONE)
                    runCatching { android.media.RingtoneManager.setActualDefaultRingtoneUri(context, pendingType, Uri.parse(raw)) }
                    prefs.edit().remove("pending_ringtone_uri").remove("pending_ringtone_type").apply()
                }
                ring = volume(android.media.AudioManager.STREAM_RING).toFloat()
            }
        }
        host?.lifecycle?.addObserver(observer)
        onDispose { host?.lifecycle?.removeObserver(observer) }
    }
    fun picker(type: Int, title: String) {
        selectedRingtoneType = type
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.media.RingtoneManager.getActualDefaultRingtoneUri(context, type))
        ringtonePicker.launch(intent)
    }
    SettingsPage("صدا و زنگ‌ها", "بلندی صدای تماس، رسانه، زنگ هشدار و اعلان‌ها را تنظیم کنید. انتخاب زنگ تماس از فهرست زنگ‌های خود گوشی انجام می‌شود.", onBack) {
        Text("صدای زنگ تماس: ${ring.toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        Slider(value = ring, onValueChange = { ring = it; audio.setStreamVolume(android.media.AudioManager.STREAM_RING, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_RING).toFloat())
        Text("صدای مکالمه: ${voice.toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        Slider(value = voice, onValueChange = { voice = it; audio.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_VOICE_CALL).toFloat())
        Text("صدای رسانه: ${media.toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        Slider(value = media, onValueChange = { media = it; audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat())
        Text("صدای زنگ هشدار: ${alarm.toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        Slider(value = alarm, onValueChange = { alarm = it; audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_ALARM).toFloat())
        Text("صدای اعلان‌ها: ${notification.toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        Slider(value = notification, onValueChange = { notification = it; audio.setStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_NOTIFICATION).toFloat())
        OutlinedButton(onClick = { picker(android.media.RingtoneManager.TYPE_RINGTONE, "انتخاب زنگ تماس") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب زنگ تماس") }
        OutlinedButton(onClick = { picker(android.media.RingtoneManager.TYPE_NOTIFICATION, "انتخاب صدای اعلان") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب صدای اعلان") }
        OutlinedButton(onClick = { picker(android.media.RingtoneManager.TYPE_ALARM, "انتخاب زنگ هشدار") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب زنگ هشدار") }
        Text("برای ذخیرهٔ زنگ انتخاب‌شده، ممکن است اجازهٔ تغییر تنظیمات سیستمی لازم باشد.", color = Color(0xFF64748B), fontSize = 12.sp)
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context))
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
            else android.widget.Toast.makeText(context, "اجازهٔ تغییر تنظیمات سیستمی فعال است.", android.widget.Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("مدیریت اجازهٔ انتخاب زنگ") }
    }
}

@Composable private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("به سهل‌بیت خوش آمدید", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(16.dp)); Text("برای شروع، مدیر دستگاه باید پین اختصاصی خود را تعیین کند. تنظیمات و اطلاعات این نسخه روی خود گوشی ذخیره می‌شوند.", color = Ink, fontSize = 18.sp, lineHeight = 29.sp)
        Spacer(Modifier.height(28.dp)); Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("شروع راه‌اندازی", fontSize = 18.sp) }
    }
}

@Composable private fun PinSetupScreen(pin: String, onPin: (String) -> Unit, confirm: String, onConfirm: (String) -> Unit, error: String, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تنظیم پین مدیر", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(12.dp)); Text("یک پین عددی با حداقل ۴ رقم انتخاب کن. فقط مدیر باید آن را بداند.", color = Ink, fontSize = 17.sp)
        Spacer(Modifier.height(18.dp)); PinField("پین جدید", pin, onPin); Spacer(Modifier.height(12.dp)); PinField("تکرار پین", confirm, onConfirm)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp)); Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ذخیره پین") }
    }
}
@Composable private fun PinLoginScreen(pin: String, onPin: (String) -> Unit, error: String, onBack: () -> Unit, onForgot: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Close, contentDescription = "بازگشت به صفحه اصلی", tint = Color(0xFFDC2626), modifier = Modifier.size(34.dp)) }
        Text("ورود مدیر", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(14.dp)); PinField("پین مدیر", pin, onPin)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("ورود به پیشخان مدیریت") }
        TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("پین را فراموش کرده‌ام") }
    }
}
@Composable private fun PinField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable private fun DashboardScreen(onExit: () -> Unit, onPages: () -> Unit, onContacts: () -> Unit, onAllApps: () -> Unit, onAppearance: () -> Unit, onClock: () -> Unit, onSecurity: () -> Unit, onPermissions: () -> Unit, onAbout: () -> Unit, onAudio: () -> Unit, onSelectLauncher: () -> Unit, onAndroidSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("مدیریت سهل‌بیت", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Button(onClick = onExit, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(6.dp)); Text("بازگشت به صفحه اصلی") } }
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DashboardCard("مدیریت صفحه‌ها و چیدمان", "تعداد صفحه‌ها و تعداد کارت‌ها", "pages", onPages) }
            item { DashboardCard("مدیریت مخاطبان و برنامه‌ها", "انتخاب موارد مجاز برای صفحه اصلی", "contacts", onContacts) }
            item { DashboardCard("برنامه‌های تلفن همراه", "مشاهده و اجرای برنامه‌های نصب‌شده", "apps", onAllApps) }
            item { DashboardCard("ظاهر و صفحه اصلی", "پس‌زمینه، رنگ‌ها و نوارهای سیستم", "appearance", onAppearance) }
            item { DashboardCard("سفارشی‌سازی ساعت", "اندازه، زبان اعداد و ثانیه‌شمار", "clock", onClock) }
            item { DashboardCard("صدا و زنگ‌ها", "تنظیم بلندی صدا و انتخاب زنگ‌ها", "audio", onAudio) }
            item { DashboardCard("امنیت و پین مدیر", "تغییر رمز ورود مدیر", "security", onSecurity) }
            item { DashboardCard("مجوزها و دسترسی‌ها", "بررسی دسترسی‌های اندروید", "permissions", onPermissions) }
            item { DashboardCard("انتخاب لانچر پیش‌فرض", "مدیریت لانچر اصلی اندروید", "home", onSelectLauncher) }
            item { DashboardCard("تنظیمات اندروید", "بازکردن تنظیمات سیستم", "settings", onAndroidSettings) }
            item { DashboardCard("درباره سهل‌بیت", "نسخه، توسعه‌دهنده و راه‌های ارتباطی", "about", onAbout) }
        }
    }
}
@Composable private fun DashboardCard(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            val categoryIcon = when (icon) {
                "contacts" -> Icons.Default.Person
                "apps" -> Icons.Default.Search
                "audio" -> Icons.Default.Call
                "about" -> Icons.Default.Info
                else -> Icons.Default.Settings
            }
            Icon(categoryIcon, contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = Color(0xFF64736B), fontSize = 13.sp) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowBack, null, tint = Teal)
        }
    }
}
@Composable private fun SettingsPage(title: String, description: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = Forest) }; Text(title, color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp)); Text(description, color = Ink, fontSize = if (title == "درباره سهل‌بیت") 19.sp else 16.sp, lineHeight = if (title == "درباره سهل‌بیت") 30.sp else 24.sp); Spacer(Modifier.height(18.dp)); content()
    }
}
@Composable private fun PageDropdown(label: String, selectedPage: Int, pageNames: List<String>, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val safeNames = pageNames.ifEmpty { listOf("صفحهٔ اول") }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Ink, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(safeNames.getOrElse(selectedPage) { "صفحهٔ ${(selectedPage + 1).toPersianDigits()}" }, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                safeNames.forEachIndexed { index, name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onChange(index); expanded = false })
                }
            }
        }
    }
}
@Composable private fun NumberSetting(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) { Text(label, color = Ink, fontWeight = FontWeight.SemiBold); Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−", fontSize = 24.sp, color = Forest) }; Text(value.toPersianDigits(), fontSize = 22.sp, color = Forest); IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Icon(Icons.Default.Add, null) } } } }
}

@Composable private fun HomeScreen(battery: Int, charging: Boolean, background: String, onAdminEntry: () -> Unit, pageCount: Int, entries: List<LauncherEntry>, gridColumns: Int, gridRows: Int, rowCardCounts: List<Int>, rowAlignments: List<String>, onEntryClick: (LauncherEntry) -> Unit, onConfirmCall: (LauncherEntry) -> Unit, pendingCall: LauncherEntry?, onCancelCall: () -> Unit, gridAlignment: String, pageNames: List<String>) {
    var settingsTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(settingsTaps) { if (settingsTaps > 0) { delay(1500); settingsTaps = 0 } }
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val contentWidth = (screenWidth - 32.dp).coerceAtLeast(240.dp)
    val launcherPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
    val adminTapThreshold = launcherPrefs.getInt("settings_taps_threshold", 7).coerceIn(3, 15)
    val showAppNames = launcherPrefs.getBoolean("show_app_names", false)
    val showContactNames = launcherPrefs.getBoolean("show_contact_names", false)
    val bg = when (background) { "white" -> Color.White; "cream" -> Color(0xFFFFF8E7); "gray" -> Color(0xFFE6E7E8); "blue" -> Color(0xFFE8F1F5); "sage" -> Color(0xFFE5EDE5); "lavender" -> Color(0xFFEDE7F6); "mint" -> Color(0xFFE2F2EC); "sand" -> Color(0xFFF3EBDD); "rose" -> Color(0xFFF7E7E9); else -> Color(0xFFFFF8E7) }
    val fg = if (background == "forest") Color.White else Ink
    val day = LocalDate.now().dayOfWeek.value // Monday=1 ... Sunday=7
    val dayText = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")[day - 1]
    val marks = when (day) { 6 -> "◯"; 7 -> "│"; 1 -> "│  │"; 2 -> "│  │  │"; 3 -> "│  │  │  │"; 4 -> "│  │  │  │  │"; else -> "━━━━━━━━" }
    val batteryColor = when { battery >= 81 -> Color(0xFF087A35); battery >= 51 -> Color(0xFF36A653); battery >= 31 -> Color(0xFF9DBB35); battery >= 21 -> Color(0xFFE2B72D); else -> Color(0xFFD32F2F) }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 12.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { settingsTaps++; if (settingsTaps >= adminTapThreshold) { settingsTaps = 0; onAdminEntry() } }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, "ورود مدیر", tint = fg.copy(alpha = .48f), modifier = Modifier.size(21.dp))
            }
        }
        val clockPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
        val clockSize = clockPrefs.getInt("clock_size_dp", 300).coerceIn(180, 360)
        val effectiveClockSize = minOf(clockSize, (screenWidth.value.toInt() - 32).coerceAtLeast(120))
        val clockDigits = clockPrefs.getInt("clock_digit_size_sp", 40).coerceIn(20, 56)
        val clockLanguage = clockPrefs.getString("clock_digit_language", "english") ?: "english"
        val secondsVisible = clockPrefs.getBoolean("clock_show_seconds", false)
        val hourLength = clockPrefs.getFloat("clock_hour_hand_length", 0.48f)
        val minuteLength = clockPrefs.getFloat("clock_minute_hand_length", 0.69f)
        val secondLength = clockPrefs.getFloat("clock_second_hand_length", 0.76f)
        AnalogClock(Modifier.size(effectiveClockSize.dp), digitLanguage = clockLanguage, digitSizeSp = clockDigits, showSecondHand = secondsVisible, hourHandLength = hourLength, minuteHandLength = minuteLength, secondHandLength = secondLength)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val numericBattery = context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE).getBoolean("numeric_battery", false)
            if (numericBattery) {
                Text("${battery.toPersianDigits()}٪", color = batteryColor, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            } else {
                Box(Modifier.width(128.dp).height(48.dp).border(2.dp, batteryColor, RoundedCornerShape(9.dp)).padding(4.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth(battery.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(batteryColor))
                }
                Box(Modifier.padding(start = 2.dp).width(8.dp).height(20.dp).background(batteryColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
            }
            if (charging) {
                Spacer(Modifier.width(12.dp))
                Text("⚡", color = Color(0xFFFFB300), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(2.dp)); Text("$dayText   $marks", color = fg, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(1.dp))
        val pager = rememberPagerState(pageCount = { pageCount.coerceIn(1, 12) })
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val layoutPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
                val rowRoot = remember(layoutPrefs.getString("row_layouts", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_layouts", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val rowCountRoot = remember(layoutPrefs.getString("row_counts_per_page", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_counts_per_page", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val alignRoot = remember(layoutPrefs.getString("row_alignments", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_alignments", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val storedRows = rowRoot.optJSONArray(index.toString())
                val fallbackRows = rowCountRoot.optInt(index.toString(), storedRows?.length() ?: layoutPrefs.getInt("grid_rows", 3)).coerceIn(1, 6)
                val pageRowCounts = (0 until fallbackRows).map { row -> (storedRows?.optInt(row, gridColumns) ?: gridColumns).coerceIn(1, 4) }
                val savedAlignments = alignRoot.opt(index.toString())
                val pageRowAlignments = when (savedAlignments) { is org.json.JSONArray -> (0 until pageRowCounts.size).map { savedAlignments.optString(it, "center") }; is String -> List(pageRowCounts.size) { savedAlignments }; else -> List(pageRowCounts.size) { gridAlignment } }
                val pageEntries = entries.filter { it.page == index }
                if (pageEntries.isEmpty()) {
                    Text("هنوز موردی به این صفحه اضافه نشده است", color = fg, fontSize = 15.sp)
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var entryOffset = 0
                        pageRowCounts.forEachIndexed { rowIndex, rowCount ->
                            val rowEntries = pageEntries.drop(entryOffset).take(rowCount)
                            entryOffset += rowCount
                            if (rowEntries.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = when (pageRowAlignments.getOrElse(rowIndex) { "center" }) { "left" -> Arrangement.Absolute.Left; "right" -> Arrangement.Absolute.Right; else -> Arrangement.Center }) {
                                val itemFraction = when (rowEntries.size) { 1 -> 0.44f; 2 -> 0.42f; 3 -> 0.30f; else -> 0.25f }
                                val itemMaxWidth = when (rowEntries.size) { 1 -> 180.dp; 2 -> 165.dp; 3 -> 120.dp; else -> 95.dp }
                                val itemWidth = minOf(contentWidth * itemFraction, itemMaxWidth)
                                rowEntries.forEach { entry ->
                                    Box(Modifier.width(itemWidth).padding(horizontal = 4.dp)) {
                            Card(onClick = { onEntryClick(entry) },
                                colors = CardDefaults.cardColors(containerColor = if (background == "forest") Color.White.copy(alpha = .16f) else Color.White), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val photo = entry.photoUri
                                    val bitmap = remember(photo) { photo?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use { input -> BitmapFactory.decodeStream(input) }?.asImageBitmap() }.getOrNull() } }
                                    val itemPhotoSize = when { rowEntries.size <= 2 -> 104.dp; rowEntries.size == 3 -> 88.dp; else -> 72.dp }
                                    if (bitmap != null) Image(bitmap, contentDescription = entry.title, modifier = Modifier.size(itemPhotoSize).clip(RoundedCornerShape(14.dp)))
                                    else if (entry.kind == "app") {
                                        val iconBitmap = remember(entry.packageName) { entry.packageName?.let { pkg -> runCatching {
                                            val drawable = context.packageManager.getApplicationIcon(pkg)
                                            val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(bmp); drawable.setBounds(0, 0, 96, 96); drawable.draw(canvas); bmp.asImageBitmap()
                                        }.getOrNull() } }
                                        if (iconBitmap != null) Image(iconBitmap, contentDescription = entry.title, modifier = Modifier.size(if (rowEntries.size <= 2) 76.dp else 60.dp))
                                        else Icon(Icons.Default.Settings, null, tint = Teal, modifier = Modifier.size(54.dp))
                                    }
                                     else Icon(Icons.Default.Person, null, tint = if (background == "forest") Color.White else Teal, modifier = Modifier.size(if (rowEntries.size <= 2) 76.dp else 54.dp))
                                    if ((entry.kind == "app" && showAppNames) || (entry.kind == "contact" && showContactNames)) {
                                        Text(entry.title, color = if (background == "forest") Color.White else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    }
                                }
                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(pageCount.coerceIn(1, 12)) { index ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (pager.currentPage == index) 9.dp else 7.dp).clip(CircleShape).background(if (pager.currentPage == index) fg else fg.copy(alpha = .28f)))
            }
        }
        if (pendingCall != null) {
            AlertDialog(onDismissRequest = onCancelCall, title = {
                val avatar = remember(pendingCall.photoUri) { pendingCall.photoUri?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } }.getOrNull() } }
                if (avatar != null) Image(avatar, contentDescription = pendingCall.title, modifier = Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)))
                else Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(72.dp), tint = Teal)
            }, text = { Text("${pendingCall.title}\n${pendingCall.phoneNumber ?: ""}\nآیا می‌خواهید تماس برقرار شود؟", color = Ink) }, confirmButton = { Button(onClick = { onConfirmCall(pendingCall) }, modifier = Modifier.height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF168A45))) { Icon(Icons.Default.Call, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f)); Spacer(Modifier.width(6.dp)); Text("بله، تماس بگیر", fontSize = 17.sp) } }, dismissButton = { Button(onClick = onCancelCall, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("خیر") } })
        }
    }
}


private fun remapPageLayoutPreferences(
    prefs: android.content.SharedPreferences,
    mapping: Map<Int, Int>,
    duplicate: Pair<Int, Int>? = null
) {
    listOf("row_counts_per_page", "row_layouts", "row_alignments").forEach { key ->
        val old = runCatching { org.json.JSONObject(prefs.getString(key, "{}") ?: "{}") }
            .getOrDefault(org.json.JSONObject())
        val updated = org.json.JSONObject()
        mapping.forEach { (oldPage, newPage) ->
            if (old.has(oldPage.toString())) updated.put(newPage.toString(), old.opt(oldPage.toString()))
        }
        duplicate?.let { (sourcePage, targetPage) ->
            if (old.has(sourcePage.toString())) updated.put(targetPage.toString(), old.opt(sourcePage.toString()))
        }
        prefs.edit().putString(key, updated.toString()).apply()
    }
}

private fun createSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
private fun hashPin(pin: String, saltHex: String): String {
    val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }.also { spec.clearPassword() }
}
private fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

private fun copyContactPhoto(context: Context, sourceUri: String): String? {
    val target = java.io.File(context.filesDir, "contact_${java.util.UUID.randomUUID()}.jpg")
    var copied = false
    return try {
        val input = context.contentResolver.openInputStream(Uri.parse(sourceUri)) ?: return null
        input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
        if (target.exists() && target.length() > 0L) {
            copied = true
            Uri.fromFile(target).toString()
        } else null
    } catch (_: Exception) {
        null
    } finally {
        if (!copied) target.delete()
    }
}

private fun deleteOwnedContactPhoto(context: Context, photo: String) {
    runCatching {
        val uri = Uri.parse(photo)
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            val base = context.filesDir.canonicalFile
            if (file.canonicalFile.parentFile == base && file.name.startsWith("contact_")) file.delete()
        }
    }
}
