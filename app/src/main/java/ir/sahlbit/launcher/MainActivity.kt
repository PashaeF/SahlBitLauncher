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

private val Forest = Color(0xFF244B3D)
private val Cream = Color(0xFFF6F2E8)
private val Ink = Color(0xFF26352F)
private val Teal = Color(0xFF397B70)

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
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, secondary = Teal, background = Cream)) {
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
    var pageCount by remember { mutableIntStateOf(prefs.getInt("page_count", 5).coerceIn(1, 12)) }
    var pageNames by remember { mutableStateOf(runCatching { org.json.JSONArray(prefs.getString("page_names", "[]") ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList()).let { saved -> (0 until prefs.getInt("page_count", 5).coerceIn(1, 12)).map { saved.getOrNull(it)?.ifBlank { "صفحه ${it + 1}" } ?: "صفحه ${it + 1}" } }) }
    var cardsPerPage by remember { mutableIntStateOf(prefs.getInt("cards_per_page", 5)) }
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
    var gridRows by remember { mutableIntStateOf(prefs.getInt("grid_rows", 3).coerceIn(1, 6)) }
    var gridAlignment by remember { mutableStateOf(prefs.getString("grid_alignment", "center") ?: "center") }
    fun loadRowCounts(page: Int): List<Int> = runCatching {
        val root = org.json.JSONObject(prefs.getString("row_layouts", "{}") ?: "{}")
        val a = root.optJSONArray(page.toString()) ?: org.json.JSONArray()
        (0 until a.length()).map { a.optInt(it, gridColumns).coerceIn(1, 4) }
    }.getOrDefault(emptyList()).ifEmpty { List(gridRows) { gridColumns } }
    var rowCardCounts by remember(selectedPage) { mutableStateOf(loadRowCounts(selectedPage).take(gridRows).let { it + List((gridRows - it.size).coerceAtLeast(0)) { gridColumns } }) }
    var lastRowAlignment by remember(selectedPage) { mutableStateOf(runCatching { org.json.JSONObject(prefs.getString("row_alignments", "{}") ?: "{}").optString(selectedPage.toString(), gridAlignment) }.getOrDefault(gridAlignment)) }
    fun persistRowLayout() {
        val root = runCatching { org.json.JSONObject(prefs.getString("row_layouts", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        root.put(selectedPage.toString(), org.json.JSONArray(rowCardCounts))
        val aligns = runCatching { org.json.JSONObject(prefs.getString("row_alignments", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        aligns.put(selectedPage.toString(), lastRowAlignment)
        prefs.edit().putString("row_layouts", root.toString()).putString("row_alignments", aligns.toString()).apply()
    }
    var screenTimeoutSeconds by remember { mutableIntStateOf(prefs.getInt("screen_timeout_seconds", 60).coerceIn(15, 1800)) }
    var pendingCall by remember { mutableStateOf<LauncherEntry?>(null) }
    val context = LocalContext.current
    val contactPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0) ?: "مخاطب"
                        val number = c.getString(1) ?: ""
                        val photoSource = c.getString(2)
                        val contactId = c.getLong(3)
                        val lookupUri = ContactsContract.Contacts.getLookupUri(context.contentResolver, contactId)
                        val photo = photoSource?.let { source -> runCatching {
                            context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                                val target = java.io.File(context.filesDir, "contact_${java.util.UUID.randomUUID()}.jpg")
                                target.outputStream().use { output -> input.copyTo(output) }
                                Uri.fromFile(target).toString()
                            }
                        }.getOrNull() }
                        val item = LauncherEntry(kind = "contact", title = name, page = selectedPage, contactUri = uri.toString(), contactLookupUri = lookupUri?.toString(), phoneNumber = number, photoUri = photo, photoSource = "contact", photoSourceUri = photoSource)
                        entries = entries + item
                        LauncherEntryStore.save(prefs, entries)
                    }
                }
            }
        }
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

    val protectedRoutes = setOf("dashboard", "pages", "contacts", "appearance", "security", "permissions", "about", "pinchange", "pinverify")
    LaunchedEffect(route, adminAuthenticated) {
        if (route in protectedRoutes && !adminAuthenticated) route = "login"
    }

    when (route) {
        "welcome" -> WelcomeScreen(
            onContinue = { route = "setpin" }
        )
        "setpin" -> PinSetupScreen(newPin, { newPin = it.filter(Char::isDigit).take(12) }, confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, error) {
            if (newPin.length < 4) error = "PIN باید حداقل ۴ رقم داشته باشد."
            else if (newPin != confirmPin) error = "دو PIN یکسان نیستند."
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
            pageCount = pageCount, pageNames = pageNames, cardsPerPage = cardsPerPage,
            entries = entries, gridColumns = gridColumns, gridRows = gridRows, gridAlignment = gridAlignment, rowCardCounts = rowCardCounts, lastRowAlignment = lastRowAlignment,
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
                error = if (lockUntil > now) "۵ تلاش ناموفق؛ ورود برای ۳۰ ثانیه قفل شد." else "PIN صحیح نیست."
                pinInput = ""
            }
        }
        "dashboard" -> DashboardScreen(
            onExit = { adminAuthenticated = false; route = "home" },
            onPages = { route = "pages" }, onContacts = { route = "contacts" },
            onAppearance = { route = "appearance" }, onSecurity = { route = "security" },
            onPermissions = { route = "permissions" }, onAbout = { route = "about" },
            onSelectLauncher = onSelectLauncher, onAndroidSettings = onAndroidSettings
        )
        "pages" -> SettingsPage("مدیریت صفحات", "نام‌گذاری، افزودن، حذف و جابه‌جایی صفحات و تنظیم تعداد و چینش کارت‌ها. حذف صفحه، کارت‌های آن را به صفحهٔ قبلی منتقل می‌کند تا اطلاعات از بین نرود.", { route = "dashboard" }) {
            NumberSetting("تعداد صفحات", pageCount, 1, 12) { requested ->
                val next = requested.coerceIn(1, 12)
                if (next < pageCount) entries = entries.map { e -> if (e.page >= next) e.copy(page = next - 1) else e }
                if (next > pageCount) pageNames = pageNames + (pageCount until next).map { "صفحه ${it + 1}" }
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
            OutlinedTextField(value = pageNames.getOrElse(selectedPage) { "صفحه ${selectedPage + 1}" }, onValueChange = { value -> pageNames = pageNames.toMutableList().also { it[selectedPage] = value.take(28) }; prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (selectedPage > 0) { val names = pageNames.toMutableList(); val n = names[selectedPage]; names[selectedPage] = names[selectedPage - 1]; names[selectedPage - 1] = n; pageNames = names; entries = entries.map { e -> when (e.page) { selectedPage -> e.copy(page = selectedPage - 1); selectedPage - 1 -> e.copy(page = selectedPage); else -> e } }; selectedPage--; LauncherEntryStore.save(prefs, entries); prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() } }, enabled = selectedPage > 0) { Text("انتقال صفحه به بالا") }
                OutlinedButton(onClick = { if (selectedPage < pageCount - 1) { val names = pageNames.toMutableList(); val n = names[selectedPage]; names[selectedPage] = names[selectedPage + 1]; names[selectedPage + 1] = n; pageNames = names; entries = entries.map { e -> when (e.page) { selectedPage -> e.copy(page = selectedPage + 1); selectedPage + 1 -> e.copy(page = selectedPage); else -> e } }; selectedPage++; LauncherEntryStore.save(prefs, entries); prefs.edit().putString("page_names", org.json.JSONArray(pageNames).toString()).apply() } }, enabled = selectedPage < pageCount - 1) { Text("انتقال صفحه به پایین") }
            }
            if (confirmPageDeletion) {
                AlertDialog(
                    onDismissRequest = { confirmPageDeletion = false },
                    title = { Text("حذف صفحهٔ ${selectedPage + 1}؟") },
                    text = { Text("تمام کارت‌های این صفحه نیز حذف می‌شوند. این کار قابل بازگشت نیست.") },
                    confirmButton = { Button(onClick = {
                        val removedPhotos = entries.filter { it.page == selectedPage }.mapNotNull { it.photoUri }.toSet()
                        entries = entries.mapNotNull { e -> when { e.page == selectedPage -> null; e.page > selectedPage -> e.copy(page = e.page - 1); else -> e } }
                        removedPhotos.filter { photo -> entries.none { it.photoUri == photo } }.forEach { deleteOwnedContactPhoto(context, it) }
                        pageNames = pageNames.filterIndexed { index, _ -> index != selectedPage }
                        pageCount -= 1; selectedPage = selectedPage.coerceAtMost(pageCount - 1)
                        LauncherEntryStore.save(prefs, entries); prefs.edit().putInt("page_count", pageCount).putString("page_names", org.json.JSONArray(pageNames).toString()).apply()
                        confirmPageDeletion = false
                    }) { Text("حذف قطعی") } },
                    dismissButton = { TextButton(onClick = { confirmPageDeletion = false }) { Text("انصراف") } }
                )
            }
            NumberSetting("کارت در هر صفحه (حداکثر نمایشی)", cardsPerPage, 1, 12) { cardsPerPage = it; prefs.edit().putInt("cards_per_page", it).apply() }
            NumberSetting("تعداد ردیف‌ها", gridRows, 1, 6) { value ->
                gridRows = value; prefs.edit().putInt("grid_rows", value).apply()
                rowCardCounts = (rowCardCounts.take(value) + List((value - rowCardCounts.size).coerceAtLeast(0)) { gridColumns })
                persistRowLayout()
            }
            Text("تعداد کارت در هر ردیف این صفحه", color = Ink, fontWeight = FontWeight.Bold)
            rowCardCounts.forEachIndexed { row, count ->
                NumberSetting("ردیف ${row + 1}", count, 1, 4) { value -> rowCardCounts = rowCardCounts.toMutableList().also { it[row] = value }; persistRowLayout() }
            }
            Text("تراز ردیف آخر", color = Ink, fontWeight = FontWeight.Bold)
            listOf("right" to "راست", "center" to "وسط", "left" to "چپ").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { lastRowAlignment = key; persistRowLayout() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(lastRowAlignment == key, { lastRowAlignment = key; persistRowLayout() }); Text(label, color = Ink) }
            }
            Text("پیش‌نمایش زندهٔ چیدمان", color = Forest, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    var n = 1
                    rowCardCounts.forEachIndexed { row, count ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = when (if (row == rowCardCounts.lastIndex) lastRowAlignment else gridAlignment) { "left" -> Arrangement.Start; "right" -> Arrangement.End; else -> Arrangement.Center }) {
                            repeat(count) {
                                Box(Modifier.weight(1f).padding(horizontal = 2.dp).height(48.dp).clip(RoundedCornerShape(9.dp)).background(Teal.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text("${n++}", color = Forest, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            Text("تراز چیدمان", color = Ink, fontWeight = FontWeight.Bold)
            listOf("right" to "راست", "center" to "وسط", "left" to "چپ").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { gridAlignment = key; prefs.edit().putString("grid_alignment", key).apply() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(gridAlignment == key, { gridAlignment = key; prefs.edit().putString("grid_alignment", key).apply() }); Text(label, color = Ink) }
            }
        }
        "contacts" -> SettingsPage("مخاطبان و برنامه‌های صفحه اصلی", "مدیر می‌تواند مخاطب واقعی را از دفترچه تلفن یا برنامه را از فهرست برنامه‌های قابل‌اجرا انتخاب کند. انتخاب‌ها فقط در صفحه اصلی سهل‌بیت نمایش داده می‌شوند.", { route = "dashboard" }) {
            NumberSetting("صفحه مقصد برای موارد جدید", selectedPage + 1, 1, pageCount.coerceAtLeast(1)) { selectedPage = it - 1 }
            Button(onClick = { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب مخاطب از دفترچه تلفن") }
            Spacer(Modifier.height(8.dp))
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val installedApps = remember(route, resumeRevision) { runCatching { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() } }.getOrDefault(emptyList()) }
            Text("افزودن برنامه به صفحه اصلی", color = Ink, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(installedApps) { resolve ->
                    val pkg = resolve.activityInfo.packageName
                    val label = resolve.loadLabel(pm).toString()
                    Row(Modifier.fillMaxWidth().clickable {
                        if (entries.none { it.kind == "app" && it.packageName == pkg && it.page == selectedPage }) {
                            entries = entries + LauncherEntry(kind = "app", title = label, page = selectedPage, packageName = pkg)
                            LauncherEntryStore.save(prefs, entries)
                        }
                    }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Ink, modifier = Modifier.weight(1f)); Text("افزودن +", color = Teal)
                    }
                }
            }
            Spacer(Modifier.height(10.dp)); Text("موارد انتخاب‌شده", color = Ink, fontWeight = FontWeight.Bold)
            entries.forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.title}  •  صفحه ${entry.page + 1}", color = Ink, modifier = Modifier.weight(1f))
                    if (entry.kind == "contact") TextButton(onClick = { selectedPhotoEntryId = entry.id; galleryPicker.launch("image/*") }) { Text("عکس گالری") }
                    if (entry.kind == "contact") TextButton(onClick = {
                        val lookup = entry.contactLookupUri?.let(Uri::parse)
                        if (lookup != null) runCatching {
                            context.startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(lookup, ContactsContract.Contacts.CONTENT_ITEM_TYPE))
                        }.onFailure { android.widget.Toast.makeText(context, "ویرایش مخاطب در دسترس نیست", android.widget.Toast.LENGTH_LONG).show() }
                        else android.widget.Toast.makeText(context, "این کارت به مخاطب دفترچه تلفن متصل نیست؛ دوباره مخاطب را انتخاب کنید", android.widget.Toast.LENGTH_LONG).show()
                    }) { Text("ویرایش دفترچه تلفن") }
                    IconButton(onClick = { val indices = entries.indices.filter { entries[it].page == entry.page }; val position = indices.indexOfFirst { entries[it].id == entry.id }; if (position > 0) { val mutable = entries.toMutableList(); val a = indices[position]; val b = indices[position - 1]; val temp = mutable[a]; mutable[a] = mutable[b]; mutable[b] = temp; entries = mutable; LauncherEntryStore.save(prefs, entries) } }) { Text("↑", color = Forest, fontSize = 20.sp) }
                    IconButton(onClick = { val indices = entries.indices.filter { entries[it].page == entry.page }; val position = indices.indexOfFirst { entries[it].id == entry.id }; if (position >= 0 && position < indices.lastIndex) { val mutable = entries.toMutableList(); val a = indices[position]; val b = indices[position + 1]; val temp = mutable[a]; mutable[a] = mutable[b]; mutable[b] = temp; entries = mutable; LauncherEntryStore.save(prefs, entries) } }) { Text("↓", color = Forest, fontSize = 20.sp) }
                    TextButton(onClick = {
                        val removed = entries.firstOrNull { it.id == entry.id }
                        entries = entries.filterNot { it.id == entry.id }
                        LauncherEntryStore.save(prefs, entries)
                        removed?.photoUri?.let { photo ->
                            if (entries.none { it.photoUri == photo }) runCatching {
                                val uri = Uri.parse(photo)
                                if (uri.scheme == "file") {
                                    val file = java.io.File(uri.path ?: "")
                                    val base = context.filesDir.canonicalFile
                                    if (file.canonicalFile.parentFile == base && file.name.startsWith("contact_")) file.delete()
                                }
                            }
                        }
                    }) { Text("حذف", color = Color(0xFFB3261E)) }
                }
            }
        }
        "appearance" -> SettingsPage("ظاهر و صفحه اصلی", "رنگ‌ها و پس‌زمینه به‌صورت محلی ذخیره می‌شوند.", { route = "dashboard" }) {
            Text("پس‌زمینه", fontWeight = FontWeight.Bold, color = Ink)
            listOf("forest" to "سبز جنگلی", "white" to "سفید", "cream" to "کِرِم", "gray" to "طوسی", "blue" to "آبی ملایم").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { backgroundChoice = key; prefs.edit().putString("background", key).apply() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = backgroundChoice == key, onClick = { backgroundChoice = key; prefs.edit().putString("background", key).apply() }); Text(label, color = Ink)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("مخفی‌سازی نوارهای سیستم", modifier = Modifier.weight(1f), color = Ink); Switch(immersive, { immersive = it; prefs.edit().putBoolean("immersive", it).apply() }) }
            Divider()
            Text("خاموش‌شدن خودکار نمایشگر", fontWeight = FontWeight.Bold, color = Ink)
            Text("پس از ${screenTimeoutSeconds / 60} دقیقه و ${screenTimeoutSeconds % 60} ثانیه بی‌استفاده‌ماندن", color = Teal)
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
        "security" -> SettingsPage("امنیت مدیر", "PIN با PBKDF2 و نمک تصادفی ذخیره می‌شود. پس از پنج تلاش ناموفق، ورود برای ۳۰ ثانیه قفل می‌شود. اگر PIN فراموش شد، امکان بازنشانی با یک پرسش ریاضی ساده وجود دارد؛ این روش امنیت محدودی دارد.", { route = "dashboard" }) {
            Button(onClick = { oldPinInput = ""; newPin = ""; confirmPin = ""; error = ""; route = "pinverify" }, modifier = Modifier.fillMaxWidth()) { Text("تغییر PIN مدیر") }
        }
        "pinverify" -> SettingsPage("تأیید PIN فعلی", "برای تغییر PIN، ابتدا رمز فعلی مدیر را وارد کنید.", { route = "security" }) {
            PinField("PIN فعلی", oldPinInput) { oldPinInput = it.filter(Char::isDigit).take(12) }
            if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E))
            Button(onClick = {
                val salt = prefs.getString("admin_pin_salt", "") ?: ""
                if (pinHash != null && constantTimeEquals(hashPin(oldPinInput, salt), pinHash!!)) { oldPinInput = ""; error = ""; route = "pinchange" }
                else { oldPinInput = ""; error = "PIN فعلی نادرست است." }
            }, modifier = Modifier.fillMaxWidth()) { Text("تأیید و ادامه") }
        }
        "mathrecovery" -> SettingsPage("بازیابی دسترسی مدیر", "برای بازنشانی PIN، مسئلهٔ سادهٔ زیر را حل کن. این روش امنیت محدودی دارد و هر فردی که به گوشی دسترسی داشته باشد ممکن است آن را حل کند.", { route = "login" }) {
            val expected = if (mathOperation == "+") mathA + mathB else mathA - mathB
            Text("$mathA $mathOperation $mathB = ؟", color = Forest, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = mathAnswer, onValueChange = { mathAnswer = it.filter(Char::isDigit).take(3) }, label = { Text("پاسخ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E))
            Button(onClick = {
                if (mathAnswer.toIntOrNull() == expected) { failedPins = 0; lockUntil = 0L; prefs.edit().putInt("pin_failed_attempts", 0).putLong("pin_lock_until", 0L).apply(); adminAuthenticated = true; newPin = ""; confirmPin = ""; error = ""; route = "pinchange" }
                else { error = "پاسخ درست نیست. دوباره تلاش کن."; mathA = SecureRandom().nextInt(8) + 2; mathB = SecureRandom().nextInt(8) + 2; mathAnswer = "" }
            }, modifier = Modifier.fillMaxWidth()) { Text("تأیید و تعیین PIN جدید") }
        }
        "pinchange" -> PinSetupScreen(newPin, { newPin = it.filter(Char::isDigit).take(12) }, confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, error) {
            if (newPin.length < 4) error = "PIN باید حداقل ۴ رقم داشته باشد."
            else if (newPin != confirmPin) error = "دو PIN یکسان نیستند."
            else { val salt = createSalt(); val h = hashPin(newPin, salt); prefs.edit().putString("admin_pin_salt", salt).putString("admin_pin_hash", h).apply(); pinHash = h; route = "dashboard"; error = "" }
        }
        "permissions" -> SettingsPage("مجوزها و نقش‌های برنامه", "برای مدیریت تماس‌ها، سهل‌بیت باید با تأیید کاربر به‌عنوان برنامهٔ تلفن پیش‌فرض انتخاب شود. اندروید این دسترسی را از طریق نقش رسمی Dialer واگذار می‌کند.", { route = "dashboard" }) {
            Button(onClick = onSelectDialer, modifier = Modifier.fillMaxWidth()) { Text("انتخاب سهل‌بیت به‌عنوان برنامه تلفن پیش‌فرض") }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAndroidSettings, modifier = Modifier.fillMaxWidth()) { Text("بازکردن تنظیمات اندروید") }
        }
        "about" -> SettingsPage("درباره سهل‌بیت", "SahlBit Launcher\nنسخه ۰٫۲٫۵ (آزمایشی)\nتوسعه‌دهنده: Amirali Pashae fard\nPashaeF.ir", { route = "dashboard" }) { }
        "recovery" -> SettingsPage("بازیابی سهل‌بیت", "اگر سهل‌بیت درست کار نمی‌کند، از دکمه زیر برای انتخاب لانچر جایگزین استفاده کن. سهل‌بیت به‌صورت خودکار لانچر گوشی را تغییر نمی‌دهد.", { route = "home" }) {
            Button(onClick = onSelectLauncher, modifier = Modifier.fillMaxWidth()) { Text("انتخاب لانچر اندروید / One UI Home") }
        }
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

@Composable private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("به سهل‌بیت خوش آمدید", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(16.dp)); Text("برای شروع، مدیر دستگاه باید PIN اختصاصی خود را تعیین کند. تنظیمات و اطلاعات این نسخه روی خود گوشی ذخیره می‌شوند.", color = Ink, fontSize = 18.sp, lineHeight = 29.sp)
        Spacer(Modifier.height(28.dp)); Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("شروع راه‌اندازی", fontSize = 18.sp) }
    }
}

@Composable private fun PinSetupScreen(pin: String, onPin: (String) -> Unit, confirm: String, onConfirm: (String) -> Unit, error: String, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تنظیم PIN مدیر", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(12.dp)); Text("یک PIN عددی با حداقل ۴ رقم انتخاب کن. فقط مدیر باید آن را بداند.", color = Ink, fontSize = 17.sp)
        Spacer(Modifier.height(18.dp)); PinField("PIN جدید", pin, onPin); Spacer(Modifier.height(12.dp)); PinField("تکرار PIN", confirm, onConfirm)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp)); Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ذخیره PIN") }
    }
}
@Composable private fun PinLoginScreen(pin: String, onPin: (String) -> Unit, error: String, onForgot: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("ورود مدیر", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Forest); Spacer(Modifier.height(20.dp)); PinField("PIN مدیر", pin, onPin)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp)); Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ورود به پنل مدیریت") }
        TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("PIN را فراموش کرده‌ام") }
    }
}
@Composable private fun PinField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable private fun DashboardScreen(onExit: () -> Unit, onPages: () -> Unit, onContacts: () -> Unit, onAppearance: () -> Unit, onSecurity: () -> Unit, onPermissions: () -> Unit, onAbout: () -> Unit, onSelectLauncher: () -> Unit, onAndroidSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("مدیریت سهل‌بیت", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.weight(1f)); TextButton(onClick = onExit) { Icon(Icons.Default.Settings, null); Text("تحویل به سالمند") } }
        Text("داشبورد مدیر", color = Teal, fontSize = 15.sp); Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DashboardCard("مدیریت صفحات و چیدمان", "تعداد صفحات و تعداد کارت‌ها", Icons.Default.Settings, onPages) }
            item { DashboardCard("مدیریت مخاطبان و برنامه‌ها", "انتخاب موارد مجاز برای صفحه اصلی", Icons.Default.Settings, onContacts) }
            item { DashboardCard("ظاهر و صفحه اصلی", "پس‌زمینه، رنگ‌ها و نوارهای سیستم", Icons.Default.Settings, onAppearance) }
            item { DashboardCard("امنیت و PIN مدیر", "تغییر رمز ورود مدیر", Icons.Default.Settings, onSecurity) }
            item { DashboardCard("مجوزها و دسترسی‌ها", "بررسی دسترسی‌های اندروید", Icons.Default.Settings, onPermissions) }
            item { DashboardCard("درباره لانچر", "نسخه، توسعه‌دهنده و وب‌سایت", Icons.Default.Info, onAbout) }
            item { DashboardCard("انتخاب لانچر پیش‌فرض", "مدیریت لانچر اصلی اندروید", Icons.Default.Home, onSelectLauncher) }
            item { DashboardCard("تنظیمات اندروید", "بازکردن تنظیمات سیستم", Icons.Default.Settings, onAndroidSettings) }
        }
    }
}
@Composable private fun DashboardCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(16.dp)); Column { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = Color(0xFF64736B), fontSize = 13.sp) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowBack, null, tint = Teal) }
    }
}
@Composable private fun SettingsPage(title: String, description: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = Forest) }; Text(title, color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp)); Text(description, color = Ink, lineHeight = 24.sp); Spacer(Modifier.height(18.dp)); content()
    }
}
@Composable private fun NumberSetting(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) { Text(label, color = Ink, fontWeight = FontWeight.SemiBold); Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−", fontSize = 24.sp, color = Forest) }; Text(value.toString(), fontSize = 22.sp, color = Forest); IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Icon(Icons.Default.Add, null) } } } }
}

@Composable private fun HomeScreen(battery: Int, charging: Boolean, background: String, onAdminEntry: () -> Unit, pageCount: Int, cardsPerPage: Int, entries: List<LauncherEntry>, gridColumns: Int, gridRows: Int, rowCardCounts: List<Int>, lastRowAlignment: String, onEntryClick: (LauncherEntry) -> Unit, onConfirmCall: (LauncherEntry) -> Unit, pendingCall: LauncherEntry?, onCancelCall: () -> Unit, gridAlignment: String, pageNames: List<String>, onReorder: (String, String) -> Unit) {
    var settingsTaps by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val bg = when (background) { "white" -> Color.White; "cream" -> Cream; "gray" -> Color(0xFFE6E7E8); "blue" -> Color(0xFFE8F1F5); else -> Forest }
    val fg = if (background == "forest") Color.White else Ink
    val day = LocalDate.now().dayOfWeek.value // Monday=1 ... Sunday=7
    val dayText = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")[day - 1]
    val marks = when (day) { 6 -> "◯"; 7 -> "│"; 1 -> "│  │"; 2 -> "│  │  │"; 3 -> "│  │  │  │"; 4 -> "│  │  │  │  │"; else -> "━━━━━━━━" }
    val batteryColor = when { battery >= 81 -> Color(0xFF087A35); battery >= 51 -> Color(0xFF36A653); battery >= 31 -> Color(0xFF9DBB35); battery >= 21 -> Color(0xFFE2B72D); else -> Color(0xFFD32F2F) }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (charging) Text("⚡", color = if (battery > 95) Color(0xFF168A45) else Color(0xFF1689D4), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { settingsTaps++; if (settingsTaps >= 7) { settingsTaps = 0; onAdminEntry() } }) { Icon(Icons.Default.Settings, "ورود مدیر", tint = fg.copy(alpha = .42f), modifier = Modifier.size(20.dp)) }
        }
        AnalogClock(Modifier.size(184.dp))
        Spacer(Modifier.height(8.dp))
        Text("$battery٪", color = batteryColor, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (charging && battery > 95) "شارژ تقریباً کامل" else if (charging) "گوشی در حال شارژ است" else "میزان شارژ باتری", color = fg, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp)); Text("$dayText   $marks", color = fg, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val pager = rememberPagerState(pageCount = { pageCount.coerceIn(1, 12) })
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val layoutPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
                val rowRoot = remember(layoutPrefs.getString("row_layouts", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_layouts", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val alignRoot = remember(layoutPrefs.getString("row_alignments", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_alignments", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val storedRows = rowRoot.optJSONArray(index.toString())
                val pageRowCounts = if (storedRows != null) (0 until storedRows.length()).map { storedRows.optInt(it, gridColumns).coerceIn(1, 4) }.take(gridRows) else List(gridRows) { gridColumns }
                val pageLastAlignment = alignRoot.optString(index.toString(), gridAlignment)
                val pageEntries = entries.filter { it.page == index }.take(minOf(cardsPerPage.coerceAtLeast(1), pageRowCounts.sum()))
                if (pageEntries.isEmpty()) {
                    Text("هنوز موردی به این صفحه اضافه نشده است", color = fg, fontSize = 15.sp)
                } else {
                    val cardBounds = remember(index) { mutableStateMapOf<String, Rect>() }
                    var draggingEntryId by remember(index) { mutableStateOf<String?>(null) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var entryOffset = 0
                        pageRowCounts.take(gridRows).forEachIndexed { rowIndex, rowCount ->
                            val rowEntries = pageEntries.drop(entryOffset).take(rowCount)
                            entryOffset += rowCount
                            if (rowEntries.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = when (if (rowIndex == pageRowCounts.take(gridRows).lastIndex) pageLastAlignment else gridAlignment) { "left" -> Arrangement.Start; "right" -> Arrangement.End; else -> Arrangement.Center }) {
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
                                    if (bitmap != null) Image(bitmap, contentDescription = entry.title, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)))
                                    else if (entry.kind == "app") {
                                        val iconBitmap = remember(entry.packageName) { entry.packageName?.let { pkg -> runCatching {
                                            val drawable = context.packageManager.getApplicationIcon(pkg)
                                            val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(bmp); drawable.setBounds(0, 0, 96, 96); drawable.draw(canvas); bmp.asImageBitmap()
                                        }.getOrNull() } }
                                        if (iconBitmap != null) Image(iconBitmap, contentDescription = entry.title, modifier = Modifier.size(58.dp))
                                        else Icon(Icons.Default.Settings, null, tint = Teal, modifier = Modifier.size(54.dp))
                                    }
                                     else Icon(Icons.Default.Person, null, tint = if (background == "forest") Color.White else Teal, modifier = Modifier.size(54.dp))
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
        Text("برای جابه‌جایی بین صفحه‌ها، صفحه را به چپ یا راست بکشید", color = fg.copy(alpha = .75f), fontSize = 12.sp)
        Text("${pageNames.getOrElse(pager.currentPage) { "صفحه ${pager.currentPage + 1}" }}  •  ${pager.currentPage + 1} از ${pageCount.coerceIn(1, 12)}", color = fg.copy(alpha = .65f), fontSize = 12.sp)
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
