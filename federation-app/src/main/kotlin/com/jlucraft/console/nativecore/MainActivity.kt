package com.jlucraft.console.nativecore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.view.WindowManager
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
    private lateinit var vault: BiometricVault
    private var unlocked by mutableStateOf(false)
    private var lockEpoch by mutableStateOf(0L)
    override fun onStop() {
        unlocked = false
        lockEpoch++
        vault.cancel()
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        vault = BiometricVault(this)
        val preferences = getPreferences(MODE_PRIVATE)
        setContent {
            JluManagerTheme {
                val scope = rememberCoroutineScope()
                var address by remember { mutableStateOf(preferences.getString("address", "") ?: "") }
                var peer by remember { mutableStateOf(preferences.getString("peer", "") ?: "") }
                var identity by remember { mutableStateOf("") }
                var state by remember { mutableStateOf<JSONObject?>(null) }
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var league by remember { mutableStateOf("") }
                var seats by remember { mutableStateOf("4") }
                var crossClub by remember { mutableStateOf(false) }
                var host by remember { mutableStateOf("") }
                var scorer by remember { mutableStateOf("") }
                var memberId by remember { mutableStateOf("") }
                var memberClub by remember { mutableStateOf("jlu") }
                var memberDevice by remember { mutableStateOf("") }
                LaunchedEffect(lockEpoch) { state = null; busy = false; identity = "" }
                fun unlock() {
                    if (busy) return
                    busy = true
                    vault.authenticate("解锁联盟管理", { secret ->
                        identity = UnionNative.peerId(secret)
                        unlocked = true
                        busy = false
                        message = ""
                    }, { message = it; busy = false })
                }
                fun request(action: JSONObject? = null) {
                    if (busy || !unlocked) return
                    val requestEpoch = lockEpoch
                    val requestAddress = address
                    val requestPeer = peer
                    val input = JSONObject().put("address", requestAddress).put("peer", requestPeer)
                    val protectedTypes = setOf("grant_admin", "revoke_admin", "rotate_member_device", "enroll", "create_league", "update_council", "set_consensus_members", "create_season", "schedule_league", "resolve_dispute")
                    val proposed = action != null && state?.optJSONObject("council") != null && action.optString("type") in protectedTypes
                    val submitted = if (proposed) JSONObject().put("type", "propose").put("id", java.util.UUID.randomUUID().toString()).put("action", action).put("expires_ms", System.currentTimeMillis() + 7 * 86400000L) else action
                    submitted?.let { input.put("action", it).put("revision", state?.getLong("revision")) }
                    busy = true
                    vault.authenticate(if (action == null) "同步联盟信息" else "确认管理操作", { secret ->
                        val material = secret.copyOf()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    try { JSONObject(UnionNative.execute(material, input.toString())) }
                                    finally { material.fill(0) }
                                }
                                if (lockEpoch == requestEpoch && unlocked) {
                                    if (!result.isNull("error")) error(result.getString("error"))
                                    state = result.getJSONObject("state")
                                    preferences.edit().putString("address", requestAddress).putString("peer", requestPeer).apply()
                                    message = if (action == null) "已同步" else if (proposed) "提案已提交，等待其他社团确认" else "操作已完成"
                                }
                            } catch (error: Exception) {
                                if (lockEpoch == requestEpoch) message = "操作未完成，请检查连接后重试"
                            } finally {
                                material.fill(0)
                                if (lockEpoch == requestEpoch) busy = false
                            }
                        }
                    }, { if (lockEpoch == requestEpoch) { message = it; busy = false } })
                }
                val seatsValid = (seats.toIntOrNull() ?: 0) > 0
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Column {
                            Text("高校联盟管理", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "吉林大学 · 社团联赛组织端",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!unlocked) {
                            Text("验证身份后继续管理联赛与成员")
                            if (message.isNotBlank()) Text(message)
                            Button(onClick = { unlock() }, enabled = !busy) { Text("验证身份") }
                        } else {
                        ConnectionCard(
                            address = address,
                            peer = peer,
                            identity = identity,
                            message = message,
                            connected = state != null,
                            leagueCount = state?.optJSONObject("leagues")?.length() ?: 0,
                            busy = busy,
                            onAddressChange = { address = it; state = null },
                            onPeerChange = { peer = it; state = null },
                            onConnect = { request() },
                        )
                        GovernanceCard(state, identity, busy) { request(it) }
                        SeasonCard(state, identity, busy) { request(it) }
                        EnrollmentCard(
                            memberId = memberId,
                            memberClub = memberClub,
                            memberDevice = memberDevice,
                            connected = state != null,
                            busy = busy,
                            onMemberIdChange = { memberId = it },
                            onMemberClubChange = { memberClub = it },
                            onMemberDeviceChange = { memberDevice = it },
                            onEnroll = {
                                request(JSONObject().put("type", "enroll").put("member", memberId).put("club", memberClub).put("device", memberDevice))
                            },
                        )
                        CreateLeagueCard(
                            league = league,
                            seats = seats,
                            crossClub = crossClub,
                            host = host,
                            scorer = scorer,
                            connected = state != null,
                            busy = busy,
                            seatsValid = seatsValid,
                            onLeagueChange = { league = it },
                            onSeatsChange = { seats = it },
                            onCrossClubChange = { crossClub = it },
                            onHostChange = { host = it },
                            onScorerChange = { scorer = it },
                            onCreate = {
                                request(JSONObject().put("type", "create_league").put("league", league).put("host", host).put("scorer", scorer)
                                    .put("rules", JSONObject().put("cross_club", crossClub).put("seats", seats.toInt()).put("reconnect_min_ms", 15000).put("reconnect_max_ms", 90000)))
                            },
                        )
                        LeagueListSection(
                            state = state,
                            busy = busy,
                            onStart = { id ->
                                val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
                                request(JSONObject().put("type", "start").put("league", id).put("seed", JSONArray(seed.map { it.toInt() and 255 })))
                            },
                        )
                        }
                    }
                }
            }
        }
    }
}
