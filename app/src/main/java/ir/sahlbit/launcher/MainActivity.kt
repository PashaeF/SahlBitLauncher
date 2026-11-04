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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
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
import ir.sahlbit.launcher.ui.AnalogClock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.time.LocalDate
import kotlinx.coroutines.delay


private fun Int.toPersianDigits(): String = toString().map { ch ->
    if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
}.joinToString("" )

private val Forest = Color(0xFF2563EB)
private val Cream = Color(0xFFF8FAFC)
private val Ink = Color(0xFF172033)
private val Teal = Color(0xFF1D4ED8)

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

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
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White, primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A8A), secondary = Teal, onSecondary = Color.White, background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF475569), outline = Color(0xFFE2E8F0), error = Color(0xFFB3261E))) {
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
    private val dialerRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private fun selectDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                if (!manager.isRoleHeld(RoleManager.ROLE_DIALER)) dialerRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                else openAndroidSettings()
                return
            }
        }
        openAndroidSettings()
    }
    private fun openHomeSettings() { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.onFailure { openAndroidSettings() } }
    private fun openAndroidSettings() { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
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
    var immersive by remember { mutableStateOf(prefs.getBoolean("immersive", true)) }
    var backgroundChoice by remember { mutableStateOf(prefs.getString("background", "forest") ?: "forest") }
    var settingsTapThreshold by remember { mutableIntStateOf(prefs.getInt("settings_taps_threshold", 7).coerceIn(3, 15)) }
    var pageCount by remember { mutableIntStateOf(prefs.getInt("page_count", 5).coerceIn(1, 12)) }
    var pageNames by remember { mutableStateOf(runCatching { org.json.JSONArray(prefs.getString("page_names", "[]") ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList()).let { saved -> (0 until prefs.getInt("page_count", 5).coerceIn(1, 12)).map { saved.getOrNull(it)?.ifBlank { "صفحه ${(it + 1).toPersianDigits()}" } ?: "صفحه ${(it + 1).toPersianDigits()}" } }) }
    var clockSizeDp by remember { mutableIntStateOf(prefs.getInt("clock_size_dp", 300).coerceIn(180, 360)) }
    var clockDigitSizeSp by remember { mutableIntStateOf(prefs.getInt("clock_digit_size_sp", 40).coerceIn(20, 56)) }
    var clockDigitLanguage by remember { mutableStateOf(prefs.getString("clock_digit_language", "english") ?: "english") }
    var showSecondHand by remember { mutableStateOf(prefs.getBoolean("clock_show_seconds", true)) }
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
        org.json.JSONObject(prefs.getString("row_counts_per_page", "{}") ?: "{}").optInt(page.toString(), prefs.getInt("grid_rows", 3)).coerceIn(1, 6)
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
            is String -> List(gridRows) { saved }
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
    var pendingCall by remember { mutableStateOf<LauncherEntry?>(null) }
    val context = LocalContext.current
    var contactPickerPending by remember { mutableStateOf(false) }
    var pendingContactName by remember { mutableStateOf("") }
    var pendingContactLookup by remember { mutableStateOf<String?>(null) }
    var pendingContactPhotoSource by remember { mutableStateOf<String?>(null) }
    var pendingContactOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun addPickedContact(name: String, number: String, phoneUri: String, lookupUri: String?, photoSource: String?) {
        val photo = photoSource?.let { source -> runCatching {
            val target = java.io.File(context.filesDir, "contact_${java.util.UUID.randomUUID()}.jpg")
            val input = context.contentResolver.openInputStream(Uri.parse(source)) ?: error("عکس مخاطب در دسترس نیست")
            input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
            if (target.exists() && target.length() > 0L) Uri.fromFile(target).toString() else { target.delete(); null }
        }.getOrNull() }
        val item = LauncherEntry(kind = "contact", title = name.ifBlank { "مخاطب" }, page = selectedPage, contactUri = phoneUri, contactLookupUri = lookupUri, phoneNumber = number, photoUri = photo, photoSource = "contact", photoSourceUri = photoSource)
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
                            when (options.size) {
                                0 -> android.widget.Toast.makeText(context, "برای این مخاطب شمارهٔ تلفنی ثبت نشده است", android.widget.Toast.LENGTH_LONG).show()
                                1 -> addPickedContact(name, options[0].first, options[0].second, lookup, photoSource)
                                else -> {
                                    pendingContactName = name
                                    pendingContactLookup = lookup
                                    pendingContactPhotoSource = photoSource
                                    pendingContactOptions = options
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
                                runCatching {
                                    val file = java.io.File(context.filesDir, "contact_${java.util.UUID.randomUUID()}.jpg")
                                    val input = context.contentResolver.openInputStream(Uri.parse(src)) ?: error("عکس مخاطب در دسترس نیست")
                                    input.use { source -> file.outputStream().use { output -> source.copyTo(output) } }
                                    if (file.exists() && file.length() > 0) Uri.fromFile(file).toString() else { file.delete(); null }
                                }.getOrNull()
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

    val protectedRoutes = setOf("dashboard", "pages", "contacts", "allapps", "appearance", "clock", "security", "permissions", "about", "pinchange", "pinverify")
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
            onReorder = { fromId, toId ->
                val from = entries.indexOfFirst { it.id == fromId }; val to = entries.indexOfFirst { it.id == toId }
                if (from >= 0 && to >= 0 && entries[from].page == entries[to].page) {
                    val mutable = entries.toMutableList(); val moving = mutable.removeAt(from); mutable.add(to.coerceIn(0, mutable.size), moving)
                    entries = mutable; LauncherEntryStore.save(prefs, entries)
                }
            },
            onEntryClick = { entry -> if (entry.kind == "contact") pendingCall = entry else entry.packageName?.let { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg)?.let { launchIntent -> context.startActivity(launchIntent) } } } },
            onConfirmCall = { entry -> entry.phoneNumber?.let { number -> runCatching { context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))) }.onFailure { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) } }; pendingCall = null },
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
            onPermissions = { route = "permissions" }, onAbout = { route = "about" },
            onSelectLauncher = onSelectLauncher, onAndroidSettings = onAndroidSettings
        )
        "allapps" -> AllAppsScreen(onBack = { route = "dashboard" }, onLaunch = { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } } })
        "clock" -> SettingsPage("سفارشی‌سازی ساعت", "اندازهٔ صفحهٔ ساعت، اندازه و زبان اعداد و نمایش ثانیه‌شمار را تنظیم کنید.", { route = "dashboard" }) {
            NumberSetting("اندازهٔ ساعت (dp)", clockSizeDp, 180, 360) { v -> clockSizeDp = v; prefs.edit().putInt("clock_size_dp", v).apply() }
            NumberSetting("اندازهٔ اعداد ساعت (sp)", clockDigitSizeSp, 20, 56) { v -> clockDigitSizeSp = v; prefs.edit().putInt("clock_digit_size_sp", v).apply() }
            Text("زبان اعداد ساعت", color = Ink, fontWeight = FontWeight.Bold)
            listOf("english" to "انگلیسی (123)", "persian" to "فارسی (۱۲۳)").forEach { (key, label) -> Row(Modifier.fillMaxWidth().clickable { clockDigitLanguage = key; prefs.edit().putString("clock_digit_language", key).apply() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(clockDigitLanguage == key, { clockDigitLanguage = key; prefs.edit().putString("clock_digit_language", key).apply() }); Text(label, color = Ink) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("نمایش ثانیه‌شمار", modifier = Modifier.weight(1f), color = Ink); Switch(showSecondHand, { showSecondHand = it; prefs.edit().putBoolean("clock_show_seconds", it).apply() }) }
        }
        "pages" -> SettingsPage("مدیریت صفحات", "نام‌گذاری، افزودن، حذف و جابه‌جایی صفحات و تنظیم تعداد و چینش کارت‌ها. با حذف صفحه، کارت‌های آن به صفحهٔ قبلی منتقل می‌شوند؛ اگر صفحهٔ اول باشد، کارت‌ها در صفحهٔ اولِ باقی‌مانده حفظ می‌شوند.", { route = "dashboard" }) {
            PageDropdown("صفحهٔ در حال ویرایش", selectedPage, pageNames) { selectedPage = it }
            NumberSetting("تعداد صفحات", pageCount, 1, 12) { requested ->
                val next = requested.coerceIn(1, 12)
                if (next < pageCount) entries = entries.map { e -> if (e.page >= next) e.copy(page = next - 1) else e }
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
                OutlinedButton(onClick = { if (selectedPage > 0) { val names = pageNames.toMutableList(); val n = names[selectedPage]; names[selectedPage] = names[selectedPage - 1]; names[selectedPage - 1] = n; pageNames = names; entries = entries.map { e -> when (e.page) { selectedPage -> e.copy(page = selectedPage - 1); selectedPage - 1 -> e.copy(page = selectedPage); else -> e } }; selectedPage--; LauncherEntryStore.save(prefs, entries); prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() } }, enabled = selectedPage > 0) { Text("انتقال صفحه به بالا") }
                OutlinedButton(onClick = { if (selectedPage < pageCount - 1) { val names = pageNames.toMutableList(); val n = names[selectedPage]; names[selectedPage] = names[selectedPage + 1]; names[selectedPage + 1] = n; pageNames = names; entries = entries.map { e -> when (e.page) { selectedPage -> e.copy(page = selectedPage + 1); selectedPage + 1 -> e.copy(page = selectedPage); else -> e } }; selectedPage++; LauncherEntryStore.save(prefs, entries); prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() } }, enabled = selectedPage < pageCount - 1) { Text("انتقال صفحه به پایین") }
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = when (rowAlignments.getOrElse(row) { "center" }) { "left" -> Arrangement.Start; "right" -> Arrangement.End; else -> Arrangement.Center }) {
                            repeat(count) {
                                Box(Modifier.weight(1f).padding(horizontal = 2.dp).height(48.dp).clip(RoundedCornerShape(9.dp)).background(Teal.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text("${(n++).toPersianDigits()}", color = Forest, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
        "contacts" -> SettingsPage("مخاطبان و برنامه‌های صفحه اصلی", "مدیر می‌تواند مخاطب واقعی را از دفترچه تلفن یا برنامه را از فهرست برنامه‌های قابل‌اجرا انتخاب کند. انتخاب‌ها فقط در صفحه اصلی سهل‌بیت نمایش داده می‌شوند.", { route = "dashboard" }) {
            PageDropdown("صفحهٔ مقصد برای موارد جدید (مخاطب یا برنامه)", selectedPage, pageNames) { selectedPage = it }
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
            val installedApps = remember(route, resumeRevision) { runCatching { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() } }.getOrDefault(emptyList()) }
            Text("افزودن برنامه به صفحه اصلی", color = Ink, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(installedApps, key = { it.activityInfo.packageName }) { resolve ->
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
            Spacer(Modifier.height(10.dp)); Text("موارد انتخاب‌شده", color = Ink, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    val position = entries.indexOfFirst { it.id == entry.id }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((position + 1).toPersianDigits(), color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (entry.kind == "contact" && !entry.phoneNumber.isNullOrBlank()) Text(entry.phoneNumber!!, color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                            if (entry.kind == "contact") TextButton(onClick = { selectedPhotoEntryId = entry.id; galleryPicker.launch("image/*") }) { Text("عکس گالری") }
                            val samePageIndices = entries.indices.filter { entries[it].page == entry.page }
                            val samePagePosition = samePageIndices.indexOf(position)
                            IconButton(enabled = samePagePosition > 0, onClick = {
                                if (samePagePosition > 0) {
                                    val mutable = entries.toMutableList()
                                    val from = samePageIndices[samePagePosition]
                                    val to = samePageIndices[samePagePosition - 1]
                                    val item = mutable[from]; mutable[from] = mutable[to]; mutable[to] = item
                                    entries = mutable; LauncherEntryStore.save(prefs, entries)
                                }
                            }) { Text("↑", color = if (samePagePosition > 0) Forest else Color.LightGray, fontSize = 22.sp) }
                            IconButton(enabled = samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex, onClick = {
                                if (samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) {
                                    val mutable = entries.toMutableList()
                                    val from = samePageIndices[samePagePosition]
                                    val to = samePageIndices[samePagePosition + 1]
                                    val item = mutable[from]; mutable[from] = mutable[to]; mutable[to] = item
                                    entries = mutable; LauncherEntryStore.save(prefs, entries)
                                }
                            }) { Text("↓", color = if (samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) Forest else Color.LightGray, fontSize = 22.sp) }
                            IconButton(onClick = {
                                val removed = entries.firstOrNull { it.id == entry.id }
                                entries = entries.filterNot { it.id == entry.id }
                                LauncherEntryStore.save(prefs, entries)
                                removed?.photoUri?.let { photo -> if (entries.none { it.photoUri == photo }) deleteOwnedContactPhoto(context, photo) }
                            }) { Icon(Icons.Default.Close, "حذف مورد", tint = Color(0xFFB3261E)) }
                        }
                    }
                }
            }
        }
        "appearance" -> SettingsPage("ظاهر و صفحه اصلی", "رنگ‌ها و پس‌زمینه به‌صورت محلی ذخیره می‌شوند.", { route = "dashboard" }) {
            Text("پس‌زمینه", fontWeight = FontWeight.Bold, color = Ink)
            listOf("forest" to "آبی پررنگ", "white" to "سفید", "cream" to "کِرِم", "gray" to "طوسی", "blue" to "آبی ملایم").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { backgroundChoice = key; prefs.edit().putString("background", key).apply() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = backgroundChoice == key, onClick = { backgroundChoice = key; prefs.edit().putString("background", key).apply() }); Text(label, color = Ink)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("مخفی‌سازی نوارهای سیستم", modifier = Modifier.weight(1f), color = Ink); Switch(immersive, { immersive = it; prefs.edit().putBoolean("immersive", it).apply() }) }
            NumberSetting("تعداد ضربه برای ورود مدیر", settingsTapThreshold, 3, 15) { value -> settingsTapThreshold = value; prefs.edit().putInt("settings_taps_threshold", value).apply() }
            var showAppNames by remember { mutableStateOf(prefs.getBoolean("show_app_names", true)) }
            var showContactNames by remember { mutableStateOf(prefs.getBoolean("show_contact_names", true)) }
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
            var windowBrightness by remember { mutableFloatStateOf(1f) }
            Text("روشنایی پنجرهٔ سهل‌بیت", fontWeight = FontWeight.Bold, color = Ink)
            Text("این کنترل فقط روشنایی داخل سهل‌بیت را تغییر می‌دهد و به تنظیم روشنایی کل گوشی دست نمی‌زند.", color = Ink, fontSize = 12.sp)
            Slider(value = windowBrightness, onValueChange = { value ->
                windowBrightness = value
                (context as? ComponentActivity)?.window?.let { w ->
                    val params = w.attributes
                    params.screenBrightness = value.coerceIn(0.05f, 1f)
                    w.attributes = params
                }
            }, valueRange = 0.05f..1f)
            OutlinedButton(onClick = {
                windowBrightness = 1f
                (context as? ComponentActivity)?.window?.let { w -> val params = w.attributes; params.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = params }
            }, modifier = Modifier.fillMaxWidth()) { Text("استفاده از روشنایی خودکار/سیستمی") }
            Divider()
            Text("خاموش‌شدن خودکار نمایشگر", fontWeight = FontWeight.Bold, color = Ink)
            Text("پس از ${(screenTimeoutSeconds / 60).toPersianDigits()} دقیقه و ${(screenTimeoutSeconds % 60).toPersianDigits()} ثانیه بی‌استفاده‌ماندن", color = Teal)
            Slider(value = screenTimeoutSeconds.toFloat(), onValueChange = { screenTimeoutSeconds = it.toInt() }, valueRange = 15f..1800f, onValueChangeFinished = {
                prefs.edit().putInt("screen_timeout_seconds", screenTimeoutSeconds).apply()
                if (android.provider.Settings.System.canWrite(context)) {
                    val applied = runCatching {
                        android.provider.Settings.System.putInt(
                            context.contentResolver,
                            android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                            screenTimeoutSeconds * 1000
                        )
                    }.getOrDefault(false)
                    if (!applied) android.widget.Toast.makeText(context, "تنظیم زمان خاموشی نمایشگر اعمال نشد", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "برای اعمال زمان خاموشی، اجازهٔ تغییر تنظیمات سیستمی لازم است", android.widget.Toast.LENGTH_LONG).show()
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            })
            Text("برای اعمال این تنظیم، اجازهٔ تغییر تنظیمات سیستمی را در صفحهٔ اندروید فعال کن.", color = Ink, fontSize = 13.sp)
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) } }, modifier = Modifier.fillMaxWidth()) { Text("اعطای اجازهٔ تنظیم زمان خاموشی") }
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
        "permissions" -> SettingsPage("مجوزها و نقش‌های برنامه", "برای استفاده از سهل‌بیت به‌عنوان صفحهٔ اصلی، نقش Home را انتخاب کنید. نقش تلفن (Dialer) مستقل و فقط برای قابلیت‌های تماس لازم است.", { route = "dashboard" }) {
            Button(onClick = onSelectLauncher, modifier = Modifier.fillMaxWidth()) { Text("انتخاب سهل‌بیت به‌عنوان صفحهٔ اصلی (Home)") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSelectDialer, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("انتخاب اختیاری به‌عنوان برنامهٔ تلفن (Dialer)") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) } }, modifier = Modifier.fillMaxWidth()) { Text("اجازهٔ تغییر تنظیمات سیستمی") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAndroidSettings, modifier = Modifier.fillMaxWidth()) { Text("بازکردن تنظیمات اندروید") }
        }
        "about" -> SettingsPage("درباره سهل‌بیت", "SahlBit Launcher\nنسخه ${BuildConfig.VERSION_NAME} (آزمایشی)\nتوسعه‌دهنده: امیرعلی پاشایی فرد\nشماره تماس: ۰۹۱۴۵۸۸۰۸۱۴\nوب‌سایت: pashaef.ir", { route = "dashboard" }) { }
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
    val apps = remember { runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString().lowercase() } }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(Cream).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = Forest) }; Text("برنامه‌های تلفن همراه", color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Text("برای اجرای هر برنامه، آن را انتخاب کنید.", color = Ink, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(apps, key = { it.activityInfo.packageName }) { item ->
                val pkg = item.activityInfo.packageName
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { onLaunch(pkg) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.loadLabel(pm).toString(), color = Ink, modifier = Modifier.weight(1f))
                    Text("اجرا", color = Teal, fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxWidth().height(132.dp)) {
            Text("ورود مدیر", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.align(Alignment.BottomStart))
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd).size(112.dp)) { Icon(Icons.Default.Close, contentDescription = "بازگشت به صفحه اصلی", tint = Color(0xFFDC2626), modifier = Modifier.size(88.dp)) }
        }
        }
        Spacer(Modifier.height(20.dp)); PinField("پین مدیر", pin, onPin)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp)); Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ورود به پیشخان مدیریت") }
        TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("پین را فراموش کرده‌ام") }
    }
}
@Composable private fun PinField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable private fun DashboardScreen(onExit: () -> Unit, onPages: () -> Unit, onContacts: () -> Unit, onAllApps: () -> Unit, onAppearance: () -> Unit, onClock: () -> Unit, onSecurity: () -> Unit, onPermissions: () -> Unit, onAbout: () -> Unit, onSelectLauncher: () -> Unit, onAndroidSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("مدیریت سهل‌بیت", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Button(onClick = onExit, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(6.dp)); Text("بازگشت به صفحه اصلی") } }
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DashboardCard("مدیریت صفحات و چیدمان", "تعداد صفحات و تعداد کارت‌ها", Icons.Default.Settings, onPages) }
            item { DashboardCard("مدیریت مخاطبان و برنامه‌ها", "انتخاب موارد مجاز برای صفحه اصلی", Icons.Default.Settings, onContacts) }
            item { DashboardCard("برنامه‌های تلفن همراه", "مشاهده و اجرای برنامه‌های نصب‌شده", Icons.Default.Settings, onAllApps) }
            item { DashboardCard("ظاهر و صفحه اصلی", "پس‌زمینه، رنگ‌ها و نوارهای سیستم", Icons.Default.Settings, onAppearance) }
            item { DashboardCard("سفارشی‌سازی ساعت", "اندازه، زبان اعداد و ثانیه‌شمار", Icons.Default.Settings, onClock) }
            item { DashboardCard("امنیت و پین مدیر", "تغییر رمز ورود مدیر", Icons.Default.Settings, onSecurity) }
            item { DashboardCard("مجوزها و دسترسی‌ها", "بررسی دسترسی‌های اندروید", Icons.Default.Settings, onPermissions) }
            item { DashboardCard("انتخاب لانچر پیش‌فرض", "مدیریت لانچر اصلی اندروید", Icons.Default.Home, onSelectLauncher) }
            item { DashboardCard("تنظیمات اندروید", "بازکردن تنظیمات سیستم", Icons.Default.Settings, onAndroidSettings) }
            item { DashboardCard("درباره سهل‌بیت", "نسخه، توسعه‌دهنده و راه‌های ارتباطی", Icons.Default.Info, onAbout) }
        }
    }
}
@Composable private fun DashboardCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(16.dp)); Column { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = Color(0xFF64736B), fontSize = 13.sp) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowBack, null, tint = Teal) }
    }
}
@Composable private fun SettingsPage(title: String, description: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = Forest) }; Text(title, color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp)); Text(description, color = Ink, lineHeight = 24.sp); Spacer(Modifier.height(18.dp)); content()
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

@Composable private fun HomeScreen(battery: Int, charging: Boolean, background: String, onAdminEntry: () -> Unit, pageCount: Int, entries: List<LauncherEntry>, gridColumns: Int, gridRows: Int, rowCardCounts: List<Int>, rowAlignments: List<String>, onEntryClick: (LauncherEntry) -> Unit, onConfirmCall: (LauncherEntry) -> Unit, pendingCall: LauncherEntry?, onCancelCall: () -> Unit, gridAlignment: String, pageNames: List<String>, onReorder: (String, String) -> Unit) {
    var settingsTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(settingsTaps) { if (settingsTaps > 0) { delay(1500); settingsTaps = 0 } }
    val context = LocalContext.current
    val launcherPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
    val adminTapThreshold = launcherPrefs.getInt("settings_taps_threshold", 7).coerceIn(3, 15)
    val showAppNames = launcherPrefs.getBoolean("show_app_names", true)
    val showContactNames = launcherPrefs.getBoolean("show_contact_names", true)
    val bg = when (background) { "white" -> Color.White; "cream" -> Color(0xFFFFF8E7); "gray" -> Color(0xFFE6E7E8); "blue" -> Color(0xFFE8F1F5); else -> Color(0xFF2563EB) }
    val fg = if (background == "forest") Color.White else Ink
    val day = LocalDate.now().dayOfWeek.value // Monday=1 ... Sunday=7
    val dayText = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")[day - 1]
    val marks = when (day) { 6 -> "◯"; 7 -> "│"; 1 -> "│  │"; 2 -> "│  │  │"; 3 -> "│  │  │  │"; 4 -> "│  │  │  │  │"; else -> "━━━━━━━━" }
    val batteryColor = when { battery >= 81 -> Color(0xFF087A35); battery >= 51 -> Color(0xFF36A653); battery >= 31 -> Color(0xFF9DBB35); battery >= 21 -> Color(0xFFE2B72D); else -> Color(0xFFD32F2F) }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (charging) Text("⚡", color = if (battery > 95) Color(0xFF168A45) else Color(0xFF1689D4), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { settingsTaps++; if (settingsTaps >= adminTapThreshold) { settingsTaps = 0; onAdminEntry() } }) { Icon(Icons.Default.Settings, "ورود مدیر", tint = fg.copy(alpha = .42f), modifier = Modifier.size(20.dp)) }
        }
        val clockPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
        val clockSize = clockPrefs.getInt("clock_size_dp", 300).coerceIn(180, 360)
        val clockDigits = clockPrefs.getInt("clock_digit_size_sp", 40).coerceIn(20, 56)
        val clockLanguage = clockPrefs.getString("clock_digit_language", "english") ?: "english"
        val secondsVisible = clockPrefs.getBoolean("clock_show_seconds", true)
        AnalogClock(Modifier.size(clockSize.dp), digitLanguage = clockLanguage, digitSizeSp = clockDigits, showSecondHand = secondsVisible)
        Spacer(Modifier.height(8.dp))
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
        }
        if (charging) Text(if (battery > 95) "شارژ تقریباً کامل" else "گوشی در حال شارژ است", color = fg, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp)); Text("$dayText   $marks", color = fg, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val pager = rememberPagerState(pageCount = { pageCount.coerceIn(1, 12) })
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val layoutPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
                val rowRoot = remember(layoutPrefs.getString("row_layouts", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_layouts", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val rowCountRoot = remember(layoutPrefs.getString("row_counts_per_page", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_counts_per_page", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val alignRoot = remember(layoutPrefs.getString("row_alignments", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_alignments", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val storedRows = rowRoot.optJSONArray(index.toString())
                val fallbackRows = rowCountRoot.optInt(index.toString(), layoutPrefs.getInt("grid_rows", 3)).coerceIn(1, 6)
                val pageRowCounts = if (storedRows != null) (0 until storedRows.length()).map { storedRows.optInt(it, gridColumns).coerceIn(1, 4) } else List(fallbackRows) { gridColumns }
                val savedAlignments = alignRoot.opt(index.toString())
                val pageRowAlignments = when (savedAlignments) { is org.json.JSONArray -> (0 until pageRowCounts.size).map { savedAlignments.optString(it, "center") }; is String -> List(pageRowCounts.size) { savedAlignments }; else -> List(pageRowCounts.size) { gridAlignment } }
                val pageEntries = entries.filter { it.page == index }
                if (pageEntries.isEmpty()) {
                    Text("هنوز موردی به این صفحه اضافه نشده است", color = fg, fontSize = 15.sp)
                } else {
                    val cardBounds = remember(index) { mutableStateMapOf<String, Rect>() }
                    var draggingEntryId by remember(index) { mutableStateOf<String?>(null) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var entryOffset = 0
                        pageRowCounts.forEachIndexed { rowIndex, rowCount ->
                            val rowEntries = pageEntries.drop(entryOffset).take(rowCount)
                            entryOffset += rowCount
                            if (rowEntries.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = when (pageRowAlignments.getOrElse(rowIndex) { "center" }) { "left" -> Arrangement.Start; "right" -> Arrangement.End; else -> Arrangement.Center }) {
                                rowEntries.forEach { entry ->
                                    Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            var lastDropTarget by remember(entry.id) { mutableStateOf<String?>(null) }
                            Card(onClick = { if (draggingEntryId == null) onEntryClick(entry) }, modifier = Modifier
                                .onGloballyPositioned { cardBounds[entry.id] = it.boundsInWindow() }
                                .pointerInput(entry.id, pageEntries, cardBounds) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingEntryId = entry.id; lastDropTarget = null },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val origin = cardBounds[entry.id]?.topLeft ?: return@detectDragGesturesAfterLongPress
                                            val pointer = origin + change.position
                                            // Track the current drop target without mutating the backing list mid-gesture.
                                            // Reordering during pointer movement invalidates LazyGrid coordinates and
                                            // can cause repeated swaps or a dropped gesture on slower devices.
                                            lastDropTarget = cardBounds.entries.firstOrNull { (id, rect) ->
                                                id != entry.id && rect.contains(pointer)
                                            }?.key
                                        },
                                        onDragEnd = {
                                            val target = lastDropTarget
                                            draggingEntryId = null
                                            lastDropTarget = null
                                            if (target != null) onReorder(entry.id, target)
                                        },
                                        onDragCancel = { draggingEntryId = null; lastDropTarget = null }
                                    )
                                }
                                .alpha(if (draggingEntryId == entry.id) .55f else 1f),
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
            AlertDialog(onDismissRequest = onCancelCall, title = { Text("تماس با ${pendingCall.title}؟") }, text = { Text("آیا می‌خواهید با این مخاطب تماس بگیرید؟\n${pendingCall.phoneNumber ?: ""}") }, confirmButton = { Button(onClick = { onConfirmCall(pendingCall) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF168A45))) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("بله، تماس بگیر") } }, dismissButton = { OutlinedButton(onClick = onCancelCall) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("خیر") } })
        }
    }
}

private fun createSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
private fun hashPin(pin: String, saltHex: String): String {
    val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }.also { spec.clearPassword() }
}
private fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

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
