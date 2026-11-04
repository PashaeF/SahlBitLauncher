package ir.sahlbit.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
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
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Senior-friendly full-screen call interface controlled by Android Telecom. */
class CallActivity : Activity(), SensorEventListener {
    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var primaryAction: Button
    private lateinit var declineAction: Button
    private lateinit var muteAction: Button
    private lateinit var speakerAction: Button
    private lateinit var holdAction: Button
    private lateinit var swapAction: Button
    private var callState = Call.STATE_NEW
    private var incoming = false
    private var hasHeldCall = false
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
                    hasHeldCall = intent.getBooleanExtra(EXTRA_HAS_HELD_CALL, false)
                    displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
                    number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
                    render()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            proximityWakeLock = powerManager?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "$packageName:CallProximity")?.apply { setReferenceCounted(false) }
        }
        buildUi()
        val filter = IntentFilter().apply { addAction(ACTION_CALL_STATE); addAction(ACTION_CALL_ENDED) }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        SahlBitInCallService.activeCall?.let { call ->
            callState = call.state
            incoming = SahlBitInCallService.isIncoming(call)
            number = call.details.handle?.schemeSpecificPart.orEmpty()
            displayName = call.details.callerDisplayName.orEmpty().ifBlank { number }
            hasHeldCall = SahlBitInCallService.hasHeldCall()
        }
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun styleText(view: TextView, size: Float, color: Int = Color.WHITE, bold: Boolean = false) {
        view.typeface = if (bold) ResourcesCompat.getFont(this, R.font.vazirmatn_bold) else vazir
        view.textSize = size
        view.setTextColor(color)
        view.gravity = Gravity.CENTER
        view.textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun makeButton(label: String, size: Float, color: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = size
            typeface = ResourcesCompat.getFont(this@CallActivity, R.font.vazirmatn_medium)
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
            setOnClickListener { onClick() }
        }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
        }
        statusView = TextView(this).also { styleText(it, 20f, Color.WHITE, true) }
        root.addView(statusView, LinearLayout.LayoutParams(-1, dp(42)))
        avatarView = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(100).toFloat() }
        }
        root.addView(avatarView, LinearLayout.LayoutParams(dp(164), dp(164)).apply { topMargin = dp(12); bottomMargin = dp(18) })
        nameView = TextView(this).also { styleText(it, 29f, Color.WHITE, true); it.maxLines = 2 }
        root.addView(nameView, LinearLayout.LayoutParams(-1, dp(56)))
        numberView = TextView(this).also { styleText(it, 20f, Color.WHITE); it.setPadding(0, dp(4), 0, dp(18)) }
        root.addView(numberView, LinearLayout.LayoutParams(-1, dp(48)))
        primaryAction = makeButton("پاسخ به تماس", 23f, Color.rgb(22, 140, 68)) {
            if (incoming && callState == Call.STATE_RINGING) SahlBitInCallService.answer()
            else if (callState == Call.STATE_HOLDING) SahlBitInCallService.toggleHold()
            else SahlBitInCallService.disconnect()
        }
        root.addView(primaryAction, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(8) })
        declineAction = makeButton("رد تماس", 20f, Color.rgb(198, 40, 40)) { SahlBitInCallService.reject() }
        root.addView(declineAction, LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(10) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; weightSum = 3f }
        muteAction = makeButton("بی‌صدا", 15f, Color.rgb(55, 71, 79)) { SahlBitInCallService.toggleMute(); render() }
        speakerAction = makeButton("بلندگو", 15f, Color.rgb(55, 71, 79)) { SahlBitInCallService.toggleSpeaker(); render() }
        holdAction = makeButton("انتظار", 15f, Color.rgb(55, 71, 79)) { SahlBitInCallService.toggleHold() }
        listOf(muteAction, speakerAction, holdAction).forEach { button ->
            controls.addView(button, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(14) })
        swapAction = makeButton("جابجایی بین تماس‌ها", 17f, Color.rgb(69, 90, 100)) { SahlBitInCallService.swapCalls() }
        root.addView(swapAction, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun render() {
        if (!::root.isInitialized) return
        val ringing = incoming && callState == Call.STATE_RINGING
        val dialing = !incoming && (callState == Call.STATE_NEW || callState == Call.STATE_DIALING ||
            callState == Call.STATE_CONNECTING || callState == Call.STATE_SELECT_PHONE_ACCOUNT)
        val active = callState == Call.STATE_ACTIVE
        val held = callState == Call.STATE_HOLDING
        val background = when {
            ringing -> Color.rgb(25, 118, 210)
            dialing -> Color.rgb(255, 193, 7)
            active -> Color.rgb(46, 125, 50)
            held -> Color.rgb(69, 90, 100)
            else -> Color.rgb(55, 71, 79)
        }
        val foreground = if (dialing) Color.rgb(38, 50, 56) else Color.WHITE
        root.setBackgroundColor(background)
        statusView.text = when {
            ringing -> "تماس ورودی"
            dialing -> "در حال شماره‌گیری"
            active -> "تماس برقرار است"
            held -> "تماس در انتظار"
            callState == Call.STATE_DISCONNECTING -> "در حال پایان تماس"
            else -> "وضعیت تماس"
        }
        nameView.text = displayName.ifBlank { "تماس ناشناس" }
        statusView.setTextColor(foreground)
        nameView.setTextColor(foreground)
        numberView.text = number
        numberView.setTextColor(foreground)
        loadContactPhoto()
        primaryAction.text = when {
            ringing -> "پاسخ به تماس"
            dialing -> "لغو تماس"
            held -> "بازگشت به تماس"
            else -> "پایان تماس"
        }
        primaryAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            if (ringing) Color.rgb(22, 140, 68) else Color.rgb(198, 40, 40)
        ))
        declineAction.visibility = if (ringing) View.VISIBLE else View.GONE
        muteAction.visibility = if (active || held) View.VISIBLE else View.GONE
        speakerAction.visibility = if (active || held) View.VISIBLE else View.GONE
        holdAction.visibility = if (active || held) View.VISIBLE else View.GONE
        swapAction.visibility = if (hasHeldCall && !ringing) View.VISIBLE else View.GONE
        muteAction.text = if (SahlBitInCallService.isMuted()) "فعال‌کردن صدا" else "بی‌صداکردن"
        speakerAction.text = if (SahlBitInCallService.isSpeakerOn()) "خاموش‌کردن بلندگو" else "بلندگو"
        holdAction.text = if (held) "ادامه تماس" else "انتظار"
        updateProximityMonitoring()
    }

    private fun loadContactPhoto() {
        avatarView.setImageResource(android.R.drawable.sym_def_app_icon)
        if (number.isBlank() || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photo = cursor.getString(0)
                    if (!photo.isNullOrBlank()) contentResolver.openInputStream(Uri.parse(photo))?.use { stream ->
                        android.graphics.drawable.Drawable.createFromStream(stream, null)?.let { avatarView.setImageDrawable(it) }
                    }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (callState != Call.STATE_ACTIVE || event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val near = event.values.firstOrNull()?.let { it < event.sensor.maximumRange } ?: false
        val lock = proximityWakeLock
        if (near) {
            if (lock != null && !lock.isHeld) runCatching { lock.acquire() }
        } else if (lock?.isHeld == true) runCatching { lock.release() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateProximityMonitoring() {
        sensorManager?.unregisterListener(this)
        if (callState == Call.STATE_ACTIVE && !isFinishing) {
            proximity?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else if (proximityWakeLock?.isHeld == true) runCatching { proximityWakeLock?.release() }
    }

    override fun onResume() { super.onResume(); updateProximityMonitoring() }
    override fun onPause() { sensorManager?.unregisterListener(this); super.onPause() }
    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        sensorManager?.unregisterListener(this)
        if (proximityWakeLock?.isHeld == true) runCatching { proximityWakeLock?.release() }
        proximityWakeLock = null
        super.onDestroy()
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
