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

/** Large, senior-friendly call screen driven by InCallService state. */
class CallActivity : Activity(), SensorEventListener {
    private lateinit var root: LinearLayout
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var action: Button
    private lateinit var decline: Button
    private lateinit var blackOverlay: View
    private var callState = Call.STATE_NEW
    private var incoming = false
    private var displayName = ""
    private var number = ""
    private var proximity: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var near = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CALL_ENDED) { finish(); return }
            if (intent?.action == ACTION_CALL_STATE) {
                callState = intent.getIntExtra(EXTRA_STATE, Call.STATE_NEW)
                incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)
                displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
                number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
                render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        buildUi()
        val filter = IntentFilter().apply { addAction(ACTION_CALL_STATE); addAction(ACTION_CALL_ENDED) }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        SahlBitInCallService.activeCall?.let { call ->
            callState = call.state
            incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
            number = call.details.handle?.schemeSpecificPart.orEmpty()
            displayName = call.details.callerDisplayName.orEmpty().ifBlank { number }
        }
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(28, 40, 28, 40) }
        avatarView = ImageView(this).apply { setImageResource(android.R.drawable.sym_def_app_icon); scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(24).toFloat() } }
        root.addView(avatarView, LinearLayout.LayoutParams(dp(220), dp(220)).apply { bottomMargin = dp(24) })
        nameView = TextView(this).apply { textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2 }
        root.addView(nameView)
        numberView = TextView(this).apply { textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 12, 0, 56) }
        root.addView(numberView)
        action = Button(this).apply { textSize = 24f; isAllCaps = false; setOnClickListener { if (incoming && callState == Call.STATE_RINGING) SahlBitInCallService.answer() else SahlBitInCallService.disconnect() } }
        root.addView(action, LinearLayout.LayoutParams(dp(240), dp(88)))
        decline = Button(this).apply { text = "رد تماس"; textSize = 20f; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(205, 35, 35))); setOnClickListener { SahlBitInCallService.reject() } }
        root.addView(decline, LinearLayout.LayoutParams(dp(240), dp(72)).apply { topMargin = dp(14) })
        blackOverlay = View(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE; isClickable = true; isFocusable = true }
        val frame = android.widget.FrameLayout(this)
        frame.addView(root, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(blackOverlay, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
    }

    private fun render() {
        nameView.text = displayName.ifBlank { "تماس" }
        numberView.text = number
        loadContactPhoto()
        val ringing = incoming && callState == Call.STATE_RINGING
        val dialing = !incoming && (callState == Call.STATE_NEW || callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING || callState == Call.STATE_SELECT_PHONE_ACCOUNT)
        val active = callState == Call.STATE_ACTIVE
        // Requested call-state palette: incoming=blue, outgoing setup=yellow,
        // connected (incoming or outgoing)=green.
        val background = when {
            ringing -> Color.rgb(25, 118, 210)       // #1976D2
            dialing -> Color.rgb(255, 193, 7)        // #FFC107
            active -> Color.rgb(46, 125, 50)         // #2E7D32
            else -> Color.rgb(55, 71, 79)
        }
        root.setBackgroundColor(background)
        val foreground = if (dialing) Color.rgb(38, 50, 56) else Color.WHITE
        nameView.setTextColor(foreground)
        numberView.setTextColor(foreground)
        action.text = if (ringing) "☎   پاسخ به تماس" else if (dialing) "✕   لغو تماس" else "✕   پایان تماس"
        decline.visibility = if (ringing) View.VISIBLE else View.GONE
        action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (ringing) Color.rgb(25, 170, 75) else Color.rgb(205, 35, 35)))
        action.setTextColor(Color.WHITE)
        decline.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(198, 40, 40)))
        if (!active) {
            sensorManager?.unregisterListener(this)
            near = false
            blackOverlay.visibility = View.GONE
        } else {
            updateProximityMonitoring()
        }
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
        val isNear = event.values.firstOrNull()?.let { it < event.sensor.maximumRange } ?: false
        near = isNear
        blackOverlay.visibility = if (near) View.VISIBLE else View.GONE
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun updateProximityMonitoring() {
        sensorManager?.unregisterListener(this)
        // The proximity sensor must only be active after Telecom reports an active call.
        if (callState == Call.STATE_ACTIVE && !isFinishing) {
            // Keep listening while near so the display can wake again when the phone moves away.
            proximity?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            near = false
            blackOverlay.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        updateProximityMonitoring()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }
    override fun onDestroy() { runCatching { unregisterReceiver(receiver) }; sensorManager?.unregisterListener(this); super.onDestroy() }

    companion object {
        const val ACTION_CALL_STATE = "ir.sahlbit.launcher.CALL_STATE"
        const val ACTION_CALL_ENDED = "ir.sahlbit.launcher.CALL_ENDED"
        const val EXTRA_STATE = "state"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"
        const val EXTRA_INCOMING = "incoming"
    }
}
