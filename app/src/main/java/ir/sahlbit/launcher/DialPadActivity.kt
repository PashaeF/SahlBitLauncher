package ir.sahlbit.launcher

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

private fun String.toPersianDialDigits(): String = map { ch ->
    when (ch) {
        in '0'..'9' -> ('۰'.code + ch.code - '0'.code).toChar()
        in '٠'..'٩' -> ('۰'.code + ch.code - '٠'.code).toChar()
        else -> ch
    }
}.joinToString("")

private fun String.toLatinDialDigits(): String = map { ch ->
    when (ch) {
        in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
        in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
        else -> ch
    }
}.joinToString("")

/** Minimal system dialer entry point required by ROLE_DIALER. */
class DialPadActivity : Activity() {
    private lateinit var number: EditText
    private var pendingNumber: String? = null

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private val deleteHandler = Handler(Looper.getMainLooper())
    private var deleteHeld = false
    private var deleteLongPress = false
    private val repeatDelete = object : Runnable {
        override fun run() {
            if (deleteHeld) {
                deleteLongPress = true
                deleteOne()
                deleteHandler.postDelayed(this, 140)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersive()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFFF6F2E8.toInt())
        }
        root.addView(TextView(this).apply {
            text = "شماره‌گیر سهل‌بیت"
            textSize = 24f
            setTextColor(0xFF244B3D.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(54)))
        number = EditText(this).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            inputType = InputType.TYPE_CLASS_PHONE
            showSoftInputOnFocus = false
            hint = "شماره تلفن"
            setText(intent?.data?.schemeSpecificPart.orEmpty().toPersianDialDigits())
            setSelection(text.length)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(number, LinearLayout.LayoutParams(-1, dp(82)))
        number.addTextChangedListener(object : TextWatcher {
            private var formatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (formatting) return
                val current = s?.toString().orEmpty()
                val persian = current.toPersianDialDigits()
                if (current != persian) {
                    val cursor = number.selectionStart.coerceAtLeast(0)
                    formatting = true
                    number.setText(persian)
                    number.setSelection(cursor.coerceAtMost(persian.length))
                    formatting = false
                }
            }
        })
        val keys = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            useDefaultMargins = true
        }
        listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "*", "۰", "#").forEachIndexed { index, key ->
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1, 1f),
                GridLayout.spec(index % 3, 1, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            keys.addView(Button(this).apply {
                text = if (key == "۰") "۰\n+ " else key
                textSize = if (key == "۰") 21f else 26f
                isAllCaps = false
                setOnClickListener { number.append(key) }
                if (key == "۰") setOnLongClickListener { number.append("+"); true }
            }, params)
        }
        root.addView(keys, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "تماس"
            textSize = 22f
            isAllCaps = false
            setOnClickListener { placeCall() }
        }, LinearLayout.LayoutParams(-1, dp(62)))
        root.addView(Button(this).apply {
            text = "⌫     پاک کردن"
            textSize = 18f
            isAllCaps = false
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        deleteHeld = true
                        deleteLongPress = false
                        deleteHandler.postDelayed(repeatDelete, 420)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        deleteHeld = false
                        deleteHandler.removeCallbacks(repeatDelete)
                        if (!deleteLongPress) deleteOne()
                        true
                    }
                    else -> true
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        applyVazirmatn(root)
        setContentView(root)
    }

    private fun deleteOne() {
        if (!::number.isInitialized) return
        val end = number.selectionStart.coerceAtLeast(0)
        if (end > 0) number.text.delete(end - 1, end)
    }

    private fun applyVazirmatn(view: View) {
        if (view is TextView) view.typeface = ResourcesCompat.getFont(this, R.font.vazirmatn_regular)
        if (view is ViewGroup) for (index in 0 until view.childCount) applyVazirmatn(view.getChildAt(index))
    }

    private fun placeCall() {
        val value = number.text.toString().trim()
        if (value.isBlank()) return
        pendingNumber = value.toLatinDialDigits()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), REQUEST_CALL_PERMISSION)
            return
        }
        placePendingCall()
    }

    private fun placePendingCall() {
        val value = pendingNumber ?: return
        val uri = Uri.fromParts("tel", value, null)
        runCatching {
            startActivity(Intent(Intent.ACTION_CALL, uri))
            finish()
        }.onFailure {
            runCatching {
                startActivity(Intent(Intent.ACTION_DIAL, uri))
                finish()
            }.onFailure {
                android.widget.Toast.makeText(this, "امکان برقراری تماس وجود ندارد. مجوز تماس و سیم‌کارت را بررسی کنید.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility with older Android versions")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PERMISSION && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) placePendingCall()
        else if (requestCode == REQUEST_CALL_PERMISSION) android.widget.Toast.makeText(this, "برای تماس، اجازهٔ برقراری تماس لازم است.", android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() { deleteHeld = false; deleteHandler.removeCallbacksAndMessages(null); super.onDestroy() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    companion object { private const val REQUEST_CALL_PERMISSION = 501 }
}
