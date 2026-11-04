package ir.sahlbit.launcher

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.os.Build

/**
 * Tracks every Telecom call and publishes the call that needs the user's
 * attention. Ringing calls take priority so call-waiting is not hidden by
 * an already-active call.
 */
class SahlBitInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        trackedCalls[call] = call.state == Call.STATE_RINGING
        call.registerCallback(callback)
        selectForegroundCall()
        publishForeground()

        val ui = Intent(this, CallActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        runCatching { startActivity(ui) }
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

    companion object {
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
