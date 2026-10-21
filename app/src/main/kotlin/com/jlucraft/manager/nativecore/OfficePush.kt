package com.jlucraft.manager.nativecore

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.edit
import android.content.Intent
import android.content.pm.PackageManager
import com.jlucraft.manager.R
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.security.MessageDigest

/** Private capabilities are scoped to the authenticated device, office chain and VAPID key. */
object OfficePush {
    private const val STORE = "office_push"
    private const val CHANNEL = "office_tasks"
    private const val WAIT_STEPS = 40
    private const val WAIT_MS = 250L
    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private fun data(context: Context, instance: String) = context.getSharedPreferences("$STORE.$instance", Context.MODE_PRIVATE)
    private fun instance(genesis: String, device: String, vapid: String) = MessageDigest.getInstance("SHA-256")
        .digest("$genesis:$device:$vapid".toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun sync(context: Context, secret: ByteArray, address: String, peer: String, genesis: String): Boolean {
        fun call(request: JSONObject): JSONObject = JSONObject(UnionNative.executePush(secret,
            JSONObject().put("address", address).put("peer", peer).put("request", request).toString()))
        val info = call(JSONObject().put("type", "info"))
        if (!info.optBoolean("ok")) return false
        val vapid = info.getString("vapid")
        val id = instance(genesis, UnionNative.peerId(secret), vapid)
        prefs(context).edit { putString("active", id); putString("vapid", vapid) }
        val state = data(context, id)
        if (state.getString("endpoint", null) == null) select(context, id, reset = true)
        else UnifiedPush.register(context, instance = id, vapid = vapid)
        repeat(WAIT_STEPS) { if (state.getString("endpoint", null) == null) delay(WAIT_MS) }
        if (state.getString("endpoint", null) == null) {
            select(context, id)
            repeat(WAIT_STEPS) { if (state.getString("endpoint", null) == null) delay(WAIT_MS) }
        }
        val endpoint = state.getString("endpoint", null) ?: return false
        val challenge = state.getString("challenge", null)
        if (challenge != null) {
            val verified = call(JSONObject().put("type", "verify").put("challenge", challenge))
            if (verified.optBoolean("ok")) {
                state.edit { remove("challenge"); putBoolean("verified", true) }
                return true
            }
        }
        val subscribed = call(JSONObject().put("type", "subscribe").put("endpoint", endpoint)
            .put("p256dh", state.getString("p256dh", "")).put("auth", state.getString("auth", "")))
        if (!subscribed.optBoolean("ok")) {clear(context, id); select(context, id); return false}
        repeat(WAIT_STEPS) { if (state.getString("challenge", null) == null && !state.getBoolean("verified", false)) delay(WAIT_MS) }
        state.getString("challenge", null)?.let {
            if (call(JSONObject().put("type", "verify").put("challenge", it)).optBoolean("ok")) {
                state.edit { remove("challenge"); putBoolean("verified", true) }
            }
        }
        return state.getBoolean("verified", false)
    }

    /** FCM is exposed by our own package. No picker or distributor preference UI. */
    @Synchronized fun select(context: Context, id: String, reset: Boolean = false) {
        if (prefs(context).getString("active", null) != id) return
        val state = data(context, id)
        val attempted = if (reset) emptySet() else state.getStringSet("attempted", emptySet()).orEmpty()
        val candidate = UnifiedPush.getDistributors(context).distinct()
            .sortedWith(compareBy<String> { it != context.packageName }.thenBy { it })
            .firstOrNull { it !in attempted } ?: return
        state.edit { putStringSet("attempted", attempted + candidate) }
        UnifiedPush.saveDistributor(context, candidate)
        UnifiedPush.register(context, instance = id, vapid = prefs(context).getString("vapid", null))
    }

    fun endpoint(context: Context, id: String, endpoint: PushEndpoint) {
        if (prefs(context).getString("active", null) != id) return
        val keys = endpoint.pubKeySet ?: return
        val state = data(context, id)
        val changed = state.getString("endpoint", null) != endpoint.url || state.getString("p256dh", null) != keys.pubKey
        state.edit {
            putString("endpoint", endpoint.url)
            putString("p256dh", keys.pubKey)
            putString("auth", keys.auth)
            if (changed) {
                remove("challenge")
                putBoolean("verified", false)
            }
        }
    }
    fun clear(context: Context, id: String) { data(context, id).edit { remove("endpoint"); remove("challenge"); putBoolean("verified", false) } }
    fun message(context: Context, id: String, message: PushMessage) {
        if (!message.decrypted || prefs(context).getString("active", null) != id) return
        val payload = runCatching { JSONObject(message.content.toString(Charsets.UTF_8)) }.getOrNull() ?: return
        if (payload.has("challenge")) {
            data(context, id).edit { putString("challenge", payload.getString("challenge")) }
            return
        }
        val notice = payload.optString("notice")
        if (notice.isBlank() || !data(context, id).getBoolean("verified", false)) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.office_push_channel), NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(context, MainActivity::class.java).putExtra("office_notice", notice)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, notice.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(notice, 0, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.office_push_channel))
            .setContentText(context.getString(R.string.office_push_open))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
    }
}

class OfficePushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) = OfficePush.endpoint(this, instance, endpoint)
    override fun onMessage(message: PushMessage, instance: String) = OfficePush.message(this, instance, message)
    override fun onRegistrationFailed(reason: FailedReason, instance: String) = OfficePush.select(this, instance)
    override fun onTempUnavailable(instance: String) { OfficePush.clear(this, instance); OfficePush.select(this, instance) }
    override fun onUnregistered(instance: String) { OfficePush.clear(this, instance); OfficePush.select(this, instance) }
}
