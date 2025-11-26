
    // BACKEND-59: Router-Rebind auf neue Transport-Pipeline
    fun rebind() {
        android.util.Log.w(TAG, "Rebinding GhostControlRouter → new Transport/Parser instance")

        // später: Listener-Objekte / Parser-Instanzen ersetzen
        // aktuell: nur Marker + Logging für Vollständigkeit
    }

    // BACKEND-61: zentraler Frame-Router (Skeleton v2)
    fun routeIncoming(frame: ByteArray) {
        val size = frame.size
        android.util.Log.d(TAG, "routeIncoming() called, size=$size")

        if (isControlFrame(frame)) {
            android.util.Log.d(TAG, "routeIncoming() → CONTROL (placeholder, no-op)")
            // später:
            // ControlFrameParser.parse(frame)
        } else {
            android.util.Log.d(TAG, "routeIncoming() → MEDIA (placeholder, no-op)")
            // später:
            // Audio/Media-Handling hier integrieren
        }
    }

    // BACKEND-61: primitive Platzhalter-Heuristik
    private fun isControlFrame(frame: ByteArray): Boolean {
        // MVP: immer true – wird später durch echtes Header-Format ersetzt
        return frame.isNotEmpty()
    }

    // BACKEND-62: parse() für ControlFrames aktivieren
    private fun dispatchControl(frame: ByteArray) {
        try {
            ControlFrameParser.parse(frame)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "dispatchControl() failed", t)
        }
    }

    // BACKEND-62: Routing für echte Control-Verarbeitung
    if (isControlFrame(frame)) {
        android.util.Log.d(TAG, "routeIncoming() → CONTROL → parse()")
        dispatchControl(frame)
    }

    // BACKEND-63: MediaFrame dispatch
    private fun dispatchMedia(frame: ByteArray) {
        try {
            val media = com.securecall.app.ghostnet.media.MediaFrame(frame)
            com.securecall.app.ghostnet.media.GhostMediaRouter.route(media)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "dispatchMedia() failed", t)
        }
    }

    // BACKEND-63: MEDIA-Frames weiterreichen
    if (!isControlFrame(frame)) {
        android.util.Log.d(TAG, "routeIncoming() → MEDIA → dispatchMedia()")
        dispatchMedia(frame)
    }
