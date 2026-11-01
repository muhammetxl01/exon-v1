package com.exon.v1

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * İnternet / SIM olmadan; Bluetooth + Wi-Fi Direct üzerinden
 * yakındaki cihazlarla doğrudan (peer-to-peer) mesajlaşma.
 */
class NearbyManager(ctx: Context, private val me: () -> Profile) {

    companion object {
        const val SERVICE_ID = "com.exon.v1"
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(ctx.applicationContext)

    val peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val messages = MutableStateFlow<Map<String, List<Msg>>>(emptyMap())
    val active = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private fun upd(id: String, name: String? = null, f: (Peer) -> Peer) {
        peers.update { m ->
            val cur = m[id] ?: Peer(id = id, name = name ?: id)
            m + (id to f(cur))
        }
    }

    private fun addMsg(id: String, msg: Msg) {
        messages.update { m -> m + (id to ((m[id] ?: emptyList()) + msg)) }
    }

    private val payloadCb = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            try {
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                when (o.optString("t")) {
                    "profile" -> {
                        val pr = Profile.from(o)
                        upd(id) { it.copy(profile = pr, name = pr.name) }
                    }
                    "msg" -> addMsg(id, Msg(o.optString("text"), false, System.currentTimeMillis()))
                }
            } catch (_: Exception) {
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
    }

    private val connCb = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            upd(id, info.endpointName) { it.copy(connecting = true) }
            client.acceptConnection(id, payloadCb)
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                upd(id) { it.copy(connected = true, connecting = false) }
                sendProfile(id)
            } else {
                upd(id) { it.copy(connecting = false) }
            }
        }

        override fun onDisconnected(id: String) {
            upd(id) { it.copy(connected = false, connecting = false) }
        }
    }

    private val discCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            upd(id, info.endpointName) {
                if (it.profile == null) it.copy(name = info.endpointName) else it
            }
        }

        override fun onEndpointLost(id: String) {
            peers.update { m ->
                val p = m[id]
                if (p != null && !p.connected) m - id else m
            }
        }
    }

    fun start() {
        if (active.value) return
        active.value = true
        error.value = null
        val name = me().name

        client.startAdvertising(
            name, SERVICE_ID, connCb,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener {
            error.value = "Yayın başlatılamadı: ${it.message}"
            active.value = false
        }

        client.startDiscovery(
            SERVICE_ID, discCb,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener {
            error.value = "Tarama başlatılamadı: ${it.message}"
            active.value = false
        }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        active.value = false
        peers.update { emptyMap() }
    }

    fun restart() {
        stop()
        start()
    }

    fun connect(id: String) {
        upd(id) { it.copy(connecting = true) }
        client.requestConnection(me().name, id, connCb)
            .addOnFailureListener {
                upd(id) { p -> p.copy(connecting = false) }
                error.value = "Bağlanamadı: ${it.message}"
            }
    }

    fun sendMessage(id: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val o = JSONObject().put("t", "msg").put("text", t)
        client.sendPayload(id, Payload.fromBytes(o.toString().toByteArray(Charsets.UTF_8)))
        addMsg(id, Msg(t, true, System.currentTimeMillis()))
    }

    fun sendProfile(id: String) {
        client.sendPayload(id, Payload.fromBytes(me().toJson().toString().toByteArray(Charsets.UTF_8)))
    }

    fun broadcastProfile() {
        peers.value.values.filter { it.connected }.forEach { sendProfile(it.id) }
    }
}
