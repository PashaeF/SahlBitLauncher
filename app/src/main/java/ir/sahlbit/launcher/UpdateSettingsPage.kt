package ir.sahlbit.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch

@Composable
fun UpdateSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<SahlBitUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    var installing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("برای بررسی نسخهٔ جدید، یکی از دو منبع رسمی را انتخاب کنید.") }
    var statusColor by remember { mutableStateOf(Color(0xFF52645B)) }

    fun checkFrom(website: Boolean) {
        checkJob = scope.launch {
            checking = true
            update = null
            status = "در حال بررسی نسخهٔ منتشرشده..."
            statusColor = Color(0xFF52645B)
            try {
                val found = withTimeout(15_000L) {
                    if (website) SahlBitUpdateManager.checkWebsite(context) else SahlBitUpdateManager.checkGitHub(context)
                }
                update = found
                if (SahlBitUpdateManager.isNewer(context, found)) {
                    status = "نسخهٔ جدید ${found.versionName.toPersianDigitsText()} در منبع ${found.source} پیدا شد."
                    statusColor = Color(0xFF167342)
                } else {
                    status = "سهل‌بیت به‌روز است؛ نسخهٔ نصب‌شده ${BuildConfig.VERSION_NAME.toPersianDigitsText()} است."
                    statusColor = Color(0xFF167342)
                }
            } catch (error: TimeoutCancellationException) {
                status = "مهلت بررسی به پایان رسید. اتصال اینترنت یا در دسترس بودن منبع را بررسی کنید و دوباره تلاش کنید."
                statusColor = Color(0xFFB3261E)
            } catch (error: CancellationException) {
                status = "بررسی به‌روزرسانی لغو شد."
                statusColor = Color(0xFFB45309)
            } catch (error: Exception) {
                status = error.message?.takeIf { message -> message.any { it.code in 0x0600..0x06FF } } ?: "بررسی به‌روزرسانی ناموفق بود. لطفاً اتصال اینترنت و نشانی منبع را بررسی کنید."
                statusColor = Color(0xFFB3261E)
            } finally {
                checking = false
                if (checkJob === currentCoroutineContext()[Job]) checkJob = null
            }
        }
    }

    fun installUpdate(found: SahlBitUpdate) {
        scope.launch {
            installing = true
            status = "در حال دانلود و بررسی اصالت فایل APK..."
            statusColor = Color(0xFF52645B)
            try {
                SahlBitUpdateManager.downloadAndInstall(context, found)
                status = "فایل بررسی شد و به نصب‌کنندهٔ اندروید تحویل داده شد. مراحل تأیید نصب را کامل کنید."
                statusColor = Color(0xFF167342)
            } catch (error: InstallPermissionRequired) {
                status = error.message ?: "ابتدا اجازهٔ نصب برنامه‌ها را فعال کنید."
                statusColor = Color(0xFFB45309)
                if (Build.VERSION.SDK_INT >= 26) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)))
                    }
                }
            } catch (error: Exception) {
                status = error.message?.takeIf { message -> message.any { it.code in 0x0600..0x06FF } } ?: "دانلود یا آماده‌سازی نصب ناموفق بود. لطفاً دوباره تلاش کنید."
                statusColor = Color(0xFFB3261E)
            } finally {
                installing = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFFFF8E7)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = Color(0xFF2563EB)) }
            Text("بررسی به‌روزرسانی برنامه", color = Color(0xFF2563EB), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF173B32)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سهل‌بیت ${BuildConfig.VERSION_NAME.toPersianDigitsText()}", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("نسخهٔ نصب‌شدهٔ برنامه روی این دستگاه", color = Color(0xFFE4F2E9), fontSize = 14.sp)
            }
        }
        val installPermissionGranted = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("اجازهٔ نصب به‌روزرسانی‌ها", color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("برای نصب نسخهٔ جدید از داخل سهل‌بیت، اندروید باید اجازهٔ نصب برنامه از این منبع را بدهد. فعال‌سازی این اجازه اختیاری است و تأیید نهایی هر نصب همچنان با شماست.", color = Color(0xFF52645B), fontSize = 13.sp, lineHeight = 21.sp)
                Text(if (installPermissionGranted) "وضعیت: اجازهٔ نصب فعال است." else "وضعیت: اجازهٔ نصب هنوز فعال نشده است.", color = if (installPermissionGranted) Color(0xFF167342) else Color(0xFFB45309), fontSize = 13.sp)
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 26 && !installPermissionGranted) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))) }
                    } else {
                        android.widget.Toast.makeText(context, "اجازهٔ نصب به‌روزرسانی‌ها فعال است.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (installPermissionGranted) "بررسی وضعیت اجازهٔ نصب" else "اعطای اجازهٔ نصب به‌روزرسانی‌ها") }
            }
        }
        Text("منبع بررسی نسخه", color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        OutlinedButton(onClick = { checkFrom(true) }, modifier = Modifier.fillMaxWidth(), enabled = !checking && !installing) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
            Spacer(Modifier.width(8.dp))
            Text("بررسی از وب‌سایت رسمی pashaeft.ir")
        }
        OutlinedButton(onClick = { checkFrom(false) }, modifier = Modifier.fillMaxWidth(), enabled = !checking && !installing) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
            Spacer(Modifier.width(8.dp))
            Text("بررسی آخرین انتشار در GitHub")
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت", color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(status, color = statusColor, fontSize = 14.sp, lineHeight = 23.sp)
                if (checking || installing) {
                    Spacer(Modifier.height(4.dp))
                    Text(if (checking) "در حال بررسی..." else "لطفاً تا پایان دانلود و بررسی فایل صبر کنید.", color = Color(0xFF52645B), fontSize = 12.sp)
                    if (checking) OutlinedButton(
                        onClick = { checkJob?.cancel(); checkJob = null; checking = false; status = "بررسی به‌روزرسانی لغو شد."; statusColor = Color(0xFFB45309) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("لغو بررسی") }
                }
            }
        }
        update?.let { found ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اطلاعات نسخهٔ منتشرشده", color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("نسخه: ${found.versionName.toPersianDigitsText()}  |  کد نسخه: ${found.versionCode.toString().toPersianDigitsText()}", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                    Text("منبع: ${found.source}", color = Color(0xFF52645B), fontSize = 13.sp)
                    if (found.changelog.isNotBlank()) Text(found.changelog.take(1800), color = Color(0xFF172033), fontSize = 13.sp, lineHeight = 21.sp)
                    if (SahlBitUpdateManager.isNewer(context, found)) {
                        Button(onClick = { installUpdate(found) }, modifier = Modifier.fillMaxWidth(), enabled = !checking && !installing) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
                            Spacer(Modifier.width(8.dp))
                            Text("دانلود و نصب به‌روزرسانی")
                        }
                    } else {
                        Unit
                    }
                }
            }
        }
        Text(
            "نکتهٔ امنیتی: قبل از نصب، شناسهٔ بسته، شمارهٔ نسخه، در صورت ارائه هش SHA-256 و امضای دیجیتال APK بررسی می‌شود. اگر امضای فایل با برنامهٔ نصب‌شده یکی نباشد، نصب متوقف می‌شود تا اطلاعات برنامه در معرض خطر قرار نگیرد.",
            color = Color(0xFF52645B), fontSize = 12.sp, lineHeight = 20.sp
        )
        Text(
            "در اندروید ۸ و جدیدتر ممکن است لازم باشد یک‌بار اجازهٔ «نصب برنامه‌های ناشناس» را برای سهل‌بیت فعال کنید. نصب نهایی همچنان از طریق پنجرهٔ تأیید خود اندروید انجام می‌شود.",
            color = Color(0xFF52645B), fontSize = 12.sp, lineHeight = 20.sp
        )
    }
}
