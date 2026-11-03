package ir.sahlbit.launcher

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile

/** Telecom-owned call controller. Tracks all calls so call-waiting does not overwrite state. */
class SahlBitInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        calls[call] = call
        call.registerCallback(callback)
        selectForegroundCall()
        publish(call)
        val ui = Intent(this, CallActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        runCatching { startActivity(ui) }
    }

    override fun onCallRemoved(call: Call) {
        runCatching { call.unregisterCallback(callback) }
        calls.remove(call)
        if (activeCall === call) selectForegroundCall()
        val current = activeCall
        if (current == null) {
            sendBroadcast(Intent(CallActivity.ACTION_CALL_ENDED).setPackage(packageName))
        } else {
            publish(current)
        }
        super.onCallRemoved(call)
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            selectForegroundCall()
            publish(activeCall ?: call)
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            selectForegroundCall()
            publish(activeCall ?: call)
        }
        override fun onParentChanged(call: Call, parent: Call?) { selectForegroundCall(); publish(activeCall ?: call) }
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) { selectForegroundCall(); publish(activeCall ?: call) }
    }

    private fun selectForegroundCall() {
        val all = calls.keys.toList()
        activeCall = all.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: all.firstOrNull { it.state == Call.STATE_RINGING }
            ?: all.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: all.firstOrNull { it.state != Call.STATE_DISCONNECTED }
    }

    private fun publish(call: Call) {
        val details = call.details
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val name = details.callerDisplayName.orEmpty().ifBlank { number }
        sendBroadcast(Intent(CallActivity.ACTION_CALL_STATE).setPackage(packageName)
            .putExtra(CallActivity.EXTRA_STATE, call.state)
            .putExtra(CallActivity.EXTRA_NUMBER, number)
            .putExtra(CallActivity.EXTRA_NAME, name)
            .putExtra(CallActivity.EXTRA_INCOMING, details.callDirection == Call.Details.DIRECTION_INCOMING))
    }

    companion object {
        private val calls = LinkedHashMap<Call, Call>()
        @Volatile var activeCall: Call? = null
            private set
        fun answer() { activeCall?.takeIf { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        fun reject() { activeCall?.takeIf { it.state == Call.STATE_RINGING }?.reject(false, null) }
        fun disconnect() { activeCall?.takeIf { it.state != Call.STATE_DISCONNECTED }?.disconnect() }
    }
}
