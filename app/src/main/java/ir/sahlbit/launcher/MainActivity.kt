@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.res.painterResource
import ir.sahlbit.launcher.ui.AnalogClock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
private fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MaterialSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(0.80f),
            valueRange = valueRange
        )
    }
}

private fun Int.toPersianDigits(): String = toString().map { ch ->
    if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
}.joinToString("" )
private fun String.toEnglishDigits(): String = map { ch ->
    when (ch) { in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar(); in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar(); else -> ch }
}.joinToString("")
private fun String.toPersianDigitsText(): String = map { ch -> if (ch in '0'..'9') ('۰'.code + ch.code - '0'.code).toChar() else ch }.joinToString("")

private val Forest = Color(0xFF2563EB)
private val Cream = Color(0xFFFFF8E7)
private val Ink = Color(0xFF172033)
private val Teal = Color(0xFF1D4ED8)

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)
private val SahlBitTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge=t.displayLarge.copy(fontFamily=Vazirmatn, lineHeight=58.sp), displayMedium=t.displayMedium.copy(fontFamily=Vazirmatn, lineHeight=48.sp), displaySmall=t.displaySmall.copy(fontFamily=Vazirmatn, lineHeight=40.sp),
        headlineLarge=t.headlineLarge.copy(fontFamily=Vazirmatn, lineHeight=40.sp), headlineMedium=t.headlineMedium.copy(fontFamily=Vazirmatn, lineHeight=34.sp), headlineSmall=t.headlineSmall.copy(fontFamily=Vazirmatn, lineHeight=30.sp),
        titleLarge=t.titleLarge.copy(fontFamily=Vazirmatn, lineHeight=32.sp), titleMedium=t.titleMedium.copy(fontFamily=Vazirmatn, lineHeight=28.sp), titleSmall=t.titleSmall.copy(fontFamily=Vazirmatn, lineHeight=24.sp),
        bodyLarge=t.bodyLarge.copy(fontFamily=Vazirmatn, lineHeight=28.sp), bodyMedium=t.bodyMedium.copy(fontFamily=Vazirmatn, lineHeight=24.sp), bodySmall=t.bodySmall.copy(fontFamily=Vazirmatn, lineHeight=21.sp),
        labelLarge=t.labelLarge.copy(fontFamily=Vazirmatn, lineHeight=22.sp), labelMedium=t.labelMedium.copy(fontFamily=Vazirmatn, lineHeight=20.sp), labelSmall=t.labelSmall.copy(fontFamily=Vazirmatn, lineHeight=18.sp)
    )
}

private fun hasUsageAccess(context: Context): Boolean = runCatching {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)

private fun isHomeRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) return manager.isRoleHeld(RoleManager.ROLE_HOME)
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
}
private fun isDialerRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) return manager.isRoleHeld(RoleManager.ROLE_DIALER)
    }
    return context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage == context.packageName
}
private fun isWizardPermissionGranted(context: Context, step: Int): Boolean = when (step) {
    1 -> isHomeRoleHeld(context)
    2 -> Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
    3 -> isDialerRoleHeld(context)
    4 -> Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    5 -> Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    6 -> {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    }
    7 -> Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    else -> true
}
private fun openUsageAccessSettings(context: Context) {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val candidates = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = packageUri
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            addFlags(flags)
        },
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            addFlags(flags)
        },
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(flags),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri).addFlags(flags)
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    android.widget.Toast.makeText(context, "بازکردن تنظیمات دسترسی آمار مصرف برنامه‌ها ممکن نشد. لطفاً از تنظیمات اندروید این دسترسی را برای سهل‌بیت فعال کنید.", android.widget.Toast.LENGTH_LONG).show()
}

@Composable
private fun PermissionCard(title: String, description: String, buttonLabel: String, granted: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(if (granted) "فعال" else "نیازمند اجازه", color = if (granted) Color(0xFF168A45) else Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(description, color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(if (granted && buttonLabel.startsWith("اعطای")) "بررسی وضعیت دسترسی" else buttonLabel) }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val battery = mutableIntStateOf(100)
    private val charging = mutableStateOf(false)
    private val callEndedFlag = mutableStateOf(false)
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CallActivity.ACTION_CALL_ENDED) callEndedFlag.value = true
        }
    }
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
        val callEndedFilter = IntentFilter(CallActivity.ACTION_CALL_ENDED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(callEndedReceiver, callEndedFilter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(callEndedReceiver, callEndedFilter)
        val prefs = getSharedPreferences("sahlbit_local", MODE_PRIVATE)
        window.attributes = window.attributes.apply {
            screenBrightness = prefs.getFloat("window_brightness", 0.40f).coerceIn(0.05f, 1f)
        }
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
                        callEnded = callEndedFlag.value,
                        onCallEndedHandled = { callEndedFlag.value = false },
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
            android.widget.Toast.makeText(this, "در اندروید ۵ رابط تماس سهل‌بیت فعال نیست و برنامهٔ تلفن خود تلفن همراه تماس‌ها را مدیریت می‌کند.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    private fun openHomeSettings() { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.onFailure { openAndroidSettings() } }
    private fun openAndroidSettings() { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode(true)
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        runCatching { unregisterReceiver(callEndedReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun SahlBitApp(
    prefs: android.content.SharedPreferences,
    battery: Int,
    charging: Boolean,
    crashRecovery: Boolean,
    callEnded: Boolean,
    onCallEndedHandled: () -> Unit,
    onSelectLauncher: () -> Unit,
    onSelectDialer: () -> Unit,
    onAndroidSettings: () -> Unit,
    onImmersiveChange: (Boolean) -> Unit
) {
    var initialized by remember { mutableStateOf(prefs.getBoolean("initialized", false)) }
    var pinHash by remember { mutableStateOf(prefs.getString("admin_pin_hash", null)) }
    val completedWizardVersion = prefs.getInt("setup_wizard_version", 0)
    var wizardStep by remember { mutableIntStateOf(prefs.getInt("setup_wizard_step", if (completedWizardVersion >= 1 || initialized) 7 else 0).coerceIn(0, 7)) }
    var wizardPinSetup by remember { mutableStateOf(pinHash == null && (completedWizardVersion >= 2 || initialized)) }
    var wizardRequestRevision by remember { mutableIntStateOf(-1) }
    var route by remember { mutableStateOf(if (pinHash != null) { if (crashRecovery) "recovery" else "home" } else "wizard") }
    var routeHistory by remember { mutableStateOf(emptyList<String>()) }
    fun navigateTo(destination: String) { if (route != destination) { routeHistory = routeHistory + route; route = destination } }
    fun navigateBack(fallback: String = "dashboard") { val previous = routeHistory.lastOrNull(); if (previous != null) { routeHistory = routeHistory.dropLast(1); route = previous } else route = fallback }
    val dashboardListState = rememberLazyListState()
    var pinInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var immersive by remember { mutableStateOf(true) }
    var backgroundChoice by remember { mutableStateOf((prefs.getString("background", "cream") ?: "cream").let { if (it == "forest") "cream" else it }) }
    var settingsTapThreshold by remember { mutableIntStateOf(prefs.getInt("settings_taps_threshold", 7).coerceIn(3, 15)) }
    var pageCount by remember { mutableIntStateOf(prefs.getInt("page_count", 3).coerceAtLeast(1)) }
    var pageNames by remember { mutableStateOf(runCatching { org.json.JSONArray(prefs.getString("page_names", "[]") ?: "[]").let { a -> (0 until a.length()).map { a.optString(it) } } }.getOrDefault(emptyList()).let { saved -> (0 until prefs.getInt("page_count", 3).coerceAtLeast(1)).map { saved.getOrNull(it)?.ifBlank { "صفحه ${(it + 1).toPersianDigits()}" } ?: "صفحه ${(it + 1).toPersianDigits()}" } }) }
    var clockSizeDp by remember { mutableIntStateOf(prefs.getInt("clock_size_dp", 360).coerceIn(180, 360)) }
    var clockDigitSizeSp by remember { mutableIntStateOf(prefs.getInt("clock_digit_size_sp", 40).coerceIn(20, 56)) }
    var clockDigitLanguage by remember { mutableStateOf(prefs.getString("clock_digit_language", "english") ?: "english") }
    var clockHourLength by remember { mutableFloatStateOf(prefs.getFloat("clock_hour_hand_length", 0.40f).coerceIn(0.25f, 0.70f)) }
    var clockMinuteLength by remember { mutableFloatStateOf(prefs.getFloat("clock_minute_hand_length", 0.65f).coerceIn(0.35f, 0.88f)) }
    var clockSecondLength by remember { mutableFloatStateOf(prefs.getFloat("clock_second_hand_length", 0.90f).coerceIn(0.40f, 0.92f)) }
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
    var customContacts by remember { mutableStateOf(CustomContactsStore.load(prefs)) }
    var dialerNumber by remember { mutableStateOf("") }
    var showContactSourceDialog by remember { mutableStateOf(false) }
    var selectedCustomContactForPage by remember { mutableStateOf<CustomContact?>(null) }
    var selectedPage by remember { mutableIntStateOf(0) }
    fun loadPageFlag(key: String, page: Int, default: Boolean): Boolean = runCatching { org.json.JSONObject(prefs.getString(key, "{}") ?: "{}").optBoolean(page.toString(), default) }.getOrDefault(default)
    fun savePageFlag(key: String, page: Int, value: Boolean) { val root = runCatching { org.json.JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()); root.put(page.toString(), value); prefs.edit().putString(key, root.toString()).apply() }
    var recentCallsFlagsRevision by remember { mutableIntStateOf(0) }
    var pendingRecentCallsPage by remember { mutableIntStateOf(0) }
    var confirmPageDeletion by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableIntStateOf(prefs.getInt("grid_columns", 2).coerceIn(1, 6)) }
    fun loadRowsPerPage(page: Int): Int = runCatching {
        val configured = org.json.JSONObject(prefs.getString("row_counts_per_page", "{}") ?: "{}").opt(page.toString())
        when (configured) {
            is Number -> configured.toInt().coerceIn(1, 6)
            else -> {
                val legacyRows = org.json.JSONObject(prefs.getString("row_layouts", "{}") ?: "{}").optJSONArray(page.toString())
                (legacyRows?.length() ?: prefs.getInt("grid_rows", 2)).coerceIn(1, 6)
            }
        }
    }.getOrDefault(2)
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
        (0 until a.length()).map { a.optInt(it, gridColumns).coerceIn(1, 6) }
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
        aligns.put(selectedPage.toString(), org.json.JSONArray(List(rowCardCounts.size) { "center" }))
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
        } else {
            android.widget.Toast.makeText(context, if (granted) "اجازهٔ تماس تلفنی فعال شد." else "اجازهٔ تماس تلفنی داده نشد؛ می‌توانید بعداً از بخش مجوزها اقدام کنید.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    var contactPickerPending by remember { mutableStateOf(false) }
    var pendingContactName by remember { mutableStateOf("") }
    var pendingContactLookup by remember { mutableStateOf<String?>(null) }
    var pendingContactPhotoSource by remember { mutableStateOf<String?>(null) }
    var pendingContactOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun addCustomContactToPage(contact: CustomContact, phone: CustomContactPhone) {
        val item = LauncherEntry(kind = "contact", title = contact.name, page = selectedPage, phoneNumber = phone.number, photoUri = phone.photoUri, photoSource = if (phone.photoUri != null) "gallery" else "none")
        entries = entries + item
        LauncherEntryStore.save(prefs, entries)
    }
    fun addPickedContact(name: String, number: String, phoneUri: String, lookupUri: String?, photoSource: String?) {
        val item = LauncherEntry(kind = "contact", title = name.ifBlank { "مخاطب" }, page = selectedPage, contactUri = phoneUri, contactLookupUri = lookupUri, phoneNumber = number, photoUri = null, photoSource = if (!photoSource.isNullOrBlank()) "contact" else "none", photoSourceUri = photoSource)
        entries = entries + item
        LauncherEntryStore.save(prefs, entries)
    }
    val contactPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone._ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)).orEmpty().ifBlank { "مخاطب" }
                        val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty().trim()
                        val photoColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                        val photoSource = if (photoColumn >= 0) cursor.getString(photoColumn) else null
                        val contactIdColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val contactId = if (contactIdColumn >= 0) cursor.getLong(contactIdColumn) else -1L
                        val lookup = if (contactId >= 0L) runCatching {
                            ContactsContract.Contacts.getLookupUri(context.contentResolver, android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId))?.toString()
                        }.getOrNull() else null
                        if (number.isBlank()) {
                            android.widget.Toast.makeText(context, "برای این مخاطب شمارهٔ تلفنی ثبت نشده است.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            val allNumbers = if (contactId >= 0L) runCatching {
                                context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone._ID),
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId.toString()),
                                    null
                                )?.use { phoneCursor ->
                                    buildList {
                                        while (phoneCursor.moveToNext()) {
                                            val value = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty().trim()
                                            val rowId = phoneCursor.getLong(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID))
                                            if (value.isNotBlank()) add(value to ContactsContract.CommonDataKinds.Phone.CONTENT_URI.buildUpon().appendPath(rowId.toString()).build().toString())
                                        }
                                    }.distinctBy { it.first }
                                }.orEmpty()
                            }.getOrDefault(emptyList()) else emptyList()
                            if (allNumbers.size > 1) {
                                pendingContactName = name
                                pendingContactLookup = lookup
                                pendingContactPhotoSource = photoSource
                                pendingContactOptions = allNumbers
                            } else {
                                addPickedContact(name, number, uri.toString(), lookup, photoSource)
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "اطلاعات مخاطب قابل خواندن نیست.", android.widget.Toast.LENGTH_LONG).show()
                    }
                } ?: android.widget.Toast.makeText(context, "انتخابگر مخاطبان اطلاعاتی برنگرداند.", android.widget.Toast.LENGTH_LONG).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "خواندن اطلاعات مخاطب ناموفق بود.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val contactsPermission = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && contactPickerPending) contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        else if (granted) android.widget.Toast.makeText(context, "دسترسی به مخاطبان فعال است.", android.widget.Toast.LENGTH_SHORT).show()
        else android.widget.Toast.makeText(context, "برای نمایش و انتخاب مخاطبان، اجازهٔ دسترسی به مخاطبان لازم است. می‌توانید بعداً از بخش مجوزها اقدام کنید.", android.widget.Toast.LENGTH_LONG).show()
        contactPickerPending = false
    }
    val recentCallsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) savePageFlag("page_recent_calls", pendingRecentCallsPage, true)
        else android.widget.Toast.makeText(context, "برای نمایش تماس‌های اخیر، اجازهٔ مشاهدهٔ سابقهٔ تماس لازم است. انتخاب شما ذخیره نشد.", android.widget.Toast.LENGTH_LONG).show()
        recentCallsFlagsRevision++
    }
    val galleryPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val targetId = selectedPhotoEntryId
        if (uri != null && targetId != null) {
            val savedPhoto = saveSquarePhotoFromUri(context, uri, "contact_")
            if (savedPhoto != null) {
                val oldPhoto = entries.firstOrNull { it.id == targetId }?.photoUri
                entries = entries.map { if (it.id == targetId) it.copy(photoUri = savedPhoto, photoSource = "gallery", photoSourceUri = null) else it }
                LauncherEntryStore.save(prefs, entries)
                oldPhoto?.takeIf { old -> entries.none { it.photoUri == old } }?.let { old -> deleteOwnedContactPhoto(context, old) }
            } else android.widget.Toast.makeText(context, "خواندن یا برش مربعی تصویر ناموفق بود.", android.widget.Toast.LENGTH_LONG).show()
        }
        selectedPhotoEntryId = null
    }
    val cameraPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val targetId = selectedPhotoEntryId
        val savedPhoto = bitmap?.let { saveSquarePhoto(context, it) }
        if (targetId != null && savedPhoto != null) {
            val oldPhoto = entries.firstOrNull { it.id == targetId }?.photoUri
            entries = entries.map { if (it.id == targetId) it.copy(photoUri = savedPhoto, photoSource = "gallery", photoSourceUri = null) else it }
            LauncherEntryStore.save(prefs, entries)
            oldPhoto?.takeIf { old -> entries.none { it.photoUri == old } }?.let { old -> deleteOwnedContactPhoto(context, old) }
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
    LaunchedEffect(callEnded) {
        if (callEnded) {
            pendingCall = null
            pendingCallNumber = null
            adminAuthenticated = false
            pinInput = ""
            error = ""
            route = "home"
            onCallEndedHandled()
        }
    }
    LaunchedEffect(resumeRevision, route, pinHash, wizardRequestRevision) {
        if (pinHash != null && (route == "wizard" || route == "setpin")) {
            prefs.edit().remove("setup_wizard_step").putInt("setup_wizard_version", 2).commit()
            route = "dashboard"
            adminAuthenticated = true
            pinInput = ""
        } else if (route == "wizard" && wizardRequestRevision >= 0 && resumeRevision > wizardRequestRevision) {
            isWizardPermissionGranted(context, wizardStep)
            wizardRequestRevision = -1
        }
    }
    LaunchedEffect(resumeRevision) {
        if (resumeRevision > 0) {
            val refreshed = entries.map { entry ->
                if (entry.kind != "contact" || entry.contactUri.isNullOrBlank()) entry
                else runCatching {
                    context.contentResolver.query(Uri.parse(entry.contactUri), arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val currentSourceUri = c.getString(2)
                            val freshPhoto = if (entry.photoSource == "contact") null else entry.photoUri
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
    BackHandler(enabled = route != "home" && route != "welcome" && route != "wizard") {
        when (route) {
            "dashboard" -> { adminAuthenticated = false; routeHistory = emptyList(); route = "home" }
            "login" -> { routeHistory = emptyList(); route = "home" }
            "mathrecovery" -> navigateBack("login")
            "recovery" -> { routeHistory = emptyList(); route = "home" }
            "setpin" -> { if (pinHash != null) { route = "dashboard"; adminAuthenticated = true } }
            else -> navigateBack("dashboard")
        }
    }

    val protectedRoutes = setOf("dashboard", "pages", "contacts", "allapps", "appearance", "clock", "audio", "security", "permissions", "about", "pinchange", "pinverify", "callcustom", "callconfirm", "updates", "androidsettings", "uninstall", "dialer", "customcontacts", "recentcalls", "recentcustom")
    LaunchedEffect(route, adminAuthenticated) {
        if (route in protectedRoutes && !adminAuthenticated) route = "login"
    }

    when (route) {
        "wizard" -> if (wizardPinSetup) {
            PinSetupScreen(newPin, { newPin = it.filter(Char::isDigit).take(12) }, confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, error) {
                if (newPin.length < 4) error = "پین باید حداقل ۴ رقم داشته باشد."
                else if (newPin != confirmPin) error = "دو پین یکسان نیستند."
                else {
                    val salt = createSalt()
                    val hash = hashPin(newPin, salt)
                    prefs.edit().putString("admin_pin_salt", salt).putString("admin_pin_hash", hash).putBoolean("initialized", true).putInt("setup_wizard_version", 2).remove("setup_wizard_step").commit()
                    pinHash = hash
                    initialized = true
                    wizardPinSetup = false
                    newPin = ""
                    confirmPin = ""
                    error = ""
                    route = "dashboard"
                    adminAuthenticated = true
                }
            }
        } else SetupWizardScreen(
            step = wizardStep,
            granted = runCatching { isWizardPermissionGranted(context, wizardStep) }.getOrNull(),
            onGrant = {
                wizardRequestRevision = resumeRevision
                when (wizardStep) {
                    1 -> onSelectLauncher()
                    2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
                    3 -> onSelectDialer()
                    4 -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) android.widget.Toast.makeText(context, "در این نسخه از اندروید، انتخاب مخاطب از طریق انتخابگر سیستم انجام می‌شود و نیازی به دسترسی گسترده نیست.", android.widget.Toast.LENGTH_LONG).show() else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) android.widget.Toast.makeText(context, "دسترسی به مخاطبان قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show() else contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                    5 -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) android.widget.Toast.makeText(context, "در این نسخه از اندروید، تماس از طریق شماره‌گیر سیستم انجام می‌شود و نیازی به دسترسی مستقیم تماس نیست.", android.widget.Toast.LENGTH_LONG).show() else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) android.widget.Toast.makeText(context, "اجازهٔ تماس تلفنی قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show() else callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                    6 -> openUsageAccessSettings(context)
                    7 -> if (Build.VERSION.SDK_INT >= 26) runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))) }
                }
            },
            onNext = {
                wizardRequestRevision = -1
                if (wizardStep >= 7) {
                    prefs.edit().remove("setup_wizard_step").putInt("setup_wizard_version", 2).commit()
                    if (pinHash != null) {
                        route = "dashboard"
                        adminAuthenticated = true
                    } else {
                        wizardPinSetup = true
                    }
                } else {
                    wizardStep += 1
                    prefs.edit().putInt("setup_wizard_step", wizardStep).apply()
                }
            },
            onBack = {
                wizardRequestRevision = -1
                if (wizardStep > 0) { wizardStep -= 1; prefs.edit().putInt("setup_wizard_step", wizardStep).apply() }
            }
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
            battery = battery, charging = charging, background = backgroundChoice, customContacts = customContacts, permissionRevision = resumeRevision,
            onAdminEntry = { pinInput = ""; error = ""; routeHistory = emptyList(); navigateTo("login") },
            pageCount = pageCount, pageNames = pageNames,
            entries = entries, gridColumns = gridColumns, gridRows = gridRows, gridAlignment = gridAlignment, rowCardCounts = rowCardCounts, rowAlignments = rowAlignments,
            onEntryClick = { entry -> if (entry.kind == "contact") pendingCall = entry else entry.packageName?.let { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg)?.let { launchIntent -> context.startActivity(launchIntent) } } } },
            onConfirmCall = { entry ->
                entry.phoneNumber?.takeIf { it.isNotBlank() }?.let { number ->
                    pendingCallNumber = number
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        pendingCallNumber = null
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                    } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
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
            onForgot = { mathA = SecureRandom().nextInt(8) + 2; mathB = SecureRandom().nextInt(8) + 2; mathOperation = if (SecureRandom().nextBoolean()) "+" else "−"; mathAnswer = ""; error = ""; navigateTo("mathrecovery") }) {
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
            listState = dashboardListState,
            onExit = { adminAuthenticated = false; routeHistory = emptyList(); route = "home" },
            onPages = { navigateTo("pages") }, onContacts = { navigateTo("contacts") }, onAllApps = { navigateTo("allapps") },
            onAppearance = { navigateTo("appearance") }, onClock = { navigateTo("clock") }, onSecurity = { navigateTo("security") },
            onPermissions = { navigateTo("permissions") }, onAbout = { navigateTo("about") }, onAudio = { navigateTo("audio") },
            onCallCustomization = { navigateTo("callcustom") },
            onCallConfirmation = { navigateTo("callconfirm") },
            onCustomContacts = { navigateTo("customcontacts") },
            onDialer = { dialerNumber = ""; navigateTo("dialer") },
            onRecentCallsCustomization = { navigateTo("recentcustom") },
            onUpdates = { navigateTo("updates") },
            onSelectLauncher = onSelectLauncher, onAndroidSettings = { navigateTo("androidsettings") }, onUninstall = { navigateTo("uninstall") }
        )
        "audio" -> AudioSettingsPage(prefs = prefs, onBack = { navigateBack("dashboard") })
        "updates" -> UpdateSettingsPage(onBack = { navigateBack("dashboard") })
        "callcustom" -> CallCustomizationPage(prefs = prefs, contacts = entries.filter { it.kind == "contact" }, callPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED, onRequestCallPermission = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                android.widget.Toast.makeText(context, "در Android 5 تماس از طریق شماره‌گیر سیستم انجام می‌شود.", android.widget.Toast.LENGTH_LONG).show()
            } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(context, "اجازهٔ برقراری تماس از قبل فعال است.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
            }
        }, onBack = { navigateBack("dashboard") })
        "callconfirm" -> CallConfirmationCustomizationPage(prefs = prefs, contacts = entries.filter { it.kind == "contact" }, onBack = { navigateBack("dashboard") })
        "customcontacts" -> CustomContactsPage(contacts = customContacts, onSave = { customContacts = it; CustomContactsStore.save(prefs, it) }, onBack = { navigateBack("dashboard") })
        "dialer" -> DialerPage(onBack = { navigateBack("dashboard") }, initialNumber = dialerNumber, onCall = { number ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
            else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) runCatching { context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))) }.onFailure { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
            else { pendingCallNumber = number; callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE) }
        })
        "recentcustom" -> RecentCallsCustomizationPage(prefs = prefs, onBack = { navigateBack("dashboard") })
        "allapps" -> AllAppsScreen(onBack = { navigateBack("dashboard") }, onLaunch = { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } } }, onDetails = { pkg -> runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) } })
        "clock" -> SettingsPage("سفارشی‌سازی ساعت", "اندازهٔ صفحهٔ ساعت، اندازه و زبان اعداد و نمایش ثانیه‌شمار را تنظیم کنید.", { navigateBack("dashboard") }) {
            Text("اندازهٔ ساعت: ${clockSizeDp.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
            CompactSlider(value = clockSizeDp.toFloat(), onValueChange = { v -> clockSizeDp = v.toInt(); prefs.edit().putInt("clock_size_dp", clockSizeDp).apply() }, valueRange = 180f..360f)
            Text("اندازهٔ اعداد ساعت: ${clockDigitSizeSp.toPersianDigits()} sp", color = Ink, fontWeight = FontWeight.SemiBold)
            CompactSlider(value = clockDigitSizeSp.toFloat(), onValueChange = { v -> clockDigitSizeSp = v.toInt(); prefs.edit().putInt("clock_digit_size_sp", clockDigitSizeSp).apply() }, valueRange = 20f..56f)
            Text("زبان اعداد ساعت", color = Ink, fontWeight = FontWeight.Bold)
            listOf("english" to "انگلیسی (123)", "persian" to "فارسی (۱۲۳)").forEach { (key, label) -> Row(Modifier.fillMaxWidth().clickable { clockDigitLanguage = key; prefs.edit().putString("clock_digit_language", key).apply() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(clockDigitLanguage == key, { clockDigitLanguage = key; prefs.edit().putString("clock_digit_language", key).apply() }); Text(label, color = Ink) } }
            Text("طول عقربهٔ ساعت‌شمار: ${(clockHourLength * 100).toInt().toPersianDigits()}٪", color = Ink, fontWeight = FontWeight.SemiBold)
            CompactSlider(value = clockHourLength, onValueChange = { clockHourLength = it; prefs.edit().putFloat("clock_hour_hand_length", it).apply() }, valueRange = 0.25f..0.70f)
            Text("طول عقربهٔ دقیقه‌شمار: ${(clockMinuteLength * 100).toInt().toPersianDigits()}٪", color = Ink, fontWeight = FontWeight.SemiBold)
            CompactSlider(value = clockMinuteLength, onValueChange = { clockMinuteLength = it; prefs.edit().putFloat("clock_minute_hand_length", it).apply() }, valueRange = 0.35f..0.88f)
            Text("طول عقربهٔ ثانیه‌شمار: ${(clockSecondLength * 100).toInt().toPersianDigits()}٪", color = Ink, fontWeight = FontWeight.SemiBold)
            CompactSlider(value = clockSecondLength, onValueChange = { clockSecondLength = it; prefs.edit().putFloat("clock_second_hand_length", it).apply() }, valueRange = 0.40f..0.92f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("نمایش ثانیه‌شمار", modifier = Modifier.weight(1f), color = Ink); Switch(showSecondHand, { showSecondHand = it; prefs.edit().putBoolean("clock_show_seconds", it).apply() }) }
            Text("پیش‌نمایش ساعت صفحهٔ اصلی", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val previewClockSize = minOf(clockSizeDp, LocalConfiguration.current.screenWidthDp - 64).coerceAtLeast(180)
            val homeClockSize = minOf(clockSizeDp, LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120)
            val previewDigitSize = (clockDigitSizeSp * previewClockSize.toFloat() / homeClockSize.toFloat()).toInt()
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7)), shape = RoundedCornerShape(16.dp)) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    AnalogClock(Modifier.size(previewClockSize.dp), digitLanguage = clockDigitLanguage, digitSizeSp = previewDigitSize, showSecondHand = showSecondHand, hourHandLength = clockHourLength, minuteHandLength = clockMinuteLength, secondHandLength = clockSecondLength)
                }
            }
        }
        "pages" -> SettingsPage("مدیریت صفحه‌ها و چیدمان", "نام‌گذاری، افزودن، حذف و جابه‌جایی صفحه‌ها و تنظیم تعداد و چینش کارت‌ها. با حذف صفحه، کارت‌های آن به صفحهٔ قبلی منتقل می‌شوند؛ اگر صفحهٔ اول باشد، کارت‌ها در صفحهٔ اولِ باقی‌مانده حفظ می‌شوند.", { navigateBack("dashboard") }) {
            val previewContentWidth = (LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtLeast(240.dp)
            PermissionCard("برنامه صفحه اصلی (لانچر) پیش‌فرض", "برای نمایش سهل‌بیت با فشردن دکمهٔ خانه، آن را به‌عنوان برنامه صفحه اصلی (لانچر) پیش‌فرض انتخاب کنید.", "انتخاب برنامه صفحه اصلی", isHomeRoleHeld(context), onSelectLauncher)
            NumberSetting("تعداد صفحه‌ها", pageCount, 1, Int.MAX_VALUE) { requested ->
                val next = requested.coerceAtLeast(1)
                if (next < pageCount) {
                    entries = entries.map { e -> if (e.page >= next) e.copy(page = next - 1) else e }
                    remapPageLayoutPreferences(prefs, (0 until next).associateWith { it })
                }
                if (next > pageCount) pageNames = pageNames + (pageCount until next).map { "صفحه ${(it + 1).toPersianDigits()}" }
                pageNames = pageNames.take(next)
                pageCount = next; selectedPage = selectedPage.coerceAtMost(next - 1)
                LauncherEntryStore.save(prefs, entries); prefs.edit().putInt("page_count", next).putString("page_names", org.json.JSONArray(pageNames).toString()).apply()
            }
            PageDropdown("صفحهٔ در حال ویرایش", selectedPage, pageNames) { selectedPage = it }
            var showClockOnPage by remember(selectedPage, recentCallsFlagsRevision) { mutableStateOf(loadPageFlag("page_clock_enabled", selectedPage, true)) }
            var showBatteryOnPage by remember(selectedPage, recentCallsFlagsRevision) { mutableStateOf(loadPageFlag("page_battery_enabled", selectedPage, true)) }
            var showRecentCallsOnPage by remember(selectedPage, recentCallsFlagsRevision, resumeRevision) { mutableStateOf(loadPageFlag("page_recent_calls", selectedPage, false)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("ساعت دیواری در این صفحه باشد", modifier = Modifier.weight(1f), color = Ink); Switch(showClockOnPage, { showClockOnPage = it; savePageFlag("page_clock_enabled", selectedPage, it) }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("نمایشگر میزان شارژ و علامت در حال شارژ در این صفحه باشد", modifier = Modifier.weight(1f), color = Ink, lineHeight = 23.sp); Switch(showBatteryOnPage, { showBatteryOnPage = it; savePageFlag("page_battery_enabled", selectedPage, it) }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("نمایش تماس‌های اخیر", modifier = Modifier.weight(1f), color = Ink); Switch(showRecentCallsOnPage, { enabled ->
                if (!enabled) { showRecentCallsOnPage = false; savePageFlag("page_recent_calls", selectedPage, false) }
                else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) { showRecentCallsOnPage = true; savePageFlag("page_recent_calls", selectedPage, true) }
                else { pendingRecentCallsPage = selectedPage; recentCallsPermissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG) }
            }) }
            if (showRecentCallsOnPage) Text("در این صفحه، بخش کارت‌ها به فهرست تماس‌های اخیر اختصاص می‌یابد و چیدمان کارت‌ها در صفحهٔ اصلی نمایش داده نمی‌شود.", color = Color(0xFF64748B), fontSize = 13.sp, lineHeight = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (pageCount < Int.MAX_VALUE) {
                        val copyItems = entries.filter { it.page == selectedPage }.map { it.copy(id = java.util.UUID.randomUUID().toString(), page = pageCount) }
                        entries = entries + copyItems
                        remapPageLayoutPreferences(prefs, (0 until pageCount).associateWith { it }, selectedPage to pageCount)
                        pageNames = pageNames + "${pageNames[selectedPage]} - کپی"
                        pageCount += 1; selectedPage = pageCount - 1
                        LauncherEntryStore.save(prefs, entries); prefs.edit().putInt("page_count", pageCount).putString("page_names", org.json.JSONArray(pageNames).toString()).apply()
                    }
                }, enabled = pageCount < Int.MAX_VALUE) { Text("تکثیر صفحه") }
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
                    text = { Text("کارت‌های این صفحه حذف نمی‌شوند و به صفحهٔ قبلی منتقل خواهند شد. اگر صفحهٔ اول را حذف کنید، کارت‌ها در صفحهٔ اولِ باقی‌مانده، می‌مانند.") },
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
            NumberSetting("تعداد ردیف‌ها", gridRows, 1, 6, enabled = !showRecentCallsOnPage) { value ->
                gridRows = value; saveRowsPerPage(selectedPage, value)
                rowCardCounts = (rowCardCounts.take(value) + List((value - rowCardCounts.size).coerceAtLeast(0)) { gridColumns })
                rowAlignments = (rowAlignments.take(value) + List((value - rowAlignments.size).coerceAtLeast(0)) { "center" })
                persistRowLayout()
            }
            val selectedPageEntryCount = entries.count { it.page == selectedPage }
            if (selectedPageEntryCount > rowCardCounts.sum()) Text("توجه: این صفحه ${selectedPageEntryCount.toPersianDigits()} مورد دارد، اما ظرفیت چیدمان فعلی ${rowCardCounts.sum().toPersianDigits()} کارت است. برای نمایش همهٔ موارد، تعداد ردیف‌ها یا کارت‌های هر ردیف را افزایش دهید.", color = Color(0xFFB3261E), fontSize = 13.sp)
            Text("تنظیم تعداد کارت هر ردیف", color = Ink, fontWeight = FontWeight.Bold)
            rowCardCounts.forEachIndexed { row, count ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumberSetting("تعداد کارت در ردیف ${(row + 1).toPersianDigits()}", count, 1, 6, enabled = !showRecentCallsOnPage) { value -> rowCardCounts = rowCardCounts.toMutableList().also { it[row] = value }; persistRowLayout() }
                        Text("تراز این ردیف به‌صورت ثابت وسط‌چین است.", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
            Text("پیش‌نمایش زندهٔ چیدمان", color = Forest, fontWeight = FontWeight.Bold)
            Text("این پیش‌نمایش، کارت‌های واقعی همین صفحه را با ترتیب، تعداد و تراز ردیف‌ها نشان می‌دهد. اگر صفحه هنوز موردی نداشته باشد، جایگاه کارت‌ها به‌صورت نمونه نمایش داده می‌شود.", color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 19.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val previewEntries = entries.filter { it.page == selectedPage }
                    val previewShowContacts = prefs.getBoolean("show_contact_names", false)
                    val previewShowApps = prefs.getBoolean("show_app_names", false)
                    val previewContactRadius = prefs.getInt("home_contact_radius", 20).coerceIn(0, 50)
                    val previewAppRadius = prefs.getInt("home_app_radius", 20).coerceIn(0, 50)
                    var previewOffset = 0
                    var placeholderNumber = 1
                    rowCardCounts.forEachIndexed { row, count ->
                        val rowItems = previewEntries.drop(previewOffset).take(count)
                        previewOffset += count
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            if (rowItems.isEmpty()) {
                                repeat(count) {
                                    Box(Modifier.weight(1f).padding(horizontal = 3.dp).height(154.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Teal.copy(alpha = .25f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Icon(Icons.Default.Home, null, tint = Teal, modifier = Modifier.size(38.dp))
                                            Text("کارت نمونهٔ ${(placeholderNumber++).toPersianDigits()}", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            } else {
                                rowItems.forEach { entry ->
                                    Box(Modifier.weight(1f).padding(horizontal = 3.dp)) {
                                        Card(Modifier.fillMaxWidth().height(154.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                            Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                val showName = (entry.kind == "contact" && previewShowContacts) || (entry.kind == "app" && previewShowApps)
                                                val iconSize = if (showName) 88.dp else 104.dp
                                                val entryPhotoUri = if (entry.photoSource == "contact") entry.photoSourceUri else entry.photoUri
                                                val contactBitmap = remember(entry.id, entryPhotoUri) {
                                                    if (entry.kind == "contact") entryPhotoUri?.let { raw -> runCatching { context.contentResolver.openInputStream(Uri.parse(raw))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } else null
                                                }
                                                val appBitmap = remember(entry.packageName) {
                                                    if (entry.kind == "app") entry.packageName?.let { pkg -> runCatching {
                                                        val drawable = context.packageManager.getApplicationIcon(pkg)
                                                        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                                        android.graphics.Canvas(bitmap).also { canvas -> drawable.setBounds(0, 0, 96, 96); drawable.draw(canvas) }
                                                        bitmap.asImageBitmap()
                                                    }.getOrNull() } else null
                                                }
                                                when {
                                                    entry.kind == "contact" && contactBitmap != null -> Image(contactBitmap, contentDescription = entry.title, modifier = Modifier.size(iconSize).clip(RoundedCornerShape(previewContactRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                                    entry.kind == "app" && appBitmap != null -> Image(appBitmap, contentDescription = entry.title, modifier = Modifier.size(iconSize).clip(RoundedCornerShape(previewAppRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                                    entry.kind == "app" -> Icon(Icons.Default.Home, null, tint = Teal, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(previewAppRadius.dp)))
                                                    else -> Icon(Icons.Default.Person, null, tint = Teal, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(previewContactRadius.dp)))
                                                }
                                                if (showName) Text(entry.title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        "contacts" -> SettingsPage("مخاطبان و برنامه‌های صفحه اصلی", "مدیر می‌تواند مخاطب واقعی را از دفترچه تلفن یا برنامه را از فهرست برنامه‌های قابل‌اجرا انتخاب کند. انتخاب‌ها فقط در صفحه اصلی سهل‌بیت نمایش داده می‌شوند.", { navigateBack("dashboard") }) {
            val capacityForSelectedPage = loadRowCounts(selectedPage).sum()
            val selectedCountForPage = entries.count { it.page == selectedPage }
            PermissionCard("نقش برنامهٔ تلفن پیش‌فرض", "در صورت تمایل، سهل‌بیت را به‌عنوان برنامهٔ تلفن پیش‌فرض انتخاب کنید تا رابط تماس اختصاصی آن نمایش داده شود.", "انتخاب برنامهٔ تلفن پیش‌فرض", isDialerRoleHeld(context), onSelectDialer)
            PermissionCard(
                title = "دسترسی به مخاطبان",
                description = "برای نمایش نام کامل، شماره تماس و تصویر مخاطب‌ها در صفحهٔ اصلی و رابط تماس لازم است. این اجازه فقط امکان خواندن دفترچه تلفن را می‌دهد؛ سهل‌بیت مخاطبان را حذف یا ویرایش نمی‌کند.",
                buttonLabel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) "انتخاب مخاطب با انتخابگر سیستم" else "اعطای دسترسی به مخاطبان",
                granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
                onClick = { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) android.widget.Toast.makeText(context, "دسترسی به مخاطبان قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show() else contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) }
            )
            PermissionCard("اجازهٔ برقراری تماس", "برای شروع تماس مستقیم با شمارهٔ انتخاب‌شده لازم است. این دسترسی فقط با تأیید شما فعال می‌شود. در Android 5 تماس از طریق شماره‌گیر سیستم انجام می‌شود.", "اعطای اجازهٔ برقراری تماس", Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED, { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) android.widget.Toast.makeText(context, "در Android 5 تماس از طریق شماره‌گیر سیستم انجام می‌شود.", android.widget.Toast.LENGTH_LONG).show() else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE) })
            PageDropdown("صفحه‌ای که می‌خواهید ویرایش کنید", selectedPage, pageNames) { selectedPage = it }
            val recentCallsMode = loadPageFlag("page_recent_calls", selectedPage, false)
            if (recentCallsMode) Text("این صفحه برای نمایش تماس‌های اخیر تنظیم شده است؛ انتخاب مخاطب و برنامه در این صفحه غیرفعال است.", color = Color(0xFFB45309), fontSize = 13.sp, lineHeight = 22.sp)
            Button(onClick = { showContactSourceDialog = true }, enabled = !recentCallsMode, modifier = Modifier.fillMaxWidth()) { Text("انتخاب مخاطب") }
            if (selectedCountForPage > capacityForSelectedPage) {
                Spacer(Modifier.height(8.dp))
                Text("توجه: این صفحه ${selectedCountForPage.toPersianDigits()} مورد دارد، اما ظرفیت چیدمان فعلی ${capacityForSelectedPage.toPersianDigits()} مورد است. تعداد ردیف‌ها یا کارت‌های هر ردیف را در بخش مدیریت صفحه‌ها افزایش دهید.", color = Color(0xFFB45309), fontSize = 13.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val installedApps by produceState(initialValue = emptyList<android.content.pm.ResolveInfo>(), key1 = resumeRevision) {
                value = withContext(Dispatchers.IO) {
                    runCatching { pm.queryIntentActivities(launcherIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString() } }.getOrDefault(emptyList())
                }
            }
            var appSearch by remember { mutableStateOf("") }
            OutlinedTextField(value = appSearch, onValueChange = { appSearch = it }, enabled = !recentCallsMode, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجوی برنامه") }, leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f)) })
            Text("افزودن برنامه به صفحهٔ انتخاب‌شده", color = Ink, fontWeight = FontWeight.Bold)
            val visibleApps = installedApps.filter { it.loadLabel(pm).toString().contains(appSearch, ignoreCase = true) }
            LazyColumn(Modifier.heightIn(max = 260.dp), userScrollEnabled = !recentCallsMode, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visibleApps, key = { it.activityInfo.packageName }) { resolve ->
                    val pkg = resolve.activityInfo.packageName
                    val label = resolve.loadLabel(pm).toString()
                    val alreadyAdded = entries.any { it.kind == "app" && it.packageName == pkg && it.page == selectedPage }
                    Row(Modifier.fillMaxWidth().clickable {
                        if (!recentCallsMode && !alreadyAdded) {
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
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), userScrollEnabled = !recentCallsMode, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        TextButton(onClick = { photoSourceDialogEntry = entry }, enabled = !recentCallsMode, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("انتخاب عکس", color = Teal) }
                                        Spacer(Modifier.weight(1f))
                                        Box(Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, enabled = !recentCallsMode && samePagePosition > 0) { val m = entries.toMutableList(); val a = samePageIndices[samePagePosition]; val b = samePageIndices[samePagePosition - 1]; val x = m[a]; m[a] = m[b]; m[b] = x; entries = m; LauncherEntryStore.save(prefs, entries) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, "انتقال به بالا", tint = if (samePagePosition > 0) Forest else Color.LightGray) }
                                        Box(Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, enabled = !recentCallsMode && samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) { val m = entries.toMutableList(); val a = samePageIndices[samePagePosition]; val b = samePageIndices[samePagePosition + 1]; val x = m[a]; m[a] = m[b]; m[b] = x; entries = m; LauncherEntryStore.save(prefs, entries) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowDown, "انتقال به پایین", tint = if (samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) Forest else Color.LightGray) }
                                        IconButton(onClick = { if (!recentCallsMode) { val removed = entries.firstOrNull { it.id == entry.id }; entries = entries.filterNot { it.id == entry.id }; LauncherEntryStore.save(prefs, entries); removed?.photoUri?.let { photo -> if (entries.none { it.photoUri == photo }) deleteOwnedContactPhoto(context, photo) } } }, enabled = !recentCallsMode) { Icon(Icons.Default.Close, "حذف مورد", tint = Color(0xFFB3261E)) }
                                    }
                                } else {
                                    Text(entry.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.weight(1f))
                                        Box(Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, enabled = !recentCallsMode && samePagePosition > 0) {
                                            val m = entries.toMutableList(); val a = samePageIndices[samePagePosition]; val b = samePageIndices[samePagePosition - 1]; val x = m[a]; m[a] = m[b]; m[b] = x; entries = m; LauncherEntryStore.save(prefs, entries)
                                        }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, "انتقال به بالا", tint = if (samePagePosition > 0) Forest else Color.LightGray) }
                                        Box(Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, enabled = !recentCallsMode && samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) {
                                            val m = entries.toMutableList(); val a = samePageIndices[samePagePosition]; val b = samePageIndices[samePagePosition + 1]; val x = m[a]; m[a] = m[b]; m[b] = x; entries = m; LauncherEntryStore.save(prefs, entries)
                                        }, contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowDown, "انتقال به پایین", tint = if (samePagePosition >= 0 && samePagePosition < samePageIndices.lastIndex) Forest else Color.LightGray) }
                                        IconButton(onClick = { if (!recentCallsMode) { entries = entries.filterNot { it.id == entry.id }; LauncherEntryStore.save(prefs, entries) } }, enabled = !recentCallsMode) { Icon(Icons.Default.Close, "حذف برنامه", tint = Color(0xFFB3261E)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        "appearance" -> SettingsPage("ظاهرِ صفحه اصلی", "ظاهر صفحهٔ اصلی، پس‌زمینه و نمایش کارت‌ها را تنظیم کنید.", { navigateBack("dashboard") }) {
            DashboardCard("سفارشی‌سازی ساعت", "اندازه، زبان اعداد و ثانیه‌شمار", "clock") { navigateTo("clock") }
            DashboardCard("سفارشی‌سازی کادر تأیید تماس", "رنگ‌ها، عنوان‌ها و اندازهٔ دکمه‌های تأیید تماس", "callconfirm") { navigateTo("callconfirm") }
            val systemWriteGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
            PermissionCard("دسترسی تغییر تنظیمات سیستم", "برای اعمال برخی تنظیمات ظاهری و تغییرات سیستم لازم است. فعال‌سازی فقط با تأیید شما در تنظیمات اندروید انجام می‌شود.", "اعطای دسترسی تغییر تنظیمات سیستم", systemWriteGranted, { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) })
            var homeContactRadius by remember { mutableIntStateOf(prefs.getInt("home_contact_radius", 20).coerceIn(0, 50)) }
            var homeAppRadius by remember { mutableIntStateOf(prefs.getInt("home_app_radius", 20).coerceIn(0, 50)) }
            NumberSetting("گردی گوشهٔ تصویر مخاطبان: ${homeContactRadius.toPersianDigits()} dp", homeContactRadius, 0, 50) { homeContactRadius = it; prefs.edit().putInt("home_contact_radius", it).apply() }
            NumberSetting("گردی گوشهٔ لوگوی برنامه‌ها: ${homeAppRadius.toPersianDigits()} dp", homeAppRadius, 0, 50) { homeAppRadius = it; prefs.edit().putInt("home_app_radius", it).apply() }
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
            Text("این کنترل فقط روشنایی داخل سهل‌بیت را تغییر می‌دهد و به تنظیم روشنایی کل تلفن همراه دست نمی‌زند.", color = Ink, fontSize = 12.sp)
            CompactSlider(value = windowBrightness, onValueChange = { value ->
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
            Text("زمان را به‌صورت عددی وارد کنید. پس از تغییر عدد، تنظیم در صورت داشتن اجازه به‌طور خودکار ذخیره و اعمال می‌شود.", color = Ink)
            LaunchedEffect(screenTimeoutSeconds, resumeRevision) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)) {
                    runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, screenTimeoutSeconds * 1000) }
                }
            }
            val timeoutScope = rememberCoroutineScope()
            val minutesBringIntoView = remember { BringIntoViewRequester() }
            val secondsBringIntoView = remember { BringIntoViewRequester() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = timeoutMinutesText, onValueChange = { value ->
                    timeoutMinutesText = value.toPersianDigitsText().filter(Char::isDigit).take(2)
                    screenTimeoutSeconds = ((timeoutMinutesText.toEnglishDigits().toIntOrNull() ?: 0) * 60 + (timeoutSecondsText.toEnglishDigits().toIntOrNull() ?: 0)).coerceIn(15, 1800)
                    prefs.edit().putInt("screen_timeout_seconds", screenTimeoutSeconds).apply()
                }, modifier = Modifier.weight(1f).bringIntoViewRequester(minutesBringIntoView).onFocusChanged { state -> if (state.isFocused) timeoutScope.launch { delay(120); minutesBringIntoView.bringIntoView() } }, label = { Text("دقیقه") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = timeoutSecondsText, onValueChange = { value ->
                    val seconds = value.toEnglishDigits().filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0
                    timeoutSecondsText = seconds.toPersianDigits()
                    screenTimeoutSeconds = ((timeoutMinutesText.toEnglishDigits().toIntOrNull() ?: 0) * 60 + seconds).coerceIn(15, 1800)
                    prefs.edit().putInt("screen_timeout_seconds", screenTimeoutSeconds).apply()
                }, modifier = Modifier.weight(1f).bringIntoViewRequester(secondsBringIntoView).onFocusChanged { state -> if (state.isFocused) timeoutScope.launch { delay(120); secondsBringIntoView.bringIntoView() } }, label = { Text("ثانیه") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("زمان انتخاب‌شده: ${(screenTimeoutSeconds / 60).toPersianDigits()} دقیقه و ${(screenTimeoutSeconds % 60).toPersianDigits()} ثانیه", color = Teal)
            Text("برای اعمال زمان خاموشی، اجازهٔ تغییر تنظیمات سیستمی را در صفحهٔ اندروید فعال کنید.", color = Ink, fontSize = 13.sp)
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
                else android.widget.Toast.makeText(context, "اجازهٔ تغییر تنظیمات سیستمی قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("اعطای اجازهٔ تغییر تنظیمات سیستم برای تنظیم زمان خاموشی") }
        }
        "security" -> SettingsPage("امنیت و پین مدیر", "برای تغییر رمز ورود مدیر یا بازیابی دسترسی از گزینه‌های زیر استفاده کنید.", { navigateBack("dashboard") }) {
            Button(onClick = { oldPinInput = ""; newPin = ""; confirmPin = ""; error = ""; navigateTo("pinverify") }, modifier = Modifier.fillMaxWidth()) { Text("تغییر پین مدیر") }
        }
        "pinverify" -> SettingsPage("تأیید پین فعلی", "برای تغییر پین، ابتدا رمز فعلی مدیر را وارد کنید.", { navigateBack("security") }) {
            PinField("پین فعلی", oldPinInput) { oldPinInput = it.filter(Char::isDigit).take(12) }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { mathA = SecureRandom().nextInt(8) + 2; mathB = SecureRandom().nextInt(8) + 2; mathOperation = if (SecureRandom().nextBoolean()) "+" else "−"; mathAnswer = ""; error = ""; navigateTo("mathrecovery") }) { Text("پین را فراموش کرده‌ام") }
            if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E))
            Button(onClick = {
                val salt = prefs.getString("admin_pin_salt", "") ?: ""
                if (pinHash != null && constantTimeEquals(hashPin(oldPinInput, salt), pinHash!!)) { oldPinInput = ""; error = ""; navigateTo("pinchange") }
                else { oldPinInput = ""; error = "پین فعلی نادرست است." }
            }, modifier = Modifier.fillMaxWidth()) { Text("تأیید و ادامه") }
        }
        "mathrecovery" -> SettingsPage("بازیابی دسترسی مدیر", "برای بازیابی دسترسی، مسئلهٔ زیر را حل کنید.", { navigateBack("login") }) {
            val expected = if (mathOperation == "+") mathA + mathB else mathA - mathB
            Text("${mathA.toPersianDigits()} $mathOperation ${mathB.toPersianDigits()} = ؟", color = Forest, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = mathAnswer, onValueChange = { mathAnswer = it.filter(Char::isDigit).take(3) }, label = { Text("پاسخ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E))
            Button(onClick = {
                if (mathAnswer.toIntOrNull() == expected) { failedPins = 0; lockUntil = 0L; prefs.edit().putInt("pin_failed_attempts", 0).putLong("pin_lock_until", 0L).apply(); adminAuthenticated = true; newPin = ""; confirmPin = ""; error = ""; navigateTo("pinchange") }
                else { error = "پاسخ درست نیست. لطفاً دوباره تلاش کنید."; mathA = SecureRandom().nextInt(8) + 2; mathB = SecureRandom().nextInt(8) + 2; mathAnswer = "" }
            }, modifier = Modifier.fillMaxWidth()) { Text("تأیید و تعیین پین جدید") }
        }
        "pinchange" -> PinSetupScreen(newPin, { newPin = it.filter(Char::isDigit).take(12) }, confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, error) {
            if (newPin.length < 4) error = "پین باید حداقل ۴ رقم داشته باشد."
            else if (newPin != confirmPin) error = "دو پین یکسان نیستند."
            else { val salt = createSalt(); val h = hashPin(newPin, salt); prefs.edit().putString("admin_pin_salt", salt).putString("admin_pin_hash", h).apply(); pinHash = h; routeHistory = emptyList(); route = "dashboard"; error = "" }
        }
        "permissions" -> SettingsPage("دسترسی‌ها و نقش‌های برنامه", "هر دسترسی فقط برای یک قابلیت مشخص استفاده می‌شود. اگر فعلاً نمی‌خواهید اجازه‌ای بدهید، می‌توانید از این صفحه خارج شوید و بعداً دوباره اقدام کنید. ندادن بعضی دسترسی‌ها فقط همان قابلیت را محدود می‌کند.", { navigateBack("dashboard") }) {
            val contactsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val callGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            val writeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
            val usageGranted = hasUsageAccess(context)
            val installGranted = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
            val homeGranted = isHomeRoleHeld(context)
            val dialerGranted = isDialerRoleHeld(context)
            PermissionCard("نقش برنامه صفحه اصلی (لانچر) پیش‌فرض", "این نقش به اندروید می‌گوید پس از فشردن دکمهٔ خانه، صفحهٔ اصلی سهل‌بیت را نشان دهد. این نقش تماس‌ها یا اطلاعات شخصی شما را کنترل نمی‌کند.", "انتخاب سهل‌بیت به‌عنوان برنامه صفحه اصلی (لانچر) پیش‌فرض", homeGranted, onSelectLauncher)
            PermissionCard("نقش برنامهٔ تلفن پیش‌فرض", "برای آنکه رابط تماس اختصاصی سهل‌بیت هنگام تماس ورودی و خروجی نمایش داده شود، اندروید باید سهل‌بیت را به‌عنوان برنامهٔ تلفن پیش‌فرض بشناسد. در هر زمان می‌توانید برنامهٔ تلفن دیگری انتخاب کنید.", "انتخاب سهل‌بیت به‌عنوان برنامهٔ تلفن پیش‌فرض", dialerGranted, onSelectDialer)
            PermissionCard("دسترسی به مخاطبان", "در Android 6 و جدیدتر، برای خواندن دفترچه تلفن می‌توانید این اجازه را به‌صورت اختیاری فعال کنید. در Android 5، انتخاب مخاطب فقط از طریق انتخابگر سیستم انجام می‌شود و دسترسی گسترده‌ای به دفترچه تلفن درخواست نمی‌شود.", if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) "انتخاب مخاطب با انتخابگر سیستم" else "اعطای دسترسی به مخاطبان", contactsGranted, { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) else if (contactsGranted) android.widget.Toast.makeText(context, "دسترسی به مخاطبان قبلاً اعطا شده است.", android.widget.Toast.LENGTH_SHORT).show() else contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) })
            PermissionCard("اجازهٔ برقراری تماس", "این اجازه فقط برای شروع تماس مستقیم با شماره‌ای است که مدیر در صفحهٔ اصلی انتخاب کرده است. اگر اجازه داده نشود، می‌توان شماره‌گیر سیستم را باز کرد. در Android 5 همیشه از شماره‌گیر سیستم استفاده می‌شود.", "اعطای اجازهٔ تماس تلفنی", callGranted, { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) android.widget.Toast.makeText(context, "در Android 5 تماس از طریق شماره‌گیر سیستم انجام می‌شود.", android.widget.Toast.LENGTH_LONG).show() else if (callGranted) android.widget.Toast.makeText(context, "اجازهٔ تماس تلفنی قبلاً اعطا شده است.", android.widget.Toast.LENGTH_SHORT).show() else callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE) })
            PermissionCard("دسترسی آمار مصرف برنامه‌ها", "اندروید این دسترسی ویژه را برای مرتب‌سازی برنامه‌ها بر اساس مدت استفاده لازم دارد. سهل‌بیت فقط زمان استفادهٔ برنامه‌ها را می‌خواند و به محتوای پیام‌ها، عکس‌ها یا فایل‌های آن‌ها دسترسی پیدا نمی‌کند.", "فعال‌سازی دسترسی آمار استفاده", usageGranted, { openUsageAccessSettings(context) })
            PermissionCard("اجازهٔ نصب به‌روزرسانی‌ها", "این دسترسی ویژه فقط برای آن است که سهل‌بیت فایل APK نسخه‌ای را که مدیر از منبع رسمی انتخاب کرده است به نصب‌کنندهٔ اندروید تحویل دهد. بدون آن، دانلود و بررسی نسخه ممکن است اما نصب از داخل برنامه انجام نمی‌شود. تأیید نهایی نصب همیشه با مدیر است.", "اجازهٔ نصب برنامه‌های ناشناس", installGranted, { if (installGranted) android.widget.Toast.makeText(context, "اجازهٔ نصب برنامه‌ها قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show() else if (Build.VERSION.SDK_INT >= 26) runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))) } })
            PermissionCard("دسترسی تغییر تنظیمات سیستم", "این دسترسی برای اعمال زمان خاموش‌شدن نمایشگر و ذخیرهٔ زنگ تماس، اعلان و هشدار لازم است. این مجوز با مجوزهای عادی برنامه فرق دارد و باید در صفحهٔ ویژهٔ اندروید فعال شود.", "اعطای اجازهٔ تغییر تنظیمات سیستم", writeGranted, { if (writeGranted) android.widget.Toast.makeText(context, "دسترسی تغییر تنظیمات سیستم قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show() else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) } })
            Text("تنظیم بلندی صدا به مجوز جداگانه‌ای نیاز ندارد؛ سهل‌بیت از مسیر رسمی AudioManager استفاده می‌کند و تغییرها مستقیماً در صدای سیستم اندروید اعمال می‌شوند.", color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 20.sp)
            PermissionCard("دسترسی به سابقهٔ تماس‌ها", "این دسترسی فقط برای نمایش فهرست تماس‌های اخیر در صفحه‌ای است که خودتان انتخاب می‌کنید. تا زمانی که این قابلیت را فعال نکنید، سابقهٔ تماس‌ها خوانده نمی‌شود.", "اعطای دسترسی به سابقهٔ تماس‌ها", Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED, { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) recentCallsPermissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG) else android.widget.Toast.makeText(context, "دسترسی به سابقهٔ تماس‌ها قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show() })
        }
        "androidsettings" -> AndroidSettingsPage(onBack = { navigateBack("dashboard") }, onUninstall = { navigateTo("uninstall") })
        "uninstall" -> UninstallPage(onBack = { navigateBack("dashboard") }, onCallDeveloper = { dialerNumber = "09145880814"; navigateTo("dialer") })
        "about" -> SettingsPage("درباره سهل‌بیت", "", { navigateBack("dashboard") }) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF173B32)), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.sahlbit_logo_transparent), contentDescription = "لوگوی سهل‌بیت", modifier = Modifier.size(112.dp))
                    Text("سهل‌بیت", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(50)) { Text("نسخهٔ " + BuildConfig.VERSION_NAME, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
                }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("توسعه‌دهنده: امیرعلی پاشایی فرد", color = Ink, fontSize = 14.sp)
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pashaef.ir"))) } }, modifier = Modifier.fillMaxWidth()) { Text("وب‌سایت: PashaeF.ir", color = Ink) }
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Pashae@iran.ir"))) } }, modifier = Modifier.fillMaxWidth()) { Text("ایمیل: Pashae@iran.ir", color = Ink) }
                    OutlinedButton(onClick = { dialerNumber = "09145880814"; navigateTo("dialer") }, modifier = Modifier.fillMaxWidth()) { Text("شمارهٔ تماس: ۰۹۱۴۵۸۸۰۸۱۴", color = Ink) }
                }
            }
        }
        "recovery" -> SettingsPage("بازیابی سهل‌بیت", "اگر سهل‌بیت درست کار نمی‌کند، از دکمهٔ زیر برای انتخاب برنامه صفحه اصلی (لانچر) پیش‌فرض جایگزین استفاده کنید. سهل‌بیت به‌صورت خودکار برنامه صفحه اصلی (لانچر) پیش‌فرض تلفن همراه را تغییر نمی‌دهد.", { navigateBack("home") }) {
            Button(onClick = onSelectLauncher, modifier = Modifier.fillMaxWidth()) { Text("انتخاب برنامه صفحه اصلی (لانچر) پیش‌فرض اندروید / One UI Home") }
        }
    }
    if (showContactSourceDialog) {
        AlertDialog(onDismissRequest = { showContactSourceDialog = false }, title = { Text("انتخاب منبع مخاطب") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showContactSourceDialog = false; navigateTo("customcontacts") }, modifier = Modifier.fillMaxWidth()) { Text("مدیریت مخاطبین اختصاصی سهل‌بیت") }
                OutlinedButton(onClick = {
                    showContactSourceDialog = false
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    else { contactPickerPending = true; contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) }
                }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب از برنامهٔ مخاطبین اندروید") }
                if (customContacts.isNotEmpty()) Divider()
                customContacts.forEach { contact ->
                    OutlinedButton(onClick = { showContactSourceDialog = false; selectedCustomContactForPage = contact }, modifier = Modifier.fillMaxWidth()) { Text(contact.name) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showContactSourceDialog = false }) { Text("بستن") } })
    }
    if (selectedCustomContactForPage != null) {
        val contact = selectedCustomContactForPage!!
        AlertDialog(onDismissRequest = { selectedCustomContactForPage = null }, title = { Text("انتخاب شمارهٔ ${contact.name}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                contact.phones.forEach { phone -> OutlinedButton(onClick = { addCustomContactToPage(contact, phone); selectedCustomContactForPage = null }, modifier = Modifier.fillMaxWidth()) { Text(phone.number) } }
            }
        }, confirmButton = { TextButton(onClick = { selectedCustomContactForPage = null }) { Text("انصراف") } })
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
                    if (!source.isNullOrBlank()) {
                        val old = entries.firstOrNull { it.id == photoEntry.id }?.photoUri
                        entries = entries.map { if (it.id == photoEntry.id) it.copy(photoUri = null, photoSource = "contact", photoSourceUri = source) else it }
                        LauncherEntryStore.save(prefs, entries)
                        old?.takeIf { entries.none { e -> e.photoUri == old } }?.let { deleteOwnedContactPhoto(context, it) }
                    } else android.widget.Toast.makeText(context, "تصویر مخاطب در دفترچه تلفن موجود نیست", android.widget.Toast.LENGTH_LONG).show()
                    photoSourceDialogEntry = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (photoEntry.photoSource == "contact") "✓ " else "") + "استفاده از عکس مخاطب در برنامهٔ مخاطبین اندروید")
                }
                TextButton(onClick = {
                    selectedPhotoEntryId = photoEntry.id
                    photoSourceDialogEntry = null
                    galleryPicker.launch("image/*")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (photoEntry.photoSource == "gallery") "✓ " else "") + "انتخاب تصویر از گالری")
                }
                TextButton(onClick = {
                    selectedPhotoEntryId = photoEntry.id
                    photoSourceDialogEntry = null
                    cameraPicker.launch(null)
                }, modifier = Modifier.fillMaxWidth()) { Text("گرفتن عکس با دوربین") }
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
private fun AllAppsScreen(onBack: () -> Unit, onLaunch: (String) -> Unit, onDetails: (String) -> Unit) {
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
        PermissionCard("دسترسی آمار مصرف برنامه‌ها", "برای مرتب‌سازی برنامه‌ها بر اساس مدت استفاده در ۳۰ روز اخیر لازم است. این دسترسی ویژه در تنظیمات اندروید فعال می‌شود.", "فعال‌سازی دسترسی آمار مصرف", usageAccessGranted, { openUsageAccessSettings(context) })
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, label = { Text("جستجوی برنامه") }, leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f)) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "پاک‌کردن جستجو", tint = Forest) } })
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredApps, key = { it.activityInfo.packageName }) { item ->
                val pkg = item.activityInfo.packageName
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Color(0xFFD8C9A8), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.loadLabel(pm).toString(), color = Ink, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onDetails(pkg) }, modifier = Modifier.weight(1f)) { Text("جزئیات") }
                        Button(onClick = { onLaunch(pkg) }, modifier = Modifier.weight(1f)) { Text("اجرا") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSettingsPage(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    fun volume(stream: Int) = audio.getStreamVolume(stream).toFloat()
    fun maxVolume(stream: Int) = audio.getStreamMaxVolume(stream).coerceAtLeast(1).toFloat()
    var ring by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_RING)) }
    var notification by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_NOTIFICATION)) }
    var media by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_MUSIC)) }
    var system by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_SYSTEM)) }
    var voice by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_VOICE_CALL)) }
    var alarm by remember { mutableFloatStateOf(volume(android.media.AudioManager.STREAM_ALARM)) }
    var selectedType by remember { mutableIntStateOf(android.media.RingtoneManager.TYPE_RINGTONE) }
    fun refresh() {
        ring = volume(android.media.AudioManager.STREAM_RING)
        notification = volume(android.media.AudioManager.STREAM_NOTIFICATION)
        media = volume(android.media.AudioManager.STREAM_MUSIC)
        system = volume(android.media.AudioManager.STREAM_SYSTEM)
        voice = volume(android.media.AudioManager.STREAM_VOICE_CALL)
        alarm = volume(android.media.AudioManager.STREAM_ALARM)
    }
    val receiver = remember { object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    } }
    val host = context as? ComponentActivity
    DisposableEffect(host) {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)) {
                    prefs.getString("pending_ringtone_uri", null)?.let { raw ->
                        val type = prefs.getInt("pending_ringtone_type", android.media.RingtoneManager.TYPE_RINGTONE)
                        runCatching { android.media.RingtoneManager.setActualDefaultRingtoneUri(context, type, Uri.parse(raw)) }
                        prefs.edit().remove("pending_ringtone_uri").remove("pending_ringtone_type").apply()
                    }
                }
            }
        }
        host?.lifecycle?.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            host?.lifecycle?.removeObserver(observer)
        }
    }
    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.extras?.get(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
        if (uri != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)) {
                runCatching { android.media.RingtoneManager.setActualDefaultRingtoneUri(context, selectedType, uri) }
                    .onSuccess { android.widget.Toast.makeText(context, "زنگ انتخاب‌شده ذخیره شد.", android.widget.Toast.LENGTH_SHORT).show() }
                    .onFailure { android.widget.Toast.makeText(context, "ذخیرهٔ زنگ ناموفق بود؛ دسترسی تغییر تنظیمات سیستم را بررسی کنید.", android.widget.Toast.LENGTH_LONG).show() }
            } else {
                prefs.edit().putString("pending_ringtone_uri", uri.toString()).putInt("pending_ringtone_type", selectedType).apply()
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
            }
        }
    }
    fun pick(type: Int, title: String) {
        selectedType = type
        pickerLauncher.launch(Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.media.RingtoneManager.getActualDefaultRingtoneUri(context, type)))
    }
    SettingsPage("صدا و زنگ‌ها", "این نوارها مستقیماً صدای سیستم اندروید را تغییر می‌دهند. تغییر صدا از دکمه‌های فیزیکی یا تنظیمات اندروید نیز هنگام بازگشت در این صفحه منعکس می‌شود.", onBack) {
        val systemWriteGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
        PermissionCard("دسترسی تغییر تنظیمات سیستم", "برای ذخیرهٔ زنگ تماس، اعلان و هشدار لازم است. این دسترسی فقط با تأیید شما در تنظیمات اندروید فعال می‌شود.", "اعطای دسترسی تغییر تنظیمات سیستم", systemWriteGranted, { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) })
        Text("صدای زنگ تماس: ${ring.toInt().toPersianDigits()} از ${maxVolume(android.media.AudioManager.STREAM_RING).toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(ring, { ring = it; audio.setStreamVolume(android.media.AudioManager.STREAM_RING, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_RING))
        Text("صدای اعلان‌ها: ${notification.toInt().toPersianDigits()} از ${maxVolume(android.media.AudioManager.STREAM_NOTIFICATION).toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(notification, { notification = it; audio.setStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_NOTIFICATION))
        Text("صدای رسانه: ${media.toInt().toPersianDigits()} از ${maxVolume(android.media.AudioManager.STREAM_MUSIC).toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(media, { media = it; audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_MUSIC))
        Text("صدای سیستم: ${system.toInt().toPersianDigits()} از ${maxVolume(android.media.AudioManager.STREAM_SYSTEM).toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(system, { system = it; audio.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_SYSTEM))
        Text("صدای مکالمه: ${voice.toInt().toPersianDigits()} از ${maxVolume(android.media.AudioManager.STREAM_VOICE_CALL).toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(voice, { voice = it; audio.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_VOICE_CALL))
        Text("صدای زنگ هشدار: ${alarm.toInt().toPersianDigits()} از ${maxVolume(android.media.AudioManager.STREAM_ALARM).toInt().toPersianDigits()}", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(alarm, { alarm = it; audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, it.toInt(), 0) }, valueRange = 0f..maxVolume(android.media.AudioManager.STREAM_ALARM))
        Divider()
        OutlinedButton(onClick = { pick(android.media.RingtoneManager.TYPE_RINGTONE, "انتخاب زنگ تماس") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب زنگ تماس") }
        OutlinedButton(onClick = { pick(android.media.RingtoneManager.TYPE_NOTIFICATION, "انتخاب صدای اعلان") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب صدای اعلان") }
        OutlinedButton(onClick = { pick(android.media.RingtoneManager.TYPE_ALARM, "انتخاب زنگ هشدار") }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب زنگ هشدار") }
        Text("برای ذخیرهٔ زنگ انتخاب‌شده، اندروید اجازهٔ تغییر تنظیمات سیستمی را درخواست می‌کند.", color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 20.sp)
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context))
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))) }
            else android.widget.Toast.makeText(context, "دسترسی تغییر تنظیمات سیستم قبلاً فعال شده است.", android.widget.Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("اعطای اجازهٔ تغییر تنظیمات سیستم برای مدیریت انتخاب زنگ") }
    }
}

@Composable
private fun SetupWizardScreen(step: Int, granted: Boolean?, onGrant: () -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val titles = listOf("خوش‌آمدگویی", "انتخاب برنامه صفحه اصلی (لانچر) پیش‌فرض", "اجازهٔ تغییر تنظیمات سیستم", "انتخاب برنامهٔ تلفن پیش‌فرض", "دسترسی به مخاطبان", "اجازهٔ برقراری تماس", "دسترسی آمار مصرف برنامه‌ها", "اجازهٔ نصب به‌روزرسانی‌های سهل‌بیت")
    val descriptions = listOf(
        "به سهل‌بیت خوش آمدید. سهل‌بیت صفحهٔ اصلی ساده‌ای برای دسترسی آسان به مخاطبان و برنامه‌های مهم فراهم می‌کند. در این راه‌اندازی هر بار یک نقش یا دسترسی را می‌بینید. می‌توانید هر مرحله را رد کنید و بعداً از بخش مجوزها آن را انجام دهید.",
        "با انتخاب سهل‌بیت به‌عنوان برنامه صفحه اصلی (لانچر) پیش‌فرض، پس از فشردن دکمهٔ خانه صفحهٔ اصلی سهل‌بیت باز می‌شود. این انتخاب قابل بازگشت است و هر زمان می‌توانید برنامه صفحه اصلی (لانچر) پیش‌فرض دیگری را در تنظیمات اندروید انتخاب کنید.",
        "این اجازه برای تغییر زمان خاموش‌شدن نمایشگر و ذخیرهٔ زنگ تماس، اعلان و هشدار لازم است. اندروید صفحه‌ای جداگانه باز می‌کند؛ نام سهل‌بیت را پیدا کنید و اجازهٔ تغییر تنظیمات سیستم را روشن کنید.",
        "برای نمایش رابط تماس اختصاصی سهل‌بیت هنگام تماس ورودی و خروجی لازم است سهل‌بیت برنامهٔ تلفن پیش‌فرض باشد. با تأیید این نقش در پنجرهٔ اندروید، ممکن است خود سیستم‌عامل برخی مجوزهای مرتبط مانند دسترسی به مخاطبان یا برقراری تماس را نیز به‌عنوان بخشی از نقش تلفن اعطا کند؛ سهل‌بیت این مجوزها را پنهانی درخواست یا فعال نمی‌کند. پیش از تأیید، توضیحات و مجوزهای نمایش‌داده‌شده در اندروید را بررسی کنید. این انتخاب هر زمان قابل تغییر است. اندروید ۵ از رابط تماس اختصاصی پشتیبانی نمی‌کند.",
        "این اجازه برای خواندن نام، شماره و تصویر مخاطبان از دفترچه تلفن لازم است تا مدیر بتواند افراد موردنظر را به صفحهٔ اصلی اضافه کند. سهل‌بیت مخاطبان را حذف یا ویرایش نمی‌کند.",
        "این اجازه برای تماس مستقیم با مخاطبی که مدیر روی صفحهٔ اصلی انتخاب کرده است استفاده می‌شود. اگر اجازه داده نشود، سهل‌بیت می‌تواند شماره‌گیر سیستم را باز کند و تماس از آنجا انجام شود.",
        "این دسترسی ویژه به سهل‌بیت اجازه می‌دهد مدت استفاده از برنامه‌ها را بخواند تا برنامه‌های پراستفاده را زودتر نشان دهد. محتوای پیام‌ها، عکس‌ها و فایل‌ها خوانده نمی‌شود و این دسترسی اختیاری است.",
        "این اجازه به سهل‌بیت امکان می‌دهد فایل APK به‌روزرسانی را که مدیر از وب‌سایت رسمی یا GitHub انتخاب کرده است به نصب‌کنندهٔ اندروید تحویل دهد. اندروید یک صفحهٔ ویژه باز می‌کند؛ گزینهٔ «اجازه از این منبع» را برای سهل‌بیت روشن کنید. نصب نهایی همچنان با تأیید مدیر انجام می‌شود و برنامه بدون اجازهٔ شما به‌روزرسانی نمی‌شود."
    )
    Column(Modifier.fillMaxSize().background(Cream).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (step == 0) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(132.dp).clip(CircleShape).background(Color(0xFF2563EB)), contentAlignment = Alignment.Center) { Image(painterResource(R.drawable.sahlbit_logo_transparent), contentDescription = "لوگوی سهل‌بیت", modifier = Modifier.size(88.dp)) }
            Text("به سهل‌بیت خوش آمدید", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null); Spacer(Modifier.width(7.dp)); Text("صفحهٔ قبلی") }
                Spacer(Modifier.weight(1f))
                Text("مرحلهٔ ${step.toPersianDigits()} از ۷", color = Teal, fontWeight = FontWeight.Bold)
            }
            Text(titles[step], color = Forest, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(descriptions[step], color = Ink, fontSize = 16.sp, lineHeight = 27.sp)
                if (step > 0) {
                    Text("اگر اکنون به هر دلیل نتوانستید این اجازه را بدهید، اشکالی ندارد؛ بعداً از بخش «دسترسی‌ها و نقش‌های برنامه» در تنظیمات سهل‌بیت می‌توانید آن را فعال کنید.", color = Color(0xFF64748B), fontSize = 13.sp, lineHeight = 22.sp)
                    Text(when (granted) { true -> "این دسترسی فعال شده است."; false -> "این دسترسی هنوز فعال نشده است."; null -> "وضعیت این دسترسی در حال حاضر قابل بررسی نیست." }, color = when (granted) { true -> Color(0xFF168A45); false -> Color(0xFFB45309); null -> Color(0xFF64748B) }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (granted == true) "بررسی تنظیم مربوط" else "رفتن به صفحهٔ اعطای اجازه") }
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text(if (step == 7) "پایان مراحل راه‌اندازی" else if (step == 0) "شروع راه‌اندازی" else when (granted) { true -> "ادامه به مرحله بعد"; false -> "ادامه بدون اعطای اجازه"; null -> "ادامه به مرحله بعدی" })
        }
        if (step > 0) Text("می‌توانید مراحل را بدون اعطای دسترسی ورق بزنید؛ هیچ مرحله‌ای شما را مجبور به فعال‌سازی مجوز نمی‌کند.", color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 20.sp)
    }
}

@Composable private fun PinSetupScreen(pin: String, onPin: (String) -> Unit, confirm: String, onConfirm: (String) -> Unit, error: String, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 40.dp, bottom = 12.dp), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("تنظیم پین مدیر", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(6.dp)); Text("لطفاً یک پین عددی با حداقل ۴ رقم انتخاب کنید. این پین را فقط در اختیار مدیر دستگاه قرار دهید.", color = Ink, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp)); PinField("پین جدید", pin, onPin); Spacer(Modifier.height(8.dp)); PinField("تکرار پین", confirm, onConfirm)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp)); Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("تنظیم پین") }
    }
}
@Composable private fun PinLoginScreen(pin: String, onPin: (String) -> Unit, error: String, onBack: () -> Unit, onForgot: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onBack, modifier = Modifier.size(84.dp)) { Icon(Icons.Default.Close, contentDescription = "بازگشت به صفحه اصلی", tint = Color(0xFFDC2626), modifier = Modifier.size(58.dp)) }
        Text("ورود مدیر", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest)
        Spacer(Modifier.height(14.dp)); PinField("پین مدیر", pin, onPin)
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("ورود به پیشخان مدیریت") }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("پین را فراموش کرده‌ام") }
    }
}
@Composable private fun PinField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable
private fun CallCustomizationPage(prefs: android.content.SharedPreferences, contacts: List<LauncherEntry>, callPermissionGranted: Boolean, onRequestCallPermission: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf("incoming") }
    var previewName by remember { mutableStateOf("مخاطب نمونه") }
    var previewNumber by remember { mutableStateOf("۰۹۱۲۱۲۳۴۵۶۷") }
    var selectedContactId by remember { mutableStateOf<String?>(null) }
    var previewPhotoUri by remember { mutableStateOf<String?>(null) }
    var contactMenuExpanded by remember { mutableStateOf(false) }
    var previewSpeakerOn by remember { mutableStateOf(false) }
    val prefix = "call_${profile}_"
    fun intValue(key: String, fallback: Int) = prefs.getInt(prefix + key, fallback)
    fun textValue(key: String, fallback: String) = prefs.getString(prefix + key, fallback) ?: fallback
    var avatarSize by remember(profile) { mutableIntStateOf(intValue("avatar_dp", 250)) }
    var avatarRadius by remember(profile) { mutableIntStateOf(intValue("avatar_radius", 20)) }
    var nameSize by remember(profile) { mutableIntStateOf(intValue("name_sp", 27)) }
    var numberSize by remember(profile) { mutableIntStateOf(intValue("number_sp", 20)) }
    var buttonHeight by remember(profile) { mutableIntStateOf(intValue("button_height_dp", 82)) }
    var buttonWidthPct by remember(profile) { mutableIntStateOf(intValue("button_width_pct", 100)) }
    var buttonTextSize by remember(profile) { mutableIntStateOf(intValue("button_text_sp", 22)) }
    var buttonRadius by remember(profile) { mutableIntStateOf(intValue("button_radius", 20)) }
    var spacing by remember(profile) { mutableIntStateOf(intValue("spacing_dp", 88)) }
    var bgHex by remember(profile) { mutableStateOf(textValue("background", if (profile == "incoming") "#6B9FC9" else if (profile == "outgoing") "#FFC94D" else "#2E7D32")) }
    var primaryHex by remember(profile) { mutableStateOf(textValue("primary", when (profile) { "active" -> "#1976D2"; "outgoing" -> "#BE232D"; else -> "#168A45" })) }
    var speakerOnHex by remember(profile) { mutableStateOf(textValue("speaker_on", "#FFC107")) }
    var speakerOffHex by remember(profile) { mutableStateOf(textValue("speaker_off", "#1976D2")) }
    var secondaryHex by remember(profile) { mutableStateOf(textValue("secondary", "#BE232D")) }
    var firstLabel by remember(profile) { mutableStateOf(textValue("first_label", if (profile == "incoming") "پاسخ به تماس" else if (profile == "outgoing") "پایان تماس" else "تغییر به بلندگو")) }
    var secondLabel by remember(profile) { mutableStateOf(textValue("second_label", if (profile == "incoming") "رد تماس" else "پایان تماس")) }
    var alternateLabel by remember(profile) { mutableStateOf(textValue("alternate_label", "تغییر به تلفن")) }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(prefix + key, value).apply() }
    fun saveText(key: String, value: String) { prefs.edit().putString(prefix + key, value).apply() }
    val palette = listOf("#6B9FC9", "#2E7D32", "#FFC94D", "#172033", "#F1F5F9", "#FFFFFF", "#168A45", "#BE232D", "#1976D2", "#455A64")
    SettingsPage("سفارشی‌سازی صفحه تماس", "سه وضعیت تماس را جداگانه تنظیم کنید. تغییرها بلافاصله ذخیره می‌شوند. پیش‌نمایش پایین صفحه اندازه‌ها، فاصله‌ها، گوشه‌ها و رنگ‌ها را نشان می‌دهد.", onBack) {
        PermissionCard("اجازهٔ برقراری تماس", "برای شروع تماس مستقیم از صفحهٔ اصلی لازم است. این دسترسی فقط پس از تأیید شما در پنجرهٔ اندروید فعال می‌شود.", "اعطای اجازهٔ برقراری تماس", callPermissionGranted, onRequestCallPermission)
        Text("وضعیت موردنظر برای ویرایش", color = Ink, fontWeight = FontWeight.Bold)
        val statusChipScroll = rememberScrollState()
        LaunchedEffect(profile) { if (profile == "incoming") statusChipScroll.animateScrollTo(0) else statusChipScroll.animateScrollTo(statusChipScroll.maxValue) }
        Row(Modifier.fillMaxWidth().horizontalScroll(statusChipScroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(profile == "incoming", { profile = "incoming" }, label = { Text("تماس ورودی قبل از برقراری مکالمه", fontSize = 11.sp) })
            FilterChip(profile == "outgoing", { profile = "outgoing" }, label = { Text("تماس خروجی قبل از برقراری مکالمه", fontSize = 11.sp) })
            FilterChip(profile == "active", { profile = "active" }, label = { Text("تماس ورودی و خروجی پس از برقراری مکالمه", fontSize = 11.sp) })
        }
        Text("مخاطب پیش‌نمایش", color = Ink, fontWeight = FontWeight.Bold)
        if (contacts.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { contactMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(contacts.firstOrNull { it.id == selectedContactId }?.let { "${it.title} — ${it.phoneNumber.orEmpty()}" } ?: "انتخاب یکی از مخاطبان ذخیره‌شده", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = contactMenuExpanded, onDismissRequest = { contactMenuExpanded = false }) {
                    contacts.groupBy { it.page }.toSortedMap().forEach { (page, pageContacts) ->
                        DropdownMenuItem(text = { Text("صفحهٔ ${(page + 1).toPersianDigits()}", fontWeight = FontWeight.Bold) }, enabled = false, onClick = {})
                        pageContacts.forEach { contact ->
                        DropdownMenuItem(text = { Text("${contact.title} — ${contact.phoneNumber.orEmpty()}") }, onClick = {
                            selectedContactId = contact.id
                            previewName = contact.title
                            previewNumber = contact.phoneNumber.orEmpty()
                            previewPhotoUri = if (contact.photoSource == "contact") contact.photoSourceUri else contact.photoUri
                            contactMenuExpanded = false
                        })
                        }
                    }
                }
            }
        } else Text("هنوز مخاطبی در صفحهٔ اصلی ذخیره نشده است؛ نام و شمارهٔ نمونه را می‌توانید دستی وارد کنید.", color = Color(0xFF64748B), fontSize = 12.sp)
        PreviewPhotoPicker(previewPhotoUri) { previewPhotoUri = it }
        OutlinedTextField(previewName, { previewName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام مخاطب") }, singleLine = true)
        OutlinedTextField(previewNumber, { previewNumber = it }, modifier = Modifier.fillMaxWidth(), label = { Text("شماره تماس") }, singleLine = true)
        Text("اندازه تصویر مخاطب: ${avatarSize.toPersianDigits()} dp", color = Ink)
        CompactSlider(avatarSize.toFloat(), { avatarSize = it.toInt(); saveInt("avatar_dp", avatarSize) }, valueRange = 140f..340f)
        Text("گردی گوشه‌های تصویر: ${avatarRadius.toPersianDigits()} dp", color = Ink)
        CompactSlider(avatarRadius.toFloat(), { avatarRadius = it.toInt(); saveInt("avatar_radius", avatarRadius) }, valueRange = 0f..40f)
        Text("اندازه نام مخاطب: ${nameSize.toPersianDigits()} sp", color = Ink)
        CompactSlider(nameSize.toFloat(), { nameSize = it.toInt(); saveInt("name_sp", nameSize) }, valueRange = 16f..40f)
        Text("اندازه شماره تماس: ${numberSize.toPersianDigits()} sp", color = Ink)
        CompactSlider(numberSize.toFloat(), { numberSize = it.toInt(); saveInt("number_sp", numberSize) }, valueRange = 12f..32f)
        Text("ارتفاع دکمه‌ها: ${buttonHeight.toPersianDigits()} dp", color = Ink)
        CompactSlider(buttonHeight.toFloat(), { buttonHeight = it.toInt(); saveInt("button_height_dp", buttonHeight) }, valueRange = 56f..120f)
        Text("عرض دکمه‌ها: ${buttonWidthPct.toPersianDigits()}٪", color = Ink)
        CompactSlider(buttonWidthPct.toFloat(), { buttonWidthPct = it.toInt(); saveInt("button_width_pct", buttonWidthPct) }, valueRange = 60f..100f)
        Text("اندازه نوشته دکمه‌ها: ${buttonTextSize.toPersianDigits()} sp", color = Ink)
        CompactSlider(buttonTextSize.toFloat(), { buttonTextSize = it.toInt(); saveInt("button_text_sp", buttonTextSize) }, valueRange = 14f..30f)
        Text("گردی گوشه دکمه‌ها: ${buttonRadius.toPersianDigits()} dp", color = Ink)
        CompactSlider(buttonRadius.toFloat(), { buttonRadius = it.toInt(); saveInt("button_radius", buttonRadius) }, valueRange = 0f..40f)
        Text(if (profile == "outgoing") "فاصلهٔ دکمهٔ پایان تماس از اطلاعات تماس: ${spacing.toPersianDigits()} dp" else "فاصله عمودی بین دکمه‌ها: ${spacing.toPersianDigits()} dp", color = Ink)
        CompactSlider(spacing.toFloat(), { spacing = it.toInt(); saveInt("spacing_dp", spacing) }, valueRange = 8f..120f)
        CallColorPicker("رنگ پس‌زمینه صفحه", bgHex, palette) { bgHex = it; saveText("background", it) }
        if (profile == "outgoing") {
            CallColorPicker("رنگ دکمه پایان تماس", primaryHex, palette) { primaryHex = it; saveText("primary", it) }
        } else if (profile == "active") {
            CallColorPicker("رنگ دکمهٔ بالایی در حالت تغییر به بلندگو", speakerOffHex, palette) { speakerOffHex = it; saveText("speaker_off", it) }
            CallColorPicker("رنگ دکمهٔ بالایی در حالت تغییر به تلفن", speakerOnHex, palette) { speakerOnHex = it; saveText("speaker_on", it) }
            CallColorPicker("رنگ دکمهٔ پایینی", secondaryHex, palette) { secondaryHex = it; saveText("secondary", it) }
        } else {
            CallColorPicker("رنگ دکمه بالایی", primaryHex, palette) { primaryHex = it; saveText("primary", it) }
            CallColorPicker("رنگ دکمه پایینی", secondaryHex, palette) { secondaryHex = it; saveText("secondary", it) }
        }
        Text("متن دکمه‌ها", color = Ink, fontWeight = FontWeight.Bold)
        OutlinedTextField(firstLabel, { firstLabel = it; saveText("first_label", it) }, modifier = Modifier.fillMaxWidth(), label = { Text(if (profile == "incoming") "متن پاسخ" else if (profile == "outgoing") "متن دکمه پایان تماس" else "متن تغییر به بلندگو") }, singleLine = true)
        if (profile != "outgoing") OutlinedTextField(secondLabel, { secondLabel = it; saveText("second_label", it) }, modifier = Modifier.fillMaxWidth(), label = { Text(if (profile == "incoming") "متن رد تماس" else "متن پایان تماس") }, singleLine = true)
        if (profile == "active") OutlinedTextField(alternateLabel, { alternateLabel = it; saveText("alternate_label", it) }, modifier = Modifier.fillMaxWidth(), label = { Text("متن تغییر به تلفن") }, singleLine = true)
        Divider()
        Text("پیش‌نمایش زنده", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        val bgColor = Color(android.graphics.Color.parseColor(bgHex))
        val primaryColor = Color(android.graphics.Color.parseColor(primaryHex))
        val activeSpeakerColor = if (previewSpeakerOn) Color(android.graphics.Color.parseColor(speakerOnHex)) else Color(android.graphics.Color.parseColor(speakerOffHex))
        val secondaryColor = Color(android.graphics.Color.parseColor(secondaryHex))
        fun readableTextColor(colorHex: String): Color {
            val value = android.graphics.Color.parseColor(colorHex)
            val lum = (android.graphics.Color.red(value) * 0.299f + android.graphics.Color.green(value) * 0.587f + android.graphics.Color.blue(value) * 0.114f) / 255f
            return if (lum > 0.62f) Ink else Color.White
        }
        val primaryTextColor = readableTextColor(primaryHex)
        val secondaryTextColor = readableTextColor(secondaryHex)
        val previewBgInt = android.graphics.Color.parseColor(bgHex)
        val previewLuminance = (android.graphics.Color.red(previewBgInt) * 0.299f + android.graphics.Color.green(previewBgInt) * 0.587f + android.graphics.Color.blue(previewBgInt) * 0.114f) / 255f
        val previewForeground = if (previewLuminance > 0.62f) Ink else Color.White
        val previewAvatarSize = minOf(avatarSize, (LocalConfiguration.current.screenWidthDp - 56).coerceAtLeast(140))
        val previewBitmap = remember(previewPhotoUri) {
            previewPhotoUri?.let { uri -> runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (profile) { "incoming" -> "تماس ورودی"; "outgoing" -> "در حال شماره‌گیری"; else -> "تماس برقرار است" }, color = previewForeground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.size(previewAvatarSize.dp).clip(RoundedCornerShape(avatarRadius.dp)).background(Color.White.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    if (previewBitmap != null) Image(previewBitmap, contentDescription = previewName, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(avatarRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, tint = previewForeground, modifier = Modifier.size(minOf(avatarSize / 2, 110).dp))
                }
                Text(previewName.ifBlank { "مخاطب نمونه" }, color = previewForeground, fontSize = nameSize.sp, fontWeight = FontWeight.Bold)
                Text(previewNumber, color = previewForeground, fontSize = numberSize.sp)
                if (profile == "incoming") {
                    Spacer(Modifier.height(2.dp))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(buttonWidthPct / 100f).height(buttonHeight.dp), shape = RoundedCornerShape(buttonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = primaryTextColor)) { Text("☎  $firstLabel", fontSize = buttonTextSize.sp) }
                    Spacer(Modifier.height((spacing - 8).coerceAtLeast(0).dp))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(buttonWidthPct / 100f).height(buttonHeight.dp), shape = RoundedCornerShape(buttonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = secondaryColor, contentColor = secondaryTextColor)) { Text("✕  $secondLabel", fontSize = buttonTextSize.sp) }
                } else if (profile == "outgoing") {
                    Spacer(Modifier.height((spacing - 8).coerceAtLeast(0).dp))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(buttonWidthPct / 100f).height(buttonHeight.dp), shape = RoundedCornerShape(buttonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = primaryTextColor)) { Text("✕  $firstLabel", fontSize = buttonTextSize.sp) }
                } else {
                    Spacer(Modifier.height(26.dp))
                    Button(onClick = { previewSpeakerOn = !previewSpeakerOn }, modifier = Modifier.fillMaxWidth(buttonWidthPct / 100f).height(buttonHeight.dp), shape = RoundedCornerShape(buttonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = activeSpeakerColor, contentColor = Color.White)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.size(minOf(46.dp, (buttonHeight - 16).coerceAtLeast(36).dp)).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                Text(if (previewSpeakerOn) "☎" else "🔊", color = Color(0xFF1976D2), fontSize = 27.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(if (previewSpeakerOn) alternateLabel else firstLabel, fontSize = buttonTextSize.sp)
                        }
                    }
                    Spacer(Modifier.height((spacing - 8).coerceAtLeast(0).dp))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(buttonWidthPct / 100f).height(buttonHeight.dp), shape = RoundedCornerShape(buttonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = secondaryColor, contentColor = secondaryTextColor)) { Text("✕  $secondLabel", fontSize = buttonTextSize.sp) }
                }
            }
        }
    }
}

@Composable
private fun CallConfirmationCustomizationPage(prefs: android.content.SharedPreferences, contacts: List<LauncherEntry>, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = listOf("#168A45", "#B3261E", "#1976D2", "#455A64", "#6B9FC9", "#2E7D32", "#FFC94D", "#172033", "#FFFFFF", "#BE232D")
    fun intValue(key: String, fallback: Int) = prefs.getInt("confirm_call_$key", fallback)
    fun textValue(key: String, fallback: String) = prefs.getString("confirm_call_$key", fallback) ?: fallback
    var yesColor by remember { mutableStateOf(textValue("yes_color", "#168A45")) }
    var noColor by remember { mutableStateOf(textValue("no_color", "#B3261E")) }
    var yesLabel by remember { mutableStateOf(textValue("yes_label", "بله، تماس بگیرید")) }
    var noLabel by remember { mutableStateOf(textValue("no_label", "خیر")) }
    var yesButtonHeight by remember { mutableIntStateOf(intValue("yes_button_height", intValue("button_height", 62)).coerceIn(48, 88)) }
    var noButtonHeight by remember { mutableIntStateOf(intValue("no_button_height", intValue("button_height", 62)).coerceIn(48, 88)) }
    var yesButtonTextSize by remember { mutableIntStateOf(intValue("yes_text_size", intValue("button_text_size", 17)).coerceIn(14, 24)) }
    var noButtonTextSize by remember { mutableIntStateOf(intValue("no_text_size", intValue("button_text_size", 17)).coerceIn(14, 24)) }
    var yesButtonRadius by remember { mutableIntStateOf(intValue("yes_button_radius", intValue("button_radius", 16)).coerceIn(0, 40)) }
    var noButtonRadius by remember { mutableIntStateOf(intValue("no_button_radius", intValue("button_radius", 16)).coerceIn(0, 40)) }
    var yesButtonWidth by remember { mutableIntStateOf(intValue("yes_width_pct", 100).coerceIn(50, 100)) }
    var noButtonWidth by remember { mutableIntStateOf(intValue("no_width_pct", 62).coerceIn(50, 100)) }
    var avatarSize by remember { mutableIntStateOf(intValue("avatar_size", 112).coerceIn(64, 160)) }
    var avatarRadius by remember { mutableIntStateOf(intValue("avatar_radius", 20).coerceIn(0, 40)) }
    var buttonRadius by remember { mutableIntStateOf(intValue("button_radius", 16).coerceIn(0, 40)) }
    var buttonSpacing by remember { mutableIntStateOf(intValue("button_spacing", 50).coerceIn(8, 100)) }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt("confirm_call_$key", value).apply() }
    fun saveText(key: String, value: String) { prefs.edit().putString("confirm_call_$key", value).apply() }

    SettingsPage("سفارشی‌سازی کادر تأیید تماس", "ظاهر پنجره‌ای را تنظیم کنید که پس از انتخاب کارت مخاطب نمایش داده می‌شود. تغییرها بلافاصله ذخیره می‌شوند.", onBack) {
        var selectedPreviewContactId by remember { mutableStateOf(contacts.firstOrNull()?.id) }
        var previewContactMenu by remember { mutableStateOf(false) }
        var sampleContactName by remember { mutableStateOf(contacts.firstOrNull()?.title ?: "مخاطب نمونه") }
        var sampleContactNumber by remember { mutableStateOf(contacts.firstOrNull()?.phoneNumber ?: "۰۹۱۲۰۰۰۰۰۰۰۰") }
        var previewPhotoUri by remember { mutableStateOf(contacts.firstOrNull()?.let { if (it.photoSource == "contact") it.photoSourceUri else it.photoUri }) }
        if (contacts.isNotEmpty()) {
            OutlinedButton(onClick = { previewContactMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(contacts.firstOrNull { it.id == selectedPreviewContactId }?.let { "${it.title} — ${it.phoneNumber.orEmpty()}" } ?: "انتخاب مخاطب ذخیره‌شده", modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = previewContactMenu, onDismissRequest = { previewContactMenu = false }) {
                contacts.groupBy { it.page }.toSortedMap().forEach { (page, pageContacts) ->
                    DropdownMenuItem(text = { Text("صفحهٔ ${(page + 1).toPersianDigits()}", fontWeight = FontWeight.Bold) }, enabled = false, onClick = {})
                    pageContacts.forEach { contact -> DropdownMenuItem(text = { Text("${contact.title} — ${contact.phoneNumber.orEmpty()}") }, onClick = { selectedPreviewContactId = contact.id; sampleContactName = contact.title; sampleContactNumber = contact.phoneNumber.orEmpty(); previewPhotoUri = if (contact.photoSource == "contact") contact.photoSourceUri else contact.photoUri; previewContactMenu = false }) }
                }
            }
        }
        PreviewPhotoPicker(previewPhotoUri) { previewPhotoUri = it }
        OutlinedTextField(sampleContactName, { sampleContactName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام مخاطب پیش‌نمایش") }, singleLine = true)
        OutlinedTextField(sampleContactNumber, { sampleContactNumber = it }, modifier = Modifier.fillMaxWidth(), label = { Text("شمارهٔ تماس پیش‌نمایش") }, singleLine = true)
        CallColorPicker("رنگ دکمهٔ بله", yesColor, palette) { yesColor = it; saveText("yes_color", it) }
        CallColorPicker("رنگ دکمهٔ خیر", noColor, palette) { noColor = it; saveText("no_color", it) }
        OutlinedTextField(yesLabel, { yesLabel = it; saveText("yes_label", it) }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان دکمهٔ بله") }, singleLine = true)
        OutlinedTextField(noLabel, { noLabel = it; saveText("no_label", it) }, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان دکمهٔ خیر") }, singleLine = true)
        Text("ارتفاع دکمهٔ بله: ${yesButtonHeight.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(yesButtonHeight.toFloat(), { yesButtonHeight = it.toInt().coerceIn(48, 88); saveInt("yes_button_height", yesButtonHeight) }, 48f..88f)
        Text("ارتفاع دکمهٔ خیر: ${noButtonHeight.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(noButtonHeight.toFloat(), { noButtonHeight = it.toInt().coerceIn(48, 88); saveInt("no_button_height", noButtonHeight) }, 48f..88f)
        Text("عرض دکمهٔ بله: ${yesButtonWidth.toPersianDigits()} درصد", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(yesButtonWidth.toFloat(), { yesButtonWidth = it.toInt().coerceIn(50, 100); saveInt("yes_width_pct", yesButtonWidth) }, 50f..100f)
        Text("عرض دکمهٔ خیر: ${noButtonWidth.toPersianDigits()} درصد", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(noButtonWidth.toFloat(), { noButtonWidth = it.toInt().coerceIn(50, 100); saveInt("no_width_pct", noButtonWidth) }, 50f..100f)
        Text("اندازهٔ متن دکمهٔ بله: ${yesButtonTextSize.toPersianDigits()} sp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(yesButtonTextSize.toFloat(), { yesButtonTextSize = it.toInt().coerceIn(14, 24); saveInt("yes_text_size", yesButtonTextSize) }, 14f..24f)
        Text("اندازهٔ متن دکمهٔ خیر: ${noButtonTextSize.toPersianDigits()} sp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(noButtonTextSize.toFloat(), { noButtonTextSize = it.toInt().coerceIn(14, 24); saveInt("no_text_size", noButtonTextSize) }, 14f..24f)
        Text("اندازهٔ تصویر مخاطب: ${avatarSize.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(avatarSize.toFloat(), { avatarSize = it.toInt().coerceIn(64, 160); saveInt("avatar_size", avatarSize) }, 64f..160f)
        Text("گردی تصویر مخاطب: ${avatarRadius.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(avatarRadius.toFloat(), { avatarRadius = it.toInt().coerceIn(0, 40); saveInt("avatar_radius", avatarRadius) }, 0f..40f)
        Text("گردی گوشهٔ دکمهٔ بله: ${yesButtonRadius.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(yesButtonRadius.toFloat(), { yesButtonRadius = it.toInt().coerceIn(0, 40); saveInt("yes_button_radius", yesButtonRadius) }, 0f..40f)
        Text("گردی گوشهٔ دکمهٔ خیر: ${noButtonRadius.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(noButtonRadius.toFloat(), { noButtonRadius = it.toInt().coerceIn(0, 40); saveInt("no_button_radius", noButtonRadius) }, 0f..40f)
        Text("فاصلهٔ عمودی دکمه‌ها: ${buttonSpacing.toPersianDigits()} dp", color = Ink, fontWeight = FontWeight.SemiBold)
        CompactSlider(buttonSpacing.toFloat(), { buttonSpacing = it.toInt().coerceIn(8, 100); saveInt("button_spacing", buttonSpacing) }, 8f..100f)
        Divider()
        Text("پیش‌نمایش زنده", color = Forest, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        val previewImage = remember(previewPhotoUri) { previewPhotoUri?.let { raw -> runCatching { context.contentResolver.openInputStream(Uri.parse(raw))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (previewImage != null) Image(previewImage, contentDescription = sampleContactName, modifier = Modifier.size(avatarSize.dp).clip(RoundedCornerShape(avatarRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Icon(Icons.Default.Person, null, tint = Teal, modifier = Modifier.size(avatarSize.dp).clip(RoundedCornerShape(avatarRadius.dp)))
                Text(sampleContactName, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                Text(sampleContactNumber, color = Color(0xFF64748B), fontSize = 13.sp)
                Button(onClick = {}, modifier = Modifier.fillMaxWidth(yesButtonWidth / 100f).height(yesButtonHeight.dp), shape = RoundedCornerShape(yesButtonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(yesColor)))) {
                    Text(yesLabel.ifBlank { "بله" }, fontSize = yesButtonTextSize.sp)
                }
                Spacer(Modifier.height(buttonSpacing.dp))
                Button(onClick = {}, modifier = Modifier.fillMaxWidth(noButtonWidth / 100f).height(noButtonHeight.dp), shape = RoundedCornerShape(noButtonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(noColor)))) {
                    Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text(noLabel.ifBlank { "خیر" }, fontSize = noButtonTextSize.sp)
                }
            }
        }
    }
}

@Composable
private fun CallColorPicker(title: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.take(5).forEach { hex ->
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(android.graphics.Color.parseColor(hex))).border(if (selected == hex) 3.dp else 1.dp, if (selected == hex) Forest else Color(0xFFCBD5E1), RoundedCornerShape(10.dp)).clickable { onSelected(hex) })
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.drop(5).forEach { hex ->
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(android.graphics.Color.parseColor(hex))).border(if (selected == hex) 3.dp else 1.dp, if (selected == hex) Forest else Color(0xFFCBD5E1), RoundedCornerShape(10.dp)).clickable { onSelected(hex) })
        }
    }
}

@Composable private fun DashboardScreen(listState: androidx.compose.foundation.lazy.LazyListState, onExit: () -> Unit, onPages: () -> Unit, onContacts: () -> Unit, onAllApps: () -> Unit, onAppearance: () -> Unit, onClock: () -> Unit, onSecurity: () -> Unit, onPermissions: () -> Unit, onAbout: () -> Unit, onAudio: () -> Unit, onCallCustomization: () -> Unit, onCallConfirmation: () -> Unit, onCustomContacts: () -> Unit, onDialer: () -> Unit, onRecentCallsCustomization: () -> Unit, onUpdates: () -> Unit, onSelectLauncher: () -> Unit, onAndroidSettings: () -> Unit, onUninstall: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("مدیریت سهل‌بیت", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Forest, modifier = Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Button(onClick = onExit, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(6.dp)); Text("بازگشت به صفحه اصلی") } }
        Spacer(Modifier.height(18.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DashboardCard("صدا و زنگ‌ها", "تنظیم بلندی صدا و انتخاب زنگ‌ها", "audio", onAudio) }
            item { DashboardCard("مدیریت صفحه‌ها و چیدمان", "تعداد صفحه‌ها و چیدمان کارت‌ها", "pages", onPages) }
            item { DashboardCard("مخاطبان و برنامه‌های صفحه اصلی", "انتخاب مخاطبان و برنامه‌های صفحهٔ اصلی", "contacts", onContacts) }
            item { DashboardCard("مخاطبین اختصاصی", "ساخت مخاطب و ثبت چند شماره با تصویر جداگانه", "customcontacts", onCustomContacts) }
            item { DashboardCard("ظاهرِ صفحه اصلی", "پس‌زمینه، کارت‌ها و تنظیمات ظاهری", "appearance", onAppearance) }
            item { DashboardCard("سفارشی‌سازی صفحه تماس", "تنظیم ظاهر تماس‌های ورودی و خروجی", "callcustom", onCallCustomization) }
            item { DashboardCard("شماره‌گیر اختصاصی", "شماره‌گیری با صفحه‌کلید سهل‌بیت", "dialer", onDialer) }
            item { DashboardCard("سفارشی‌سازی صفحه تماس‌های اخیر", "تنظیم تصویر، نام و اندازهٔ اطلاعات تماس‌ها", "recentcustom", onRecentCallsCustomization) }
            item { DashboardCard("امنیت و پین مدیر", "تغییر پین و بازیابی دسترسی مدیر", "security", onSecurity) }
            item { DashboardCard("دسترسی‌ها و نقش‌های برنامه", "مدیریت دسترسی‌ها و نقش‌های اندروید", "permissions", onPermissions) }
            item { DashboardCard("بررسی به‌روزرسانی برنامه", "دریافت نسخهٔ جدید از منابع رسمی", "updates", onUpdates) }
            item { DashboardCard("برنامه‌های تلفن همراه", "مشاهده، جزئیات و اجرای برنامه‌ها", "apps", onAllApps) }
            item { DashboardCard("گزینه‌های تنظیمات اندروید", "دسترسی به بخش‌های مختلف تنظیمات تلفن همراه", "settings", onAndroidSettings) }
            item { DashboardCard("درباره سهل‌بیت", "نسخه، توسعه‌دهنده و راه‌های ارتباطی", "about", onAbout) }
            item { DashboardCard("حذف برنامه سهل‌بیت", "پاک کردن حافظهٔ پنهان برنامه یا حذف سهل‌بیت", "uninstall", onUninstall) }
        }
    }
}
@Composable
private fun AndroidSettingsPage(onBack: () -> Unit, onUninstall: () -> Unit) {
    val context = LocalContext.current
    fun open(action: String, data: Uri? = null) {
        val intent = Intent(action).apply { if (data != null) this.data = data; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    SettingsPage("گزینه‌های تنظیمات اندروید", "میان‌برهای کاربردی برای دسترسی به بخش‌های مختلف تنظیمات تلفن همراه. نام و محل دقیق گزینه‌ها ممکن است در دستگاه‌های مختلف متفاوت باشد.", onBack) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دکمه‌های سهل‌بیت در پنل سریع اندروید", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("این ۱۰ دکمه برای بازگشت سریع به صفحهٔ اصلی سهل‌بیت طراحی شده‌اند. اگر هنگام باز کردن یا ویرایش پنل سریع، دست شما به یکی از این دکمه‌ها برخورد کند، برنامهٔ سهل‌بیت باز می‌شود و به صفحهٔ اصلی آن برمی‌گردید؛ این دکمه‌ها هیچ تنظیمی را تغییر نمی‌دهند و اقدامی مانند تماس یا ارسال اطلاعات انجام نمی‌دهند. برای افزودن آن‌ها، پنل سریع اندروید را ویرایش کنید و کاشی‌های «سهل‌بیت ۱» تا «سهل‌بیت ۱۰» را به پنل اضافه کنید.", color = Color(0xFF64748B), fontSize = 13.sp, lineHeight = 22.sp)
            }
        }
        @Composable fun actionCard(title: String, description: String, label: String, action: String, data: Uri? = null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(title, color = Ink, fontWeight = FontWeight.Bold)
                    Text(description, color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 20.sp)
                    OutlinedButton(onClick = { open(action, data) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        }
        actionCard("صفحهٔ برنامهٔ سهل‌بیت", "نمایش اطلاعات برنامه، اعلان‌ها، مجوزها و فضای ذخیره‌سازی سهل‌بیت.", "بازکردن اطلاعات برنامه", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        actionCard("تنظیمات اصلی تلفن همراه", "ورود به صفحهٔ اصلی تنظیمات سیستم‌عامل.", "بازکردن تنظیمات اصلی", Settings.ACTION_SETTINGS)
        actionCard("اتصالات", "مدیریت Wi-Fi، بلوتوث، شبکهٔ تلفن همراه و سایر اتصالات.", "بازکردن تنظیمات اتصالات", Settings.ACTION_WIRELESS_SETTINGS)
        actionCard("صفحهٔ قفل و امنیت", "مدیریت قفل صفحه، رمز عبور و گزینه‌های امنیتی دستگاه.", "بازکردن تنظیمات امنیت و قفل", Settings.ACTION_SECURITY_SETTINGS)
        actionCard("برنامه‌ها", "مدیریت برنامه‌های نصب‌شده و مجوزهای آن‌ها.", "بازکردن تنظیمات برنامه‌ها", Settings.ACTION_APPLICATION_SETTINGS)
        actionCard("مدیریت عمومی دستگاه", "ورود به بخش‌های عمومی تنظیمات؛ عنوان دقیق این بخش در دستگاه‌ها متفاوت است.", "بازکردن تنظیمات عمومی", Settings.ACTION_SETTINGS)
        actionCard("تاریخ و ساعت", "تنظیم منطقهٔ زمانی، تاریخ، ساعت و قالب نمایش.", "بازکردن تنظیمات تاریخ و ساعت", Settings.ACTION_DATE_SETTINGS)
        actionCard("به‌روزرسانی اندروید", "بازکردن صفحهٔ به‌روزرسانی سیستم‌عامل؛ ممکن است سازنده این گزینه را در بخش دیگری قرار داده باشد.", "بررسی به‌روزرسانی اندروید", "android.settings.SYSTEM_UPDATE_SETTINGS")
        actionCard("دربارهٔ تلفن", "نمایش مدل دستگاه، شمارهٔ ساخت و اطلاعات سیستم‌عامل.", "بازکردن دربارهٔ تلفن", Settings.ACTION_DEVICE_INFO_SETTINGS)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("حذف برنامهٔ سهل‌بیت", color = Ink, fontWeight = FontWeight.Bold)
                Text("برای رفتن به صفحهٔ اختصاصی حذف برنامه و پاک‌کردن حافظهٔ پنهان، از دکمهٔ زیر استفاده کنید.", color = Color(0xFF64748B), fontSize = 12.sp, lineHeight = 20.sp)
                OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) { Text("رفتن به صفحهٔ حذف برنامه") }
            }
        }
    }
}

@Composable
private fun UninstallPage(onBack: () -> Unit, onCallDeveloper: () -> Unit) {
    val context = LocalContext.current
    var showLauncherRequired by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFallback by remember { mutableStateOf(false) }
    val uninstallRequest = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val stillInstalled = runCatching { context.packageManager.getPackageInfo(context.packageName, 0); true }.getOrDefault(false)
        if (stillInstalled) showFallback = true
    }
    fun openAppDetails() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }
            .onFailure { android.widget.Toast.makeText(context, "صفحهٔ اطلاعات برنامه در تنظیمات اندروید باز نشد.", android.widget.Toast.LENGTH_LONG).show() }
    }
    SettingsPage("حذف برنامه سهل‌بیت", "اگر دیگر به سهل‌بیت نیاز ندارید، حذف آن از مسیر استاندارد اندروید انجام می‌شود. پس از تأیید نهایی، خود برنامه و داده‌های آن نیز حذف خواهند شد.", onBack) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("پاک کردن حافظه پنهان برنامه", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("اگر برنامه کند شده یا با خطا روبه‌رو است، فایل‌های موقت و حافظهٔ پنهان را پاک کنید. تنظیمات، پین مدیر، مخاطبان اختصاصی و کارت‌های ذخیره‌شده حذف نمی‌شوند.", color = Color(0xFF64748B), fontSize = 13.sp, lineHeight = 22.sp)
                OutlinedButton(onClick = {
                    val ok = runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }; true }.getOrDefault(false)
                    android.widget.Toast.makeText(context, if (ok) "حافظهٔ پنهان برنامه پاک شد." else "پاک‌کردن حافظهٔ پنهان کامل نشد.", android.widget.Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("پاک کردن حافظه پنهان برنامه") }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ارتباط با توسعه‌دهنده", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("اگر با مشکلی روبه‌رو شدید، لطفاً با شمارهٔ ۰۹۱۴۵۸۸۰۸۱۴ تماس بگیرید یا از طریق پیام‌رسان‌های بله، ایتا، روبیکا، تلگرام و واتساپ پیام دهید. همچنین می‌توانید به ایمیل Pashae@iran.ir پیام بفرستید.", color = Color(0xFF64748B), fontSize = 13.sp, lineHeight = 22.sp)
                OutlinedButton(onClick = onCallDeveloper, modifier = Modifier.fillMaxWidth()) { Text("تماس با توسعه‌دهنده") }
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Pashae@iran.ir"))) } }, modifier = Modifier.fillMaxWidth()) { Text("ارسال ایمیل") }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("حذف برنامه سهل‌بیت", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("اگر سهل‌بیت هنوز برنامه صفحه اصلی (لانچر) پیش‌فرض است، ابتدا برنامهٔ دیگری را انتخاب کنید. پس از آن، حذف برنامه با تأیید خود اندروید انجام می‌شود.", color = Color(0xFF64748B), fontSize = 13.sp, lineHeight = 22.sp)
                Button(onClick = {
                    if (isHomeRoleHeld(context)) showLauncherRequired = true else showDeleteConfirm = true
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("حذف برنامه سهل‌بیت") }
            }
        }
        if (showLauncherRequired) AlertDialog(
            onDismissRequest = { showLauncherRequired = false },
            title = { Text("ابتدا برنامه صفحه اصلی را تغییر دهید") },
            text = { Text("سهل‌بیت هنوز برنامه صفحه اصلی (لانچر) پیش‌فرض تلفن همراه شماست. اندروید تا زمانی که برنامهٔ دیگری را به‌عنوان صفحه اصلی انتخاب نکنید، ممکن است اجازهٔ حذف سهل‌بیت را ندهد. با انتخاب برنامهٔ دیگر، اطلاعات سهل‌بیت حذف نمی‌شود و می‌توانید دوباره به همین صفحه برگردید.") },
            confirmButton = { Button(onClick = {
                showLauncherRequired = false
                runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
            }) { Text("انتخاب برنامه صفحه اصلی دیگر") } },
            dismissButton = { TextButton(onClick = { showLauncherRequired = false }) { Text("انصراف") } }
        )
        if (showDeleteConfirm) AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأیید حذف برنامه سهل‌بیت") },
            text = { Text("با ادامه، اندروید صفحهٔ تأیید حذف را نمایش می‌دهد. پس از تأیید آن صفحه، برنامه سهل‌بیت، تنظیمات، پین مدیر، مخاطبان اختصاصی و داده‌های ذخیره‌شدهٔ آن از تلفن همراه حذف می‌شوند. اگر اندروید اجازهٔ حذف مستقیم ندهد، می‌توانید از صفحهٔ اطلاعات برنامه در تنظیمات اقدام کنید.") },
            confirmButton = { Button(onClick = {
                showDeleteConfirm = false
                runCatching { uninstallRequest.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + context.packageName))) }
                    .onFailure { showFallback = true }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("تأیید و حذف") } },
            dismissButton = { Row { TextButton(onClick = { showDeleteConfirm = false; openAppDetails() }) { Text("اطلاعات برنامه") }; TextButton(onClick = { showDeleteConfirm = false }) { Text("انصراف") } } }
        )
        if (showFallback) AlertDialog(
            onDismissRequest = { showFallback = false },
            title = { Text("باز کردن اطلاعات برنامه") },
            text = { Text("باز کردن درخواست حذف مستقیم ممکن نشد. از صفحهٔ اطلاعات برنامه در تنظیمات اندروید، گزینهٔ حذف را انتخاب کنید.") },
            confirmButton = { Button(onClick = { showFallback = false; openAppDetails() }) { Text("باز کردن اطلاعات برنامه") } },
            dismissButton = { TextButton(onClick = { showFallback = false }) { Text("بستن") } }
        )
    }
}

@Composable private fun DashboardCard(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            val categoryIcon = when (icon) {
                "pages" -> Icons.Default.List
                "contacts" -> Icons.Default.Person
                "apps" -> Icons.Default.Search
                "appearance" -> Icons.Default.Edit
                "clock" -> Icons.Default.DateRange
                "audio" -> Icons.Default.Notifications
                "callcustom" -> Icons.Default.Phone
                "dialer" -> Icons.Default.Call
                "customcontacts" -> Icons.Default.Person
                "recentcustom" -> Icons.Default.DateRange
                "callconfirm" -> Icons.Default.CheckCircle
                "security" -> Icons.Default.Lock
                "permissions" -> Icons.Default.Build
                "home" -> Icons.Default.Home
                "settings" -> Icons.Default.Settings
                "about" -> Icons.Default.Info
                "updates" -> Icons.Default.Refresh
                "uninstall" -> Icons.Default.Delete
                else -> Icons.Default.MoreVert
            }
            when (icon) {
                "clock" -> Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
                "audio" -> Icon(painterResource(R.drawable.ic_speaker), contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
                "dialer" -> Icon(painterResource(R.drawable.ic_dialer), contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
                "customcontacts" -> Icon(painterResource(R.drawable.ic_custom_contacts), contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
                "recentcustom" -> Icon(painterResource(R.drawable.ic_recent_calls), contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp))
                else -> Icon(categoryIcon, contentDescription = null, tint = Forest, modifier = Modifier.size(28.dp).then(if (icon == "apps" || icon == "callcustom" || icon == "updates") Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier))
            }
            Spacer(Modifier.width(16.dp))
            Column { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = Color(0xFF64736B), fontSize = 13.sp) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowBack, null, tint = Teal)
        }
    }
}
@Composable private fun SettingsPage(title: String, description: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
@Composable private fun RepeatableStepButton(label: String, enabled: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            onStep()
            delay(320)
            while (pressed && enabled) { onStep(); delay(65) }
        }
    }
    Box(Modifier.size(48.dp).clickable(interactionSource = interaction, indication = null, enabled = enabled) {}, contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Forest else Color.LightGray, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun NumberSetting(label: String, value: Int, min: Int, max: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp).alpha(if (enabled) 1f else .45f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Ink, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                RepeatableStepButton("−", enabled && value > min) { onChange((value - 1).coerceAtLeast(min)) }
                Text(value.toPersianDigits(), fontSize = 22.sp, color = Forest)
                RepeatableStepButton("+", enabled && value < max) { onChange((value + 1).coerceAtMost(max)) }
            }
        }
    }
}

@Composable private fun HomeScreen(battery: Int, charging: Boolean, background: String, customContacts: List<CustomContact>, permissionRevision: Int, onAdminEntry: () -> Unit, pageCount: Int, entries: List<LauncherEntry>, gridColumns: Int, gridRows: Int, rowCardCounts: List<Int>, rowAlignments: List<String>, onEntryClick: (LauncherEntry) -> Unit, onConfirmCall: (LauncherEntry) -> Unit, pendingCall: LauncherEntry?, onCancelCall: () -> Unit, gridAlignment: String, pageNames: List<String>) {
    var settingsTaps by remember { mutableIntStateOf(0) }
    var settingsHintShown by remember { mutableStateOf(false) }
    LaunchedEffect(settingsTaps) { if (settingsTaps > 0) { delay(1500); settingsTaps = 0; settingsHintShown = false } }
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val contentWidth = (screenWidth - 32.dp).coerceAtLeast(240.dp)
    val launcherPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
    val adminTapThreshold = launcherPrefs.getInt("settings_taps_threshold", 7).coerceIn(3, 15)
    val showAppNames = launcherPrefs.getBoolean("show_app_names", false)
    val showContactNames = launcherPrefs.getBoolean("show_contact_names", false)
    val recentShowNames = launcherPrefs.getBoolean("recent_show_names", false)
    val recentShowSavedCount = launcherPrefs.getBoolean("recent_show_saved_count", false)
    val recentAvatarSize = launcherPrefs.getInt("recent_avatar_size", 64).coerceIn(40, 112)
    val recentAvatarRadius = launcherPrefs.getInt("recent_avatar_radius", 20).coerceIn(0, 50)
    val recentNameSize = launcherPrefs.getInt("recent_name_size", 15).coerceIn(11, 24)
    val homeContactRadius = launcherPrefs.getInt("home_contact_radius", 20).coerceIn(0, 50)
    val homeAppRadius = launcherPrefs.getInt("home_app_radius", 20).coerceIn(0, 50)
    fun confirmColor(key: String, fallback: String): Color = runCatching { Color(android.graphics.Color.parseColor(launcherPrefs.getString("confirm_call_$key", fallback) ?: fallback)) }.getOrDefault(Color(android.graphics.Color.parseColor(fallback)))
    val confirmYesColor = confirmColor("yes_color", "#168A45")
    val confirmNoColor = confirmColor("no_color", "#B3261E")
    val confirmYesLabel = launcherPrefs.getString("confirm_call_yes_label", "بله، تماس بگیرید") ?: "بله، تماس بگیرید"
    val confirmNoLabel = launcherPrefs.getString("confirm_call_no_label", "خیر") ?: "خیر"
    val confirmYesButtonHeight = launcherPrefs.getInt("confirm_call_yes_button_height", launcherPrefs.getInt("confirm_call_button_height", 62)).coerceIn(48, 88)
    val confirmNoButtonHeight = launcherPrefs.getInt("confirm_call_no_button_height", launcherPrefs.getInt("confirm_call_button_height", 62)).coerceIn(48, 88)
    val confirmYesTextSize = launcherPrefs.getInt("confirm_call_yes_text_size", launcherPrefs.getInt("confirm_call_button_text_size", 17)).coerceIn(14, 24)
    val confirmNoTextSize = launcherPrefs.getInt("confirm_call_no_text_size", launcherPrefs.getInt("confirm_call_button_text_size", 17)).coerceIn(14, 24)
    val confirmAvatarSize = launcherPrefs.getInt("confirm_call_avatar_size", 112).coerceIn(64, 160)
    val confirmAvatarRadius = launcherPrefs.getInt("confirm_call_avatar_radius", 20).coerceIn(0, 40)
    val confirmYesButtonRadius = launcherPrefs.getInt("confirm_call_yes_button_radius", launcherPrefs.getInt("confirm_call_button_radius", 16)).coerceIn(0, 40)
    val confirmNoButtonRadius = launcherPrefs.getInt("confirm_call_no_button_radius", launcherPrefs.getInt("confirm_call_button_radius", 16)).coerceIn(0, 40)
    val confirmButtonSpacing = launcherPrefs.getInt("confirm_call_button_spacing", 50).coerceIn(8, 100)
    val bg = when (background) { "white" -> Color.White; "cream" -> Color(0xFFFFF8E7); "gray" -> Color(0xFFE6E7E8); "blue" -> Color(0xFFE8F1F5); "sage" -> Color(0xFFE5EDE5); "lavender" -> Color(0xFFEDE7F6); "mint" -> Color(0xFFE2F2EC); "sand" -> Color(0xFFF3EBDD); "rose" -> Color(0xFFF7E7E9); else -> Color(0xFFFFF8E7) }
    val fg = if (background == "forest") Color.White else Ink
    val day = LocalDate.now().dayOfWeek.value // Monday=1 ... Sunday=7
    val dayText = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")[day - 1]
    val marks = when (day) { 6 -> "◯"; 7 -> "│"; 1 -> "│  │"; 2 -> "│  │  │"; 3 -> "│  │  │  │"; 4 -> "│  │  │  │  │"; else -> "━━━━━━━━" }
    val batteryColor = when { battery >= 81 -> Color(0xFF087A35); battery >= 51 -> Color(0xFF36A653); battery >= 31 -> Color(0xFF9DBB35); battery >= 21 -> Color(0xFFE2B72D); else -> Color(0xFFD32F2F) }
    val pager = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    fun pageFlag(key: String, default: Boolean): Boolean = runCatching { org.json.JSONObject(launcherPrefs.getString(key, "{}") ?: "{}").optBoolean(pager.currentPage.toString(), default) }.getOrDefault(default)
    val showClockOnCurrentPage = pageFlag("page_clock_enabled", true)
    val showBatteryOnCurrentPage = pageFlag("page_battery_enabled", true)
    val showRecentCallsOnCurrentPage = pageFlag("page_recent_calls", false)
    val recentCallRecords by produceState(initialValue = emptyList<RecentCallRecord>(), key1 = pager.currentPage, key2 = showRecentCallsOnCurrentPage, key3 = permissionRevision) {
        value = if (showRecentCallsOnCurrentPage && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) withContext(Dispatchers.IO) { readRecentCallRecords(context) } else emptyList()
    }
    Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 12.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 0.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (!settingsHintShown) {
                    android.widget.Toast.makeText(context, "لطفاً ${adminTapThreshold.toPersianDigits()} بار ضربه بزنید تا صفحه ورود مدیر باز شود.", android.widget.Toast.LENGTH_SHORT).show()
                    settingsHintShown = true
                }
                settingsTaps++
                if (settingsTaps >= adminTapThreshold) { settingsTaps = 0; settingsHintShown = false; onAdminEntry() }
            }, modifier = Modifier.size(32.dp).offset(x = (-10).dp)) {
                Icon(Icons.Default.Settings, "ورود مدیر", tint = fg.copy(alpha = .72f), modifier = Modifier.size(21.dp))
            }
        }
        val clockPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
        val clockSize = clockPrefs.getInt("clock_size_dp", 360).coerceIn(180, 360)
        val effectiveClockSize = minOf(clockSize, (screenWidth.value.toInt() - 32).coerceAtLeast(120))
        val clockDigits = clockPrefs.getInt("clock_digit_size_sp", 40).coerceIn(20, 56)
        val clockLanguage = clockPrefs.getString("clock_digit_language", "english") ?: "english"
        val secondsVisible = clockPrefs.getBoolean("clock_show_seconds", false)
        val hourLength = clockPrefs.getFloat("clock_hour_hand_length", 0.40f)
        val minuteLength = clockPrefs.getFloat("clock_minute_hand_length", 0.65f)
        val secondLength = clockPrefs.getFloat("clock_second_hand_length", 0.90f)
        if (showClockOnCurrentPage) {
            AnalogClock(Modifier.size(effectiveClockSize.dp), digitLanguage = clockLanguage, digitSizeSp = clockDigits, showSecondHand = secondsVisible, hourHandLength = hourLength, minuteHandLength = minuteLength, secondHandLength = secondLength)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text(dayText, color = fg, fontSize = 25.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Text(marks, color = fg, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = 2.dp)) }
        }
        if (showBatteryOnCurrentPage) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                val numericBattery = context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE).getBoolean("numeric_battery", false)
                if (numericBattery) Text("${battery.toPersianDigits()}٪", color = batteryColor, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                else {
                    Box(Modifier.width(128.dp).height(48.dp).border(2.dp, batteryColor, RoundedCornerShape(9.dp)).padding(4.dp), contentAlignment = Alignment.CenterStart) { Box(Modifier.fillMaxWidth(battery.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(batteryColor)) }
                    Box(Modifier.padding(start = 2.dp).width(8.dp).height(20.dp).background(batteryColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                }
                if (charging) { Spacer(Modifier.width(12.dp)); Text("⚡", color = Color(0xFFFFB300), fontSize = 48.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(1.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val layoutPrefs = remember { context.getSharedPreferences("sahlbit_local", Context.MODE_PRIVATE) }
                val rowRoot = remember(layoutPrefs.getString("row_layouts", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_layouts", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val rowCountRoot = remember(layoutPrefs.getString("row_counts_per_page", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_counts_per_page", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val alignRoot = remember(layoutPrefs.getString("row_alignments", "{}")) { runCatching { org.json.JSONObject(layoutPrefs.getString("row_alignments", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject()) }
                val storedRows = rowRoot.optJSONArray(index.toString())
                val fallbackRows = rowCountRoot.optInt(index.toString(), storedRows?.length() ?: layoutPrefs.getInt("grid_rows", 2)).coerceIn(1, 6)
                val pageRowCounts = (0 until fallbackRows).map { row -> (storedRows?.optInt(row, gridColumns) ?: gridColumns).coerceIn(1, 6) }
                val uniformCardsPerRow = pageRowCounts.maxOrNull()?.coerceIn(1, 6) ?: 1
                val headerHeight = (if (showClockOnCurrentPage) effectiveClockSize.dp + 46.dp else 0.dp) + (if (showBatteryOnCurrentPage) 64.dp else 0.dp)
                val responsiveCardHeight = ((LocalConfiguration.current.screenHeightDp.dp - headerHeight - 120.dp) / pageRowCounts.size.coerceAtLeast(1)).coerceIn(24.dp, 154.dp)
                val savedAlignments = alignRoot.opt(index.toString())
                val pageRowAlignments = List(pageRowCounts.size) { "center" }
                val pageEntries = entries.filter { it.page == index }
                if (showRecentCallsOnCurrentPage) {
                    if (recentCallRecords.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("تماس اخیری برای نمایش وجود ندارد یا اجازهٔ مشاهدهٔ سابقهٔ تماس فعال نیست.", color = fg, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                    else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        if (recentShowSavedCount) item { Text("مخاطبان ذخیره‌شده: ${customContacts.size.toPersianDigits()}", color = fg, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                        items(recentCallRecords, key = { it.id }) { call ->
                            val matchingContact = entries.firstOrNull { it.kind == "contact" && it.phoneNumber?.filter(Char::isDigit) == call.number.filter(Char::isDigit) }
                            val matchingCustomPhone = customContacts.asSequence().flatMap { contact -> contact.phones.asSequence().map { phone -> contact to phone } }.firstOrNull { it.second.number.filter(Char::isDigit) == call.number.filter(Char::isDigit) }
                            val recentPhotoUri = matchingContact?.let { if (it.photoSource == "contact") it.photoSourceUri else it.photoUri } ?: matchingCustomPhone?.second?.photoUri
                            val photoBitmap = remember(recentPhotoUri) { recentPhotoUri?.let { uri -> runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } }
                            val callName = call.name.ifBlank { matchingContact?.title ?: matchingCustomPhone?.first?.name ?: call.number.ifBlank { "شمارهٔ ناشناس" } }
                            val (callSymbol, callColor) = when (call.type) {
                                android.provider.CallLog.Calls.MISSED_TYPE, android.provider.CallLog.Calls.REJECTED_TYPE -> "×" to Color(0xFFB3261E)
                                android.provider.CallLog.Calls.INCOMING_TYPE -> "↙" to Color(0xFF168A45)
                                android.provider.CallLog.Calls.OUTGOING_TYPE -> if (call.durationSeconds > 0) "↗" to Color(0xFF1976D2) else "↗" to Color(0xFFB3261E)
                                else -> "☎" to Color(0xFF64748B)
                            }
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (background == "forest") Color.White.copy(alpha = .16f) else Color.White), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(recentAvatarSize.dp).clip(RoundedCornerShape(recentAvatarRadius.dp)).background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) {
                                        if (photoBitmap != null) Image(photoBitmap, contentDescription = callName, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(recentAvatarRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        else Text(callSymbol, color = callColor, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (recentShowNames) Text(callName, color = fg, fontWeight = FontWeight.Bold, fontSize = recentNameSize.sp, maxLines = 2, lineHeight = (recentNameSize + 4).sp)
                                        Text(call.number.toPersianDigitsText(), color = fg.copy(alpha = .75f), fontSize = 12.sp, lineHeight = 17.sp)
                                        Text(if (call.type == android.provider.CallLog.Calls.MISSED_TYPE) "تماس ورودی بدون پاسخ" else if (call.type == android.provider.CallLog.Calls.INCOMING_TYPE) "تماس ورودی پاسخ‌داده‌شده" else if (call.durationSeconds > 0) "تماس خروجی با برقراری مکالمه" else "تماس خروجی بدون برقراری مکالمه", color = callColor, fontSize = 11.sp, lineHeight = 16.sp)
                                    }
                                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(call.number)))) } }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Default.Call, null); Text("تماس", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                } else if (pageEntries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("هنوز موردی به این صفحه اضافه نشده است.", color = fg, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        var entryOffset = 0
                        pageRowCounts.forEachIndexed { rowIndex, rowCount ->
                            val rowEntries = pageEntries.drop(entryOffset).take(rowCount)
                            entryOffset += rowCount
                            if (rowEntries.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                rowEntries.forEach { entry ->
                                    Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Card(onClick = { onEntryClick(entry) },
                                modifier = Modifier.fillMaxWidth().height(responsiveCardHeight),
                                colors = CardDefaults.cardColors(containerColor = if (background == "forest") Color.White.copy(alpha = .16f) else Color.White), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxSize().padding(vertical = 3.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val photo = if (entry.photoSource == "contact") entry.photoSourceUri else entry.photoUri
                                    val bitmap = remember(photo) { photo?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use { input -> BitmapFactory.decodeStream(input) }?.asImageBitmap() }.getOrNull() } }
                                    val showEntryName = (entry.kind == "app" && showAppNames) || (entry.kind == "contact" && showContactNames && entry.title.isNotBlank())
                                    val contactWithoutName = entry.kind == "contact" && !showEntryName
                                    val entryImageSize = minOf(if (showEntryName) 88.dp else 104.dp, (responsiveCardHeight - if (showEntryName) 30.dp else 12.dp).coerceAtLeast(18.dp))
                                    Box(Modifier.size(entryImageSize), contentAlignment = Alignment.Center) {
                                        if (bitmap != null) Image(bitmap, contentDescription = entry.title, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(homeContactRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        else if (entry.kind == "app") {
                                            val iconBitmap = remember(entry.packageName) { entry.packageName?.let { pkg -> runCatching {
                                                val drawable = context.packageManager.getApplicationIcon(pkg)
                                                val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                                val canvas = android.graphics.Canvas(bmp); drawable.setBounds(0, 0, 96, 96); drawable.draw(canvas); bmp.asImageBitmap()
                                            }.getOrNull() } }
                                            if (iconBitmap != null) Image(iconBitmap, contentDescription = entry.title, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(homeAppRadius.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                            else Icon(Icons.Default.Search, null, tint = Teal, modifier = Modifier.size(minOf(72.dp, entryImageSize)).scale(scaleX = -1f, scaleY = 1f))
                                        } else Icon(Icons.Default.Person, null, tint = if (background == "forest") Color.White else Teal, modifier = Modifier.size(minOf(if (contactWithoutName) 90.dp else 72.dp, entryImageSize)).clip(RoundedCornerShape(homeContactRadius.dp)))
                                    }
                                    if (showEntryName) {
                                        Box(Modifier.fillMaxWidth().height(minOf(42.dp, (responsiveCardHeight * .35f).coerceAtLeast(14.dp))), contentAlignment = Alignment.Center) {
                                            Text(entry.title, color = if (background == "forest") Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
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
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(pageCount.coerceAtLeast(1)) { index ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (pager.currentPage == index) 9.dp else 7.dp).clip(CircleShape).background(if (pager.currentPage == index) fg else fg.copy(alpha = .28f)))
            }
        }
        if (pendingCall != null) {
            AlertDialog(onDismissRequest = onCancelCall, title = {
                val pendingPhotoUri = if (pendingCall.photoSource == "contact") pendingCall.photoSourceUri else pendingCall.photoUri
                val avatar = remember(pendingPhotoUri) { pendingPhotoUri?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } }.getOrNull() } }
                if (avatar != null) Image(avatar, contentDescription = pendingCall.title, modifier = Modifier.size(confirmAvatarSize.dp).clip(RoundedCornerShape(confirmAvatarRadius.dp)))
                else Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(confirmAvatarSize.dp), tint = Teal)
            }, text = { Text("${pendingCall.title}\n${pendingCall.phoneNumber ?: ""}\nآیا می‌خواهید تماس برقرار شود؟", color = Ink) }, confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { onConfirmCall(pendingCall) }, modifier = Modifier.fillMaxWidth(launcherPrefs.getInt("confirm_call_yes_width_pct", 100).coerceIn(50, 100) / 100f).height(confirmYesButtonHeight.dp), shape = RoundedCornerShape(confirmYesButtonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = confirmYesColor)) { Icon(Icons.Default.Call, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f)); Spacer(Modifier.width(6.dp)); Text(confirmYesLabel, fontSize = confirmYesTextSize.sp) }
                    Spacer(Modifier.height(confirmButtonSpacing.dp))
                    Button(onClick = onCancelCall, modifier = Modifier.fillMaxWidth(launcherPrefs.getInt("confirm_call_no_width_pct", 62).coerceIn(50, 100) / 100f).height(confirmNoButtonHeight.dp), shape = RoundedCornerShape(confirmNoButtonRadius.dp), colors = ButtonDefaults.buttonColors(containerColor = confirmNoColor)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text(confirmNoLabel, fontSize = confirmNoTextSize.sp) }
                }
            }, dismissButton = { })
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

private fun copyContactPhoto(context: Context, sourceUri: String): String? =
    saveSquarePhotoFromUri(context, Uri.parse(sourceUri), "contact_")

private fun deleteOwnedContactPhoto(context: Context, photo: String) {
    runCatching {
        val uri = Uri.parse(photo)
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            val base = context.filesDir.canonicalFile
            if (file.canonicalFile.parentFile == base && (file.name.startsWith("contact_") || file.name.startsWith("sahlbit_photo_"))) file.delete()
        }
    }
}
