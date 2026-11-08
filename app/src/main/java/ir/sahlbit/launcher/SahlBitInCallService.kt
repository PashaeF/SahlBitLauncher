package ir.sahlbit.launcher

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile

/** Tracks Telecom calls and opens the dedicated call activity without posting notifications. */
class SahlBitInCallService : InCallService() {
    override fun onCreate() { super.onCreate(); serviceInstance = this }
    override fun onDestroy() { if (serviceInstance === this) serviceInstance = null; super.onDestroy() }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        trackedCalls[call] = call.state == Call.STATE_RINGING
        call.registerCallback(callback)
        selectForegroundCall()
        publishForeground()
        bringCallUiToFront()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        publishForeground()
    }

    override fun onCallRemoved(call: Call) {
        runCatching { call.unregisterCallback(callback) }
        trackedCalls.remove(call)
        selectForegroundCall()
        if (activeCall == null) {
            sendBroadcast(Intent(CallActivity.ACTION_CALL_ENDED).setPackage(packageName))
        } else {
            publishForeground()
        }
        super.onCallRemoved(call)
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_HOLDING) {
                pendingUnhold?.let { waiting -> pendingUnhold = null; runCatching { waiting.unhold() } }
            }
            publishAfterSelection()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = publishAfterSelection()
        override fun onParentChanged(call: Call, parent: Call?) = publishAfterSelection()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publishAfterSelection()
    }

    private fun publishAfterSelection() { selectForegroundCall(); publishForeground() }

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
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val name = call.details.callerDisplayName.orEmpty().ifBlank { number }
        val held = trackedCalls.keys.any { it !== call && it.state == Call.STATE_HOLDING }
        sendBroadcast(Intent(CallActivity.ACTION_CALL_STATE).setPackage(packageName)
            .putExtra(CallActivity.EXTRA_STATE, call.state)
            .putExtra(CallActivity.EXTRA_NUMBER, number)
            .putExtra(CallActivity.EXTRA_NAME, name)
            .putExtra(CallActivity.EXTRA_INCOMING, isIncoming(call))
            .putExtra(CallActivity.EXTRA_HAS_HELD_CALL, held))
    }

    private fun bringCallUiToFront() {
        val intent = Intent(this, CallActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
    }

    override fun onBringToForeground(showDialpad: Boolean) { super.onBringToForeground(showDialpad); bringCallUiToFront() }

    companion object {
        @Volatile private var serviceInstance: SahlBitInCallService? = null
        private val trackedCalls = LinkedHashMap<Call, Boolean>()
        @Volatile var activeCall: Call? = null
            private set
        private var pendingUnhold: Call? = null

        fun isIncoming(call: Call): Boolean = if (android.os.Build.VERSION.SDK_INT >= 29) {
            call.details.callDirection == Call.Details.DIRECTION_INCOMING
        } else trackedCalls[call] ?: (call.state == Call.STATE_RINGING)

        fun hasHeldCall(): Boolean = trackedCalls.keys.any { it !== activeCall && it.state == Call.STATE_HOLDING }
        fun isMuted(): Boolean = serviceInstance?.callAudioState?.isMuted == true
        fun isSpeakerOn(): Boolean {
            val service = serviceInstance ?: return false
            // Use the stable CallAudioState API across supported Android versions.
            return ((service.callAudioState?.route ?: 0) and CallAudioState.ROUTE_SPEAKER) != 0
        }
        fun toggleMute() { serviceInstance?.let { it.setMuted(!(it.callAudioState?.isMuted ?: false)) } }
        fun toggleSpeaker() {
            val service = serviceInstance ?: return
            val route = service.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
            runCatching {
                service.setAudioRoute(
                    if ((route and CallAudioState.ROUTE_SPEAKER) != 0) {
                        CallAudioState.ROUTE_EARPIECE
                    } else {
                        CallAudioState.ROUTE_SPEAKER
                    }
                )
            }.onFailure {
                service.sendBroadcast(Intent(CallActivity.ACTION_AUDIO_ROUTE_FAILED).setPackage(service.packageName))
            }
        }
        fun toggleHold() {
            activeCall?.let { call -> when (call.state) {
                Call.STATE_ACTIVE -> runCatching { call.hold() }
                Call.STATE_HOLDING -> runCatching { call.unhold() }
            } }
        }
        fun answer() { activeCall?.takeIf { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        fun reject() { activeCall?.takeIf { it.state == Call.STATE_RINGING }?.reject(false, null) }
        fun disconnect() { activeCall?.takeIf { it.state != Call.STATE_DISCONNECTED }?.disconnect() }
        fun swapCalls() {
            val calls = trackedCalls.keys.toList()
            val active = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
            val held = calls.firstOrNull { it.state == Call.STATE_HOLDING }
            if (active != null && held != null) { pendingUnhold = held; runCatching { active.hold() }.onFailure { pendingUnhold = null } }
            else if (active == null && held != null) runCatching { held.unhold() }
        }
    }
}
