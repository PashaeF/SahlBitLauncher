package ir.sahlbit.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Minimal system dialer entry point required by ROLE_DIALER. */
class DialPadActivity : Activity() {
    private lateinit var number: EditText
    private var pendingNumber: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24, 24, 24, 24); setBackgroundColor(0xFFF6F2E8.toInt()) }
        root.addView(TextView(this).apply { text = "شماره‌گیر سهل‌بیت"; textSize = 26f; setTextColor(0xFF244B3D.toInt()); gravity = Gravity.CENTER }, ViewGroup.LayoutParams(-1, 80))
        number = EditText(this).apply { textSize = 28f; gravity = Gravity.CENTER; hint = "شماره تلفن"; setSingleLine(true); setText(intent?.data?.schemeSpecificPart.orEmpty()) }
        root.addView(number, ViewGroup.LayoutParams(-1, 76))
        val keys = GridLayout(this).apply { columnCount = 3; rowCount = 4; useDefaultMargins = true }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#").forEachIndexed { index, key ->
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1, 1f),
                GridLayout.spec(index % 3, 1, 1f)
            ).apply { width = 0; height = 72 }
            keys.addView(Button(this).apply {
                text = key; textSize = 26f; isAllCaps = false
                setOnClickListener { number.append(key) }
            }, params)
        }
        root.addView(keys, LinearLayout.LayoutParams(-1, 320))
        root.addView(Button(this).apply { text = "تماس"; textSize = 24f; setOnClickListener { placeCall() } }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply { text = "پاک‌کردن"; setOnClickListener { number.text.clear() } }, LinearLayout.LayoutParams(-1, 60))
        setContentView(root)
    }
    private fun placeCall() {
        val value = number.text.toString().trim()
        if (value.isBlank()) return
        pendingNumber = value
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), REQUEST_CALL_PERMISSION)
            return
        }
        placePendingCall()
    }

    private fun placePendingCall() {
        val value = pendingNumber ?: return
        val uri = Uri.fromParts("tel", value, null)
        runCatching {
            val telecom = getSystemService(android.telecom.TelecomManager::class.java)
                ?: error("Telecom service unavailable")
            telecom.placeCall(uri, android.os.Bundle())
            finish()
        }.onFailure {
            android.widget.Toast.makeText(this, "امکان برقراری تماس وجود ندارد. مجوز تماس و سیم‌کارت را بررسی کنید.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated by Android; retained for minSdk 29 compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PERMISSION && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) placePendingCall()
        else if (requestCode == REQUEST_CALL_PERMISSION) android.widget.Toast.makeText(this, "برای تماس، اجازهٔ برقراری تماس لازم است.", android.widget.Toast.LENGTH_LONG).show()
    }

    companion object { private const val REQUEST_CALL_PERMISSION = 501 }
}
