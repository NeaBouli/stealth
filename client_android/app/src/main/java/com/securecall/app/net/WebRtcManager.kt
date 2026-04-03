package com.securecall.app.net

import android.util.Log
import com.securecall.app.BuildConfig
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer

/**
 * Manages a WebRTC PeerConnection + DataChannel for P2P audio transport.
 * DataChannel is used instead of audio tracks because we have our own
 * Opus codec + E2E encryption pipeline.
 */
class WebRtcManager(
    private val onLocalSdp: (type: String, sdp: String) -> Unit,
    private val onLocalIceCandidate: (JSONObject) -> Unit,
    private val onDataReceived: (ByteArray) -> Unit,
    private val onPeerDisconnect: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "WEBRTC"
        private const val DC_LABEL = "audio"
    }

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    @Volatile
    var isDataChannelOpen = false
        private set

    @Volatile
    var isClosed = false
        private set

    // WebRTC stats timer for SecLog
    private var statsTimer: java.util.Timer? = null

    // BUG-011: ICE reconnect grace period — don't kill call on transient DISCONNECTED
    private var iceDisconnectHandler: android.os.Handler? = null
    private var iceDisconnectRunnable: Runnable? = null
    private val ICE_RECONNECT_TIMEOUT_MS = 10_000L // 10 seconds grace period

    // Pending queues for messages that arrive before init() completes
    private var pendingOffer: String? = null
    private var pendingAnswer: String? = null
    private val pendingIceCandidates = mutableListOf<JSONObject>()

    fun init(dynamicIceServers: List<PeerConnection.IceServer>? = null) {
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        val iceServers = dynamicIceServers ?: listOf(
            // Fallback: STUN only (no TURN relay) if dynamic fetch failed
            PeerConnection.IceServer.builder(BuildConfig.STUN_URL).createIceServer()
        )
        Log.d(TAG, "Using ${iceServers.size} ICE servers (dynamic=${dynamicIceServers != null})")
        com.securecall.app.debug.SecLogManager.log("ICE", "Init: ${iceServers.size} servers (dynamic=${dynamicIceServers != null})")
        iceServers.forEach { server ->
            Log.d(TAG, "  ICE server: ${server.urls}")
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, pcObserver)
        Log.d(TAG, "PeerConnection created")

        // Drain any messages that arrived before init completed
        pendingOffer?.let { sdp ->
            pendingOffer = null
            onRemoteOffer(sdp)
        }
        pendingAnswer?.let { sdp ->
            pendingAnswer = null
            onRemoteAnswer(sdp)
        }
        if (pendingIceCandidates.isNotEmpty()) {
            Log.d(TAG, "Draining ${pendingIceCandidates.size} pending ICE candidates")
            val candidates = pendingIceCandidates.toList()
            pendingIceCandidates.clear()
            candidates.forEach { onRemoteIceCandidate(it) }
        }
    }

    /** Caller: create DataChannel + SDP offer */
    fun createOffer() {
        val dcInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        }
        dataChannel = peerConnection?.createDataChannel(DC_LABEL, dcInit)
        dataChannel?.registerObserver(dcObserver)
        Log.d(TAG, "DataChannel '$DC_LABEL' created (caller)")

        peerConnection?.createOffer(sdpObserver, MediaConstraints())
    }

    /** Callee: receive remote offer, set it, create answer */
    fun onRemoteOffer(sdp: String) {
        val pc = peerConnection
        if (pc == null) {
            Log.d(TAG, "Queuing remote offer (PeerConnection not ready)")
            pendingOffer = sdp
            return
        }
        val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(setSdpObserver("setRemoteOffer"), desc)
        pc.createAnswer(sdpObserver, MediaConstraints())
        Log.d(TAG, "Remote offer set, creating answer")
    }

    /** Caller: receive remote answer */
    fun onRemoteAnswer(sdp: String) {
        val pc = peerConnection
        if (pc == null) {
            Log.d(TAG, "Queuing remote answer (PeerConnection not ready)")
            pendingAnswer = sdp
            return
        }
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(setSdpObserver("setRemoteAnswer"), desc)
        Log.d(TAG, "Remote answer set")
    }

    /** Add remote ICE candidate */
    fun onRemoteIceCandidate(json: JSONObject) {
        val pc = peerConnection
        if (pc == null) {
            pendingIceCandidates.add(json)
            return
        }
        val candidate = IceCandidate(
            json.optString("sdpMid", ""),
            json.optInt("sdpMLineIndex", 0),
            json.optString("candidate", "")
        )
        pc.addIceCandidate(candidate)
    }

    /** Send data via DataChannel */
    fun send(data: ByteArray): Boolean {
        val dc = dataChannel ?: return false
        if (!isDataChannelOpen) return false
        val buf = DataChannel.Buffer(ByteBuffer.wrap(data), true)
        return dc.send(buf)
    }

    private fun startStatsLogging() {
        statsTimer?.cancel()
        statsTimer = java.util.Timer().apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    val pc = peerConnection ?: return
                    try {
                        pc.getStats { report ->
                            report.statsMap.values.forEach { stats ->
                                when (stats.type) {
                                    "candidate-pair" -> {
                                        if (stats.members["state"] == "succeeded") {
                                            val local = stats.members["localCandidateId"] ?: ""
                                            val remote = stats.members["remoteCandidateId"] ?: ""
                                            com.securecall.app.debug.SecLogManager.log("STATS",
                                                "Active pair: local=$local remote=$remote")
                                        }
                                    }
                                    "inbound-rtp" -> {
                                        val pkts = stats.members["packetsReceived"] ?: "0"
                                        val lost = stats.members["packetsLost"] ?: "0"
                                        val jitter = stats.members["jitter"] ?: "0"
                                        com.securecall.app.debug.SecLogManager.log("STATS",
                                            "RX: pkts=$pkts lost=$lost jitter=$jitter")
                                    }
                                    "outbound-rtp" -> {
                                        val pkts = stats.members["packetsSent"] ?: "0"
                                        com.securecall.app.debug.SecLogManager.log("STATS",
                                            "TX: pkts=$pkts")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }, 5000, 10000) // Start after 5s, repeat every 10s
        }
    }

    /** Tear down everything */
    fun close() {
        Log.d(TAG, "Closing WebRTC")
        isClosed = true  // Prevent callbacks from firing after close
        isDataChannelOpen = false
        cancelIceDisconnectTimeout()
        statsTimer?.cancel()
        statsTimer = null
        try { dataChannel?.close() } catch (_: Exception) {}
        try { peerConnection?.close() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        dataChannel = null
        peerConnection = null
        factory = null
    }

    // BUG-011: ICE reconnect grace period helpers
    private fun scheduleIceDisconnectTimeout() {
        cancelIceDisconnectTimeout()
        iceDisconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
        iceDisconnectRunnable = Runnable {
            if (!isClosed) {
                Log.w(TAG, "ICE did not recover in ${ICE_RECONNECT_TIMEOUT_MS}ms — ending call")
                com.securecall.app.debug.SecLogManager.log("ICE", "Grace period expired — call teardown")
                onPeerDisconnect?.invoke()
            }
        }
        iceDisconnectHandler?.postDelayed(iceDisconnectRunnable!!, ICE_RECONNECT_TIMEOUT_MS)
    }

    private fun cancelIceDisconnectTimeout() {
        iceDisconnectRunnable?.let { iceDisconnectHandler?.removeCallbacks(it) }
        iceDisconnectHandler = null
        iceDisconnectRunnable = null
    }

    // ===================== PeerConnection Observer =====================

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            if (isClosed) return
            val candidateType = when {
                candidate.sdp.contains("typ host") -> "host"
                candidate.sdp.contains("typ srflx") -> "srflx"
                candidate.sdp.contains("typ relay") -> "relay (TURN)"
                candidate.sdp.contains("typ prflx") -> "prflx"
                else -> "unknown"
            }
            Log.d(TAG, "Local ICE candidate [$candidateType]: ${candidate.sdp.take(60)}...")
            com.securecall.app.debug.SecLogManager.log("ICE", "Candidate: $candidateType")
            val json = JSONObject().apply {
                put("candidate", candidate.sdp)
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
            }
            onLocalIceCandidate(json)
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (isClosed) return
            Log.d(TAG, "ICE connection state: $state")
            com.securecall.app.debug.SecLogManager.log("ICE", "State: $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    com.securecall.app.debug.SecLogManager.log("ICE", "P2P connected — audio should flow")
                    // BUG-011: Cancel any pending disconnect timeout — ICE recovered
                    cancelIceDisconnectTimeout()
                    startStatsLogging()
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    com.securecall.app.debug.SecLogManager.log("ICE", "FAILED — no audio path found")
                    Log.d(TAG, "ICE FAILED — no recovery possible, triggering call teardown")
                    cancelIceDisconnectTimeout()
                    onPeerDisconnect?.invoke()
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    // BUG-011: Don't kill call immediately — give ICE 10s to reconnect.
                    // Transient disconnects (WiFi↔mobile, brief signal loss) often recover.
                    com.securecall.app.debug.SecLogManager.log("ICE", "DISCONNECTED — waiting ${ICE_RECONNECT_TIMEOUT_MS}ms for recovery")
                    Log.d(TAG, "ICE DISCONNECTED — starting ${ICE_RECONNECT_TIMEOUT_MS}ms grace period")
                    scheduleIceDisconnectTimeout()
                }
                else -> {}
            }
        }

        override fun onDataChannel(dc: DataChannel) {
            if (isClosed) return
            // Callee receives the DataChannel here
            Log.d(TAG, "DataChannel received (callee): ${dc.label()}")
            dataChannel = dc
            dc.registerObserver(dcObserver)
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {
            Log.d(TAG, "Signaling state: $state")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }

    // ===================== DataChannel Observer =====================

    private val dcObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}

        override fun onStateChange() {
            if (isClosed) return
            val state = dataChannel?.state()
            Log.d(TAG, "DataChannel state: $state")
            isDataChannelOpen = (state == DataChannel.State.OPEN)
            if (isDataChannelOpen) {
                Log.d(TAG, "DataChannel opened — P2P audio transport active")
            } else if (state == DataChannel.State.CLOSED) {
                Log.d(TAG, "DataChannel closed — peer disconnected, triggering call teardown")
                onPeerDisconnect?.invoke()
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                onDataReceived(data)
            }
        }
    }

    // ===================== SDP Observer =====================

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {
            Log.d(TAG, "SDP created: ${desc.type}")
            peerConnection?.setLocalDescription(setSdpObserver("setLocal"), desc)
            onLocalSdp(desc.type.canonicalForm(), desc.description)
        }

        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "SDP create failed: $error")
        }

        override fun onSetSuccess() {}
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "SDP set failed: $error")
        }
    }

    private fun setSdpObserver(label: String) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetSuccess() {
            Log.d(TAG, "$label success")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "$label failed: $error")
        }
    }
}
