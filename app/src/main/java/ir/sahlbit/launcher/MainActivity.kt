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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sahlbit.launcher.ui.AnalogClock
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val batteryLevel = mutableIntStateOf(100)
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) batteryLevel.intValue = (level * 100) / scale
        }
    }

    private val homeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Android owns the HOME role decision. Returning from the system screen is sufficient;
        // the app never attempts to override the user's selected launcher.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        setContent {
            SahlBitHome(
                batteryPercent = batteryLevel.intValue,
                onRequestSahlBitDefault = { requestHomeRole() },
                onOpenHomeSettings = { openHomeSettings() }
            )
        }
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    openHomeSettings()
                } else {
                    homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }
                return
            }
        }
        openHomeSettings()
    }

    private fun openHomeSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        super.onDestroy()
    }
}

private data class HomePage(val title: String, val contacts: List<String>)

@Composable
private fun SahlBitHome(
    batteryPercent: Int,
    onRequestSahlBitDefault: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    val pages = remember {
        listOf(
            HomePage("خانه فرزندان", listOf("فرزند اول", "فرزند دوم", "فرزند سوم", "فرزند چهارم", "فرزند پنجم")),
            HomePage("موبایل فرزندان", listOf("فرزند اول", "فرزند دوم", "فرزند سوم", "فرزند چهارم", "فرزند پنجم")),
            HomePage("نوه‌ها · صفحه اول", listOf("نوه اول", "نوه دوم", "نوه سوم", "نوه چهارم")),
            HomePage("نوه‌ها · صفحه دوم", listOf("نوه پنجم", "نوه ششم", "نوه هفتم")),
            HomePage("همسران فرزندان", listOf("همسر اول", "همسر دوم", "همسر سوم"))
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var settingsTaps by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        LauncherSettingsScreen(
            onBack = { showSettings = false },
            onRequestSahlBitDefault = onRequestSahlBitDefault,
            onOpenHomeSettings = onOpenHomeSettings
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF203D35), Color(0xFF55745C), Color(0xFF263F36)))
        )
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.34f).align(Alignment.BottomCenter).background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0x553A573D), Color(0xAA172A26)))
        ))

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    settingsTaps++
                    if (settingsTaps >= 7) { showSettings = true; settingsTaps = 0 }
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "ورود مدیر", tint = Color.White.copy(alpha = 0.82f))
                }
            }

            AnalogClock(modifier = Modifier.size(174.dp))
            Spacer(Modifier.height(10.dp))
            Text("${batteryPercent.coerceIn(0, 100)}٪", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(page.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        page.contacts.chunked(2).forEach { rowContacts ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                                rowContacts.forEach { contact ->
                                    ContactTile(contact = contact, modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                                if (rowContacts.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("برای جابه‌جایی، صفحه را به چپ یا راست بکشید", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun LauncherSettingsScreen(
    onBack: () -> Unit,
    onRequestSahlBitDefault: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F5F2)).systemBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color(0xFF203D35))
            }
            Text("تنظیمات سهل‌بیت", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF203D35))
        }

        Text("مدیریت لانچر پیش‌فرض", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF203D35))
        Text(
            "می‌توانی سهل‌بیت را به‌عنوان صفحه اصلی گوشی انتخاب کنی. هر زمان هم خواستی، از تنظیمات اندروید لانچر سامسونگ (One UI Home) را دوباره انتخاب کن.",
            color = Color(0xFF45534B), lineHeight = 23.sp
        )

        Button(
            onClick = onRequestSahlBitDefault,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("انتخاب سهل‌بیت به‌عنوان لانچر اصلی") }

        OutlinedButton(
            onClick = onOpenHomeSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("انتخاب لانچر سامسونگ (One UI Home)") }

        HorizontalDivider()
        Text("بازیابی در صورت بروز خطا", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF203D35))
        Text(
            "اگر سهل‌بیت درست اجرا نشد، صفحه انتخاب لانچر اندروید را باز کن و One UI Home را انتخاب کن. همچنین می‌توانی از مسیر تنظیمات گوشی ← برنامه‌ها ← برنامه‌های پیش‌فرض ← برنامه صفحه اصلی، لانچر سامسونگ را فعال کنی.",
            color = Color(0xFF45534B), lineHeight = 23.sp
        )
        Text(
            "نکته: اندروید اجازه نمی‌دهد یک لانچر معمولی بدون تأیید کاربر، خودش را با One UI جایگزین کند. این روش عمداً از تغییر خودکار و غیرمجاز لانچر جلوگیری می‌کند.",
            color = Color(0xFF8A4B16), lineHeight = 21.sp,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.weight(1f))
        Text("نسخه آزمایشی ۰.۱.۱ · سهل‌بیت هنوز محدودیت‌های سیستمی یا حالت کیوسک را فعال نمی‌کند.", color = Color(0xFF68756D), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ContactTile(contact: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE8E4D7), Color(0xFFB8C7B5)))),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF718B78)), contentAlignment = Alignment.Center) {
                Text(contact.take(1), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(contact, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}
