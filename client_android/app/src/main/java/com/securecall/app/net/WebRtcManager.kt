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
    private var isClosed = false

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

    /** Tear down everything */
    fun close() {
        Log.d(TAG, "Closing WebRTC")
        isClosed = true  // Prevent callbacks from firing after close
        isDataChannelOpen = false
        try { dataChannel?.close() } catch (_: Exception) {}
        try { peerConnection?.close() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        dataChannel = null
        peerConnection = null
        factory = null
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
                PeerConnection.IceConnectionState.CONNECTED ->
                    com.securecall.app.debug.SecLogManager.log("ICE", "P2P connected — audio should flow")
                PeerConnection.IceConnectionState.FAILED -> {
                    com.securecall.app.debug.SecLogManager.log("ICE", "FAILED — no audio path found")
                    Log.d(TAG, "ICE FAILED — peer disconnected, triggering call teardown")
                    onPeerDisconnect?.invoke()
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    com.securecall.app.debug.SecLogManager.log("ICE", "DISCONNECTED — peer lost")
                    Log.d(TAG, "ICE DISCONNECTED — triggering call teardown")
                    onPeerDisconnect?.invoke()
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
