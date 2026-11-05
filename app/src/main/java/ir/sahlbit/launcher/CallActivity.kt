package ir.sahlbit.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Large, notification-free, full-screen call UI for the managed launcher. */
class CallActivity : Activity(), SensorEventListener {
    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var primaryAction: Button
    private lateinit var declineAction: Button
    private lateinit var speakerAction: Button
    private var callState = Call.STATE_NEW
    private var incoming = false
    private var displayName = ""
    private var number = ""
    private var proximity: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var powerManager: PowerManager? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val vazir by lazy { ResourcesCompat.getFont(this, R.font.vazirmatn_regular) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CALL_ENDED -> finish()
                ACTION_CALL_STATE -> {
                    callState = intent.getIntExtra(EXTRA_STATE, Call.STATE_NEW)
                    incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)
                    displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
                    number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
                    render()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        applyImmersive()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            proximityWakeLock = powerManager?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "${packageName}:CallProximity")?.apply { setReferenceCounted(false) }
        }
        buildUi()
        val filter = IntentFilter().apply { addAction(ACTION_CALL_STATE); addAction(ACTION_CALL_ENDED) }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        SahlBitInCallService.activeCall?.let { call ->
            callState = call.state; incoming = SahlBitInCallService.isIncoming(call)
            number = call.details.handle?.schemeSpecificPart.orEmpty()
            displayName = call.details.callerDisplayName.orEmpty().ifBlank { number }
        }
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun styleText(view: TextView, size: Float, color: Int = Color.WHITE, bold: Boolean = false) {
        view.typeface = if (bold) ResourcesCompat.getFont(this, R.font.vazirmatn_bold) else vazir
        view.textSize = size; view.setTextColor(color); view.gravity = Gravity.CENTER; view.textDirection = View.TEXT_DIRECTION_RTL
    }
    private fun makeButton(label: String, size: Float, color: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label; textSize = size; typeface = ResourcesCompat.getFont(this@CallActivity, R.font.vazirmatn_bold)
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }
            setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { onClick() }
        }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(10), dp(20), dp(14)) }
        statusView = TextView(this).also { styleText(it, 20f, Color.WHITE, true) }
        root.addView(statusView, LinearLayout.LayoutParams(-1, dp(38)))
        avatarView = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon); scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat() }
        }
        root.addView(avatarView, LinearLayout.LayoutParams(dp(236), dp(236)).apply { topMargin = dp(4); bottomMargin = dp(8) })
        nameView = TextView(this).also { styleText(it, 27f, Color.WHITE, true); it.maxLines = 2 }
        root.addView(nameView, LinearLayout.LayoutParams(-1, dp(58)))
        numberView = TextView(this).also { styleText(it, 20f, Color.WHITE) }
        root.addView(numberView, LinearLayout.LayoutParams(-1, dp(42)))
        primaryAction = makeButton("☎  پاسخ به تماس", 22f, Color.rgb(22, 140, 68)) {
            if (incoming && callState == Call.STATE_RINGING) SahlBitInCallService.answer()
            else if (callState == Call.STATE_HOLDING) SahlBitInCallService.toggleHold() else SahlBitInCallService.disconnect()
        }
        root.addView(primaryAction, LinearLayout.LayoutParams(-1, dp(78)).apply { topMargin = dp(10) })
        declineAction = makeButton("✕  رد تماس", 22f, Color.rgb(190, 35, 45)) { SahlBitInCallService.reject() }
        root.addView(declineAction, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(76) })
        speakerAction = makeButton("🔊  تغییر به بلندگو", 21f, Color.rgb(25, 105, 190)) { SahlBitInCallService.toggleSpeaker(); render() }
        root.addView(speakerAction, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(34) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun render() {
        if (!::root.isInitialized) return
        val ringing = incoming && callState == Call.STATE_RINGING
        val dialing = !incoming && (callState == Call.STATE_NEW || callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING || callState == Call.STATE_SELECT_PHONE_ACCOUNT)
        val active = callState == Call.STATE_ACTIVE
        val held = callState == Call.STATE_HOLDING
        val background = when { ringing -> Color.rgb(77, 145, 205); dialing -> Color.rgb(255, 193, 7); active -> Color.rgb(46, 125, 50); held -> Color.rgb(69, 90, 100); else -> Color.rgb(55, 71, 79) }
        val foreground = if (dialing) Color.rgb(38, 50, 56) else Color.WHITE
        root.setBackgroundColor(background)
        statusView.text = when { ringing -> "تماس ورودی"; dialing -> "در حال شماره‌گیری"; active -> "تماس برقرار است"; held -> "تماس در انتظار"; callState == Call.STATE_DISCONNECTING -> "در حال پایان تماس"; else -> "وضعیت تماس" }
        nameView.text = displayName.ifBlank { "تماس ناشناس" }; statusView.setTextColor(foreground); nameView.setTextColor(foreground)
        numberView.text = number; numberView.setTextColor(foreground); loadContactPhoto()
        primaryAction.text = when { ringing -> "☎  پاسخ به تماس"; dialing -> "✕  لغو تماس"; held -> "☎  بازگشت به تماس"; else -> "✕  پایان تماس" }
        primaryAction.background = GradientDrawable().apply { setColor(if (ringing) Color.rgb(22, 140, 68) else Color.rgb(190, 35, 45)); cornerRadius = dp(20).toFloat() }
        primaryAction.layoutParams = (primaryAction.layoutParams as LinearLayout.LayoutParams).apply { height = dp(78); topMargin = dp(if (dialing) 78 else 10) }
        declineAction.visibility = if (ringing) View.VISIBLE else View.GONE
        speakerAction.visibility = if (active || held) View.VISIBLE else View.GONE
        speakerAction.text = if (SahlBitInCallService.isSpeakerOn()) "☎  تغییر به تلفن" else "🔊  تغییر به بلندگو"
        updateProximityMonitoring(); applyImmersive()
    }

    private fun loadContactPhoto() {
        avatarView.setImageResource(android.R.drawable.sym_def_app_icon)
        if (number.isBlank() || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val foundName = cursor.getString(0).orEmpty()
                    if (foundName.isNotBlank() && (displayName.isBlank() || displayName == number)) nameView.text = foundName
                    cursor.getString(1)?.takeIf { it.isNotBlank() }?.let { photo ->
                        contentResolver.openInputStream(Uri.parse(photo))?.use { stream -> android.graphics.drawable.Drawable.createFromStream(stream, null)?.let { avatarView.setImageDrawable(it) } }
                    }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (callState != Call.STATE_ACTIVE || event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val near = event.values.firstOrNull()?.let { it < event.sensor.maximumRange } ?: false
        val lock = proximityWakeLock
        if (near) { if (lock != null && !lock.isHeld) runCatching { lock.acquire() } }
        else if (lock?.isHeld == true) runCatching { lock.release() }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun updateProximityMonitoring() {
        sensorManager?.unregisterListener(this)
        if (callState == Call.STATE_ACTIVE && !isFinishing) proximity?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        else if (proximityWakeLock?.isHeld == true) runCatching { proximityWakeLock?.release() }
    }
    override fun onResume() { super.onResume(); applyImmersive(); updateProximityMonitoring() }
    override fun onPause() { sensorManager?.unregisterListener(this); super.onPause() }
    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }; sensorManager?.unregisterListener(this)
        if (proximityWakeLock?.isHeld == true) runCatching { proximityWakeLock?.release() }
        proximityWakeLock = null; super.onDestroy()
    }

    companion object {
        const val ACTION_CALL_STATE = "ir.sahlbit.launcher.CALL_STATE"
        const val ACTION_CALL_ENDED = "ir.sahlbit.launcher.CALL_ENDED"
        const val EXTRA_STATE = "state"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"
        const val EXTRA_INCOMING = "incoming"
        const val EXTRA_HAS_HELD_CALL = "has_held_call"
    }
}
