package ir.sahlbit.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.os.Build

/**
 * Tracks every Telecom call and publishes the call that needs the user's
 * attention. Ringing calls take priority so call-waiting is not hidden by
 * an already-active call.
 */
class SahlBitInCallService : InCallService() {
    override fun onCreate() { super.onCreate(); serviceInstance = this }
    override fun onDestroy() { if (serviceInstance === this) serviceInstance = null; super.onDestroy() }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        trackedCalls[call] = call.state == Call.STATE_RINGING
        call.registerCallback(callback)
        selectForegroundCall()
        publishForeground()
        showCallNotification()
        if (!isIncoming(call)) {
            val ui = Intent(this, CallActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            runCatching { startActivity(ui) }
        }
    }

    override fun onCallRemoved(call: Call) {
        runCatching { call.unregisterCallback(callback) }
        trackedCalls.remove(call)
        selectForegroundCall()
        if (activeCall == null) {
            getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIFICATION_ID)
            sendBroadcast(Intent(CallActivity.ACTION_CALL_ENDED).setPackage(packageName))
        } else {
            publishForeground()
            showCallNotification()
        }
        super.onCallRemoved(call)
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_HOLDING) {
                val waiting = pendingUnhold
                if (waiting != null) {
                    pendingUnhold = null
                    runCatching { waiting.unhold() }
                }
            }
            publishAfterSelection()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = publishAfterSelection()
        override fun onParentChanged(call: Call, parent: Call?) = publishAfterSelection()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publishAfterSelection()
    }

    private fun publishAfterSelection() {
        selectForegroundCall()
        publishForeground()
    }

    private fun selectForegroundCall() {
        val calls = trackedCalls.keys.toList().filter { it.state != Call.STATE_DISCONNECTED }
        activeCall = calls.firstOrNull { it.state == Call.STATE_RINGING }
            ?: calls.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING || it.state == Call.STATE_SELECT_PHONE_ACCOUNT }
            ?: calls.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: calls.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: calls.firstOrNull()
    }

    private fun publishForeground() {
        val call = activeCall ?: return
        val details = call.details
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val name = details.callerDisplayName.orEmpty().ifBlank { number }
        val hasHeldCall = trackedCalls.keys.any { it !== call && it.state == Call.STATE_HOLDING }
        sendBroadcast(
            Intent(CallActivity.ACTION_CALL_STATE).setPackage(packageName)
                .putExtra(CallActivity.EXTRA_STATE, call.state)
                .putExtra(CallActivity.EXTRA_NUMBER, number)
                .putExtra(CallActivity.EXTRA_NAME, name)
                .putExtra(CallActivity.EXTRA_INCOMING, isIncoming(call))
                .putExtra(CallActivity.EXTRA_HAS_HELD_CALL, hasHeldCall)
        )
    }

    private fun showCallNotification() {
        val call = activeCall ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CALL_CHANNEL_ID, "تماس‌های سهل‌بیت", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "نمایش رابط تماس ورودی و وضعیت تماس"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val fullScreen = PendingIntent.getActivity(this, CALL_NOTIFICATION_ID, intent, flags)
        val incoming = isIncoming(call) && call.state == Call.STATE_RINGING
        val title = call.details.callerDisplayName.orEmpty().ifBlank { call.details.handle?.schemeSpecificPart.orEmpty().ifBlank { "تماس ورودی" } }
        val text = if (incoming) "تماس ورودی؛ برای پاسخ لمس کنید" else "تماس در حال انجام"
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CALL_CHANNEL_ID) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, incoming)
            .setPriority(Notification.PRIORITY_MAX)
            .build()
        runCatching { manager.notify(CALL_NOTIFICATION_ID, notification) }
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        val intent = Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
    }

    companion object {
        private const val CALL_CHANNEL_ID = "sahlbit_calls"
        private const val CALL_NOTIFICATION_ID = 7301
        @Volatile private var serviceInstance: SahlBitInCallService? = null
        private val trackedCalls = LinkedHashMap<Call, Boolean>()

        @Volatile
        var activeCall: Call? = null
            private set

        private var pendingUnhold: Call? = null

        fun isIncoming(call: Call): Boolean = if (Build.VERSION.SDK_INT >= 29) {
            call.details.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            trackedCalls[call] ?: (call.state == Call.STATE_RINGING)
        }

        fun hasHeldCall(): Boolean = trackedCalls.keys.any { it !== activeCall && it.state == Call.STATE_HOLDING }
        fun isMuted(): Boolean = serviceInstance?.callAudioState?.isMuted == true
        fun isSpeakerOn(): Boolean = (serviceInstance?.callAudioState?.route ?: 0) and CallAudioState.ROUTE_SPEAKER != 0
        fun toggleMute() { serviceInstance?.let { service -> service.setMuted(!(service.callAudioState?.isMuted ?: false)) } }
        fun toggleSpeaker() {
            serviceInstance?.let { service ->
                val current = service.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
                val target = if ((current and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
                service.setAudioRoute(target)
            }
        }
        fun toggleHold() {
            activeCall?.let { call ->
                when (call.state) {
                    Call.STATE_ACTIVE -> runCatching { call.hold() }
                    Call.STATE_HOLDING -> runCatching { call.unhold() }
                }
            }
        }

        fun answer() {
            activeCall?.takeIf { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun reject() {
            activeCall?.takeIf { it.state == Call.STATE_RINGING }?.reject(false, null)
        }

        fun disconnect() {
            activeCall?.takeIf { it.state != Call.STATE_DISCONNECTED }?.disconnect()
        }

        /** Switches between the currently active and held calls. */
        fun swapCalls() {
            val calls = trackedCalls.keys.toList()
            val active = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
            val held = calls.firstOrNull { it.state == Call.STATE_HOLDING }
            if (active != null && held != null) {
                pendingUnhold = held
                runCatching { active.hold() }.onFailure { pendingUnhold = null }
            } else if (active == null && held != null) {
                runCatching { held.unhold() }
            }
        }
    }
}
