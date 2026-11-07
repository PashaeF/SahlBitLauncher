package ir.sahlbit.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class SahlBitUpdate(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val changelog: String,
    val source: String
)

class InstallPermissionRequired : Exception("برای نصب نسخهٔ دانلودشده، اجازهٔ «نصب برنامه‌های ناشناس» را برای سهل‌بیت فعال کنید و دوباره دکمهٔ نصب را بزنید.")

object SahlBitUpdateManager {
    const val WEBSITE_MANIFEST_URL = "https://pashaef.ir/sahlbit/update.json"
    private const val GITHUB_MANIFEST_URL = "https://raw.githubusercontent.com/PashaeF/SahlBitLauncher/development/update.json"
    private const val GITHUB_API_MANIFEST_URL = "https://api.github.com/repos/PashaeF/SahlBitLauncher/contents/update.json?ref=development"
    private const val MAX_APK_SIZE = 120L * 1024L * 1024L

    suspend fun checkWebsite(context: Context): SahlBitUpdate = withContext(Dispatchers.IO) {
        parseManifest(JSONObject(httpGet(WEBSITE_MANIFEST_URL)), "وب‌سایت سهل‌بیت")
    }

    suspend fun checkGitHub(context: Context): SahlBitUpdate = withContext(Dispatchers.IO) {
        val manifest = runCatching { JSONObject(httpGet(GITHUB_MANIFEST_URL)) }.getOrElse {
            val apiResponse = JSONObject(httpGet(GITHUB_API_MANIFEST_URL))
            val encoded = apiResponse.getString("content")
            val decoded = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
            JSONObject(decoded)
        }
        parseManifest(manifest, "گیت‌هاب")
    }

    private fun parseManifest(json: JSONObject, source: String): SahlBitUpdate {
        val update = SahlBitUpdate(
            packageName = json.optString("packageName", "ir.sahlbit.launcher"),
            versionName = json.getString("versionName"),
            versionCode = json.getLong("versionCode"),
            minSdk = json.optInt("minSdk", 21),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.optString("sha256"),
            changelog = json.optString("changelog", "فهرست تغییرات ارائه نشده است."),
            source = source
        )
        require(update.apkUrl.startsWith("https://")) { "نشانی APK باید با HTTPS آغاز شود." }
        require(update.packageName == "ir.sahlbit.launcher") { "این فایل برای برنامهٔ سهل‌بیت نیست." }
        return update
    }

    private fun httpGet(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SahlBit-Android-Updater")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("دریافت اطلاعات نسخه ناموفق بود (HTTP $code).")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(context: Context, update: SahlBitUpdate): Boolean =
        update.versionCode > installedVersionCode(context)

    @Suppress("DEPRECATION")
    private fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    suspend fun downloadAndInstall(context: Context, update: SahlBitUpdate) = withContext(Dispatchers.IO) {
        require(update.packageName == context.packageName) { "شناسهٔ بسته با برنامهٔ نصب‌شده مطابقت ندارد." }
        require(update.versionCode > installedVersionCode(context)) { "نسخهٔ ارائه‌شده جدیدتر از نسخهٔ نصب‌شده نیست." }
        require(Build.VERSION.SDK_INT >= update.minSdk) { "این نسخه با نسخهٔ اندروید دستگاه سازگار نیست." }

        val apk = File(context.cacheDir, "sahlbit-update.apk")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 45000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SahlBit-Android-Updater")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("دانلود APK ناموفق بود (HTTP ${connection.responseCode}).")
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= MAX_APK_SIZE) { "حجم فایل APK غیرعادی است." }
            connection.inputStream.use { input ->
                apk.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_SIZE) { "حجم فایل از حد مجاز بیشتر است." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        require(apk.exists() && apk.length() > 0L) { "فایل APK دانلودشده خالی است." }

        if (update.sha256.isNotBlank()) {
            val actual = FileInputStream(apk).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            val expected = update.sha256.trim().lowercase()
            require(actual.equals(expected, ignoreCase = true)) { "صحت فایل دانلودشده تأیید نشد؛ فایل نصب نمی‌شود." }
        }

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val signingFlags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, signingFlags)
            ?: throw IllegalStateException("فایل دانلودشده یک APK معتبر نیست.")
        require(archive.packageName == context.packageName) { "شناسهٔ برنامهٔ داخل APK با سهل‌بیت مطابقت ندارد." }
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archiveVersion == update.versionCode) { "شمارهٔ نسخهٔ داخل APK با اطلاعات سرور یکسان نیست." }
        require((archive.applicationInfo?.minSdkVersion ?: 1) <= Build.VERSION.SDK_INT) { "این APK برای نسخهٔ اندروید دستگاه ساخته نشده است." }

        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, signingFlags)
        val installedSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            else installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val archiveSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            else archive.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        require(installedSigners.isNotEmpty() && installedSigners == archiveSigners) {
            "امضای APK جدید با نسخهٔ نصب‌شده یکسان نیست. برای حفظ اطلاعات برنامه، آن را نصب نکنید؛ ابتدا باید همهٔ نسخه‌ها با یک کلید امضای ثابت ساخته شوند."
        }

        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) throw InstallPermissionRequired()

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val installer = pm.packageInstaller
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                FileInputStream(apk).use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, PackageInstallStatusReceiver::class.java).setAction("ir.sahlbit.launcher.INSTALL_STATUS"),
                    flags
                )
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }
}
