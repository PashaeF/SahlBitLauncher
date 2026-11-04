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
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.provider.ContactsContract
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Large, senior-friendly call screen driven by Android Telecom state. */
class CallActivity : Activity(), SensorEventListener {
    private lateinit var root: LinearLayout
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var action: Button
    private lateinit var decline: Button
    private lateinit var swap: Button
    private var callState = Call.STATE_NEW
    private var incoming = false
    private var hasHeldCall = false
    private var displayName = ""
    private var number = ""
    private var proximity: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var powerManager: PowerManager? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

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
        // Do not use FLAG_KEEP_SCREEN_ON: it conflicts with proximity-based
        // display blanking and can prevent the panel from turning off.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            proximityWakeLock = powerManager?.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "$packageName:CallProximity"
            )?.apply { setReferenceCounted(false) }
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
        }
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(40), dp(28), dp(40))
        }
        avatarView = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(24).toFloat() }
        }
        root.addView(avatarView, LinearLayout.LayoutParams(dp(220), dp(220)).apply { bottomMargin = dp(24) })
        nameView = TextView(this).apply { textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2 }
        root.addView(nameView)
        numberView = TextView(this).apply { textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(42)) }
        root.addView(numberView)
        action = Button(this).apply {
            textSize = 24f
            isAllCaps = false
            setOnClickListener {
                if (incoming && callState == Call.STATE_RINGING) SahlBitInCallService.answer()
                else if (callState == Call.STATE_HOLDING) SahlBitInCallService.swapCalls()
                else SahlBitInCallService.disconnect()
            }
        }
        root.addView(action, LinearLayout.LayoutParams(dp(260), dp(88)))
        decline = Button(this).apply {
            text = "رد تماس"
            textSize = 20f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(205, 35, 35)))
            setOnClickListener { SahlBitInCallService.reject() }
        }
        root.addView(decline, LinearLayout.LayoutParams(dp(260), dp(72)).apply { topMargin = dp(12) })
        swap = Button(this).apply {
            text = "جابجایی بین تماس‌ها"
            textSize = 18f
            isAllCaps = false
            setOnClickListener { SahlBitInCallService.swapCalls() }
        }
        root.addView(swap, LinearLayout.LayoutParams(dp(260), dp(68)).apply { topMargin = dp(10) })
        setContentView(root)
    }

    private fun render() {
        nameView.text = displayName.ifBlank { "تماس" }
        numberView.text = number
        loadContactPhoto()
        val ringing = incoming && callState == Call.STATE_RINGING
        val dialing = !incoming && (
            callState == Call.STATE_NEW || callState == Call.STATE_DIALING ||
                callState == Call.STATE_CONNECTING || callState == Call.STATE_SELECT_PHONE_ACCOUNT
            )
        val active = callState == Call.STATE_ACTIVE
        val held = callState == Call.STATE_HOLDING
        val background = when {
            ringing -> Color.rgb(25, 118, 210)
            dialing -> Color.rgb(255, 193, 7)
            active -> Color.rgb(46, 125, 50)
            held -> Color.rgb(69, 90, 100)
            else -> Color.rgb(55, 71, 79)
        }
        root.setBackgroundColor(background)
        val foreground = if (dialing) Color.rgb(38, 50, 56) else Color.WHITE
        nameView.setTextColor(foreground)
        numberView.setTextColor(foreground)
        action.text = when {
            ringing -> "☎   پاسخ به تماس"
            dialing -> "✕   لغو تماس"
            held -> "بازگشت به این تماس"
            else -> "✕   پایان تماس"
        }
        decline.visibility = if (ringing) View.VISIBLE else View.GONE
        swap.visibility = if (hasHeldCall && !ringing) View.VISIBLE else View.GONE
        action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            if (ringing) Color.rgb(25, 170, 75) else Color.rgb(205, 35, 35)
        ))
        action.setTextColor(Color.WHITE)
        decline.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(198, 40, 40)))
        updateProximityMonitoring()
    }

    private fun loadContactPhoto() {
        avatarView.setImageResource(android.R.drawable.sym_def_app_icon)
        if (number.isBlank()) return
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
        } else {
            if (lock?.isHeld == true) runCatching { lock.release() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateProximityMonitoring() {
        sensorManager?.unregisterListener(this)
        if (callState == Call.STATE_ACTIVE && !isFinishing) {
            // The screen-off wake lock blanks the physical display when the
            // sensor reports NEAR and lets Android wake it again on FAR.
            proximity?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            if (proximityWakeLock?.isHeld == true) runCatching { proximityWakeLock?.release() }
        }
    }

    override fun onResume() {
        super.onResume()
        updateProximityMonitoring()
    }

    override fun onPause() {
        // Keep an acquired proximity wake lock: Android uses the proximity
        // sensor internally to wake the display again when the phone moves away.
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

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
