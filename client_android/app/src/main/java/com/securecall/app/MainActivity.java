package com.securecall.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnCall = findViewById(R.id.btnCall);
        Button btnSettings = findViewById(R.id.btnSettings);

        btnCall.setOnClickListener(v ->
                startActivity(new Intent(this, CallActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }
}

    // BACKEND-22: WebSocketService Binding
    private com.securecall.app.net.WebSocketService wsService;
    private android.content.ServiceConnection wsConn =
        new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
                wsService = ((com.securecall.app.net.WebSocketService.LocalBinder) binder).getService();
                android.util.Log.d("MAIN", "WS Service connected");
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
                wsService = null;
                android.util.Log.d("MAIN", "WS Service disconnected");
            }
        };

    @Override
    protected void onStart() {
        super.onStart();
        // Service starten & binden
        android.content.Intent i =
            new android.content.Intent(this, com.securecall.app.net.WebSocketService.class);
        bindService(i, wsConn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (wsService != null) {
            unbindService(wsConn);
            wsService = null;
        }
    }

    // BACKEND-22: WS Test Button Handler
    @Override
    protected void onResume() {
        super.onResume();

        Button btnWsTest = findViewById(R.id.btnWsTest);
        btnWsTest.setOnClickListener(v -> {
            if (wsService != null) {
                wsService.sendMessage("{\"type\":\"TEST\",\"payload\":\"hello_from_android\"}");
                android.util.Log.d("MAIN", "WS Test message sent");
            } else {
                android.util.Log.d("MAIN", "WS Service not connected yet");
            }
        });
    }

    // BACKEND-22: WS-Statusanzeige aktualisieren
    private void updateWsStatus(boolean isOnline) {
        TextView status = findViewById(R.id.wsStatus);
        if (isOnline) {
            status.setText("WS: ONLINE");
            status.setTextColor(android.graphics.Color.parseColor("#00AA00"));
        } else {
            status.setText("WS: OFFLINE");
            status.setTextColor(android.graphics.Color.parseColor("#CC0000"));
        }
    }

    // Hook in die bestehenden Lifecycle Methoden / WS-Events einfügen
    @Override
    protected void onStart() {
        super.onStart();
        android.content.Intent i =
            new android.content.Intent(this, com.securecall.app.net.WebSocketService.class);
        bindService(i, wsConn, BIND_AUTO_CREATE);
        updateWsStatus(false);  // default offline until connected
    }

    // Wird vom Service aufgerufen
    private final Runnable wsOnlineNotifier = () -> updateWsStatus(true);
    private final Runnable wsOfflineNotifier = () -> updateWsStatus(false);

    // BACKEND-22: Callback-Anbindung nach onServiceConnected
    private void bindWsCallbacks() {
        if (wsService != null) {
            wsService.statusCallbackOnline = () -> runOnUiThread(wsOnlineNotifier);
            wsService.statusCallbackOffline = () -> runOnUiThread(wsOfflineNotifier);
        }
    }

    // Ergänzung innerhalb von onServiceConnected
    @Override
    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
        wsService = ((com.securecall.app.net.WebSocketService.LocalBinder) binder).getService();
        android.util.Log.d("MAIN", "WS Service connected");
        bindWsCallbacks(); // hier einfügen
    }

    // BACKEND-22: Timer für LastSeen-Überwachung
    private java.util.Timer wsTimer;

    private void startLastSeenTimer() {
        TextView lastSeenView = findViewById(R.id.wsLastSeen);
        wsTimer = new java.util.Timer();
        wsTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (wsService != null) {
                    long diff = System.currentTimeMillis() - wsService.lastSeen();
                    runOnUiThread(() -> lastSeenView.setText("LastSeen: " + diff + "ms"));
                }
            }
        }, 0, 1000); // 1 Sekunde
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLastSeenTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wsTimer != null) {
            wsTimer.cancel();
            wsTimer = null;
        }
    }

    // BACKEND-22: Callback für WS-Fehler
    private void bindWsErrorCallback() {
        if (wsService != null) {
            wsService.errorCallback = (Throwable t) -> {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(
                        this,
                        "WS Error: " + t.getMessage(),
                        android.widget.Toast.LENGTH_SHORT
                    ).show();
                    updateWsStatus(false);
                });
            };
        }
    }

    // in onServiceConnected anhängen
    @Override
    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
        wsService = ((com.securecall.app.net.WebSocketService.LocalBinder) binder).getService();
        android.util.Log.d("MAIN", "WS Service connected");

        bindWsCallbacks();
        bindWsErrorCallback();   // <--- HIER
    }

    // BACKEND-22: Callback für WS-Fehler
    private void bindWsErrorCallback() {
        if (wsService != null) {
            wsService.errorCallback = (Throwable t) -> {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(
                        this,
                        "WS Error: " + t.getMessage(),
                        android.widget.Toast.LENGTH_SHORT
                    ).show();
                    updateWsStatus(false);
                });
            };
        }
    }

    // in onServiceConnected anhängen
    @Override
    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
        wsService = ((com.securecall.app.net.WebSocketService.LocalBinder) binder).getService();
        android.util.Log.d("MAIN", "WS Service connected");

        bindWsCallbacks();
        bindWsErrorCallback();   // <--- HIER
    }

    // BACKEND-22: CALL_INVITE Debug-Knopf verbinden
    @Override
    protected void onResume() {
        super.onResume();

        // bestehender Code für btnWsTest bleibt unberührt

        Button btnCallInvite = findViewById(R.id.btnCallInvite);
        btnCallInvite.setOnClickListener(v -> {
            if (wsService != null) {
                wsService.sendCallInvite("peer-123");
                android.util.Log.d("MAIN", "CALL_INVITE sent to peer-123");
            }
        });
    }

    // BACKEND-22: CALL_ACCEPT Debug Handler
    private void setupAcceptButton() {
        Button btn = findViewById(R.id.btnCallAccept);
        btn.setOnClickListener(v -> {
            if (wsService == null) return;

            String sessionId = com.securecall.app.session.SessionManager.currentSessionId;
            if (sessionId != null) {
                wsService.sendCallAccept(sessionId);
                android.util.Log.d("MAIN", "CALL_ACCEPT sent for " + sessionId);
            } else {
                android.util.Log.d("MAIN", "No sessionId available");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupAcceptButton();
    }

    // BACKEND-22: CALL_END Handler
    private void setupEndCallButton() {
        Button btn = findViewById(R.id.btnCallEnd);
        btn.setOnClickListener(v -> {
            if (wsService == null) return;

            String sessionId = com.securecall.app.session.SessionManager.currentSessionId;
            if (sessionId != null) {
                wsService.sendCallEnd(sessionId);
                com.securecall.app.session.SessionManager.clear();
                android.util.Log.d("MAIN", "CALL_END sent for " + sessionId);
            } else {
                android.util.Log.d("MAIN", "No active session to end");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupEndCallButton();
    }

    // BACKEND-23: Ghost Prepare Button Setup
    private void setupGhostPrepButton() {
        Button btn = findViewById(R.id.btnGhostPrep);
        btn.setOnClickListener(v -> {
            if (wsService == null) return;

            String sessionId = com.securecall.app.session.SessionManager.currentSessionId;
            if (sessionId != null) {
                wsService.sendGhostPrepare(sessionId);
                android.util.Log.d("MAIN", "GHOST_PREPARE sent for " + sessionId);
            } else {
                android.util.Log.d("MAIN", "No active session for GHOST_PREPARE");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupGhostPrepButton();
    }

    // BACKEND-24: GhostNet Status aktualisieren
    private void updateGhostStatus() {
        TextView ghost = findViewById(R.id.ghostStatus);
        String id = com.securecall.app.ghostnet.GhostNetSession.ghostNetId;

        if (id != null) {
            ghost.setText("GhostNet: READY (" + id + ")");
            ghost.setTextColor(android.graphics.Color.parseColor("#008800"));
        } else {
            ghost.setText("GhostNet: Not ready");
            ghost.setTextColor(android.graphics.Color.parseColor("#AA0000"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGhostStatus();
    }

    // BACKEND-24: GhostNet Status aktualisieren
    private void updateGhostStatus() {
        TextView ghost = findViewById(R.id.ghostStatus);
        String id = com.securecall.app.ghostnet.GhostNetSession.ghostNetId;

        if (id != null) {
            ghost.setText("GhostNet: READY (" + id + ")");
            ghost.setTextColor(android.graphics.Color.parseColor("#008800"));
        } else {
            ghost.setText("GhostNet: Not ready");
            ghost.setTextColor(android.graphics.Color.parseColor("#AA0000"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGhostStatus();
    }

    // BACKEND-24: GhostNet Relay-Hints Debug-Button
    private void setupGhostHintsButton() {
        Button btn = findViewById(R.id.btnGhostHints);
        btn.setOnClickListener(v -> {
            java.util.List<com.securecall.app.ghostnet.GhostNetRelayHint> hints =
                    com.securecall.app.ghostnet.GhostNetSession.relayHints;

            if (hints == null || hints.isEmpty()) {
                android.widget.Toast
                        .makeText(this, "No GhostNet relay hints available", android.widget.Toast.LENGTH_SHORT)
                        .show();
                android.util.Log.d("MAIN", "[GHOST] No relay hints in session");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (com.securecall.app.ghostnet.GhostNetRelayHint h : hints) {
                sb.append(h.getHost()).append(":").append(h.getPort()).append("  ");
            }

            String msg = "GhostNet relays: " + sb.toString();
            android.widget.Toast
                    .makeText(this, msg, android.widget.Toast.LENGTH_LONG)
                    .show();
            android.util.Log.d("MAIN", "[GHOST] Relay hints = " + msg);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // bestehende onResume-Logik bleibt unverändert; diese Methode wird am Ende aufgerufen
        setupGhostHintsButton();
    }

    // BACKEND-24: Debug-Panel aktualisieren
    private void updateGhostDebug(String msg) {
        TextView dbg = findViewById(R.id.ghostDebugPanel);
        dbg.setText("Ghost Debug Panel:\n" + msg);
        android.util.Log.d("GHOSTDBG", msg);
    }

    // BACKEND-24: Button für Ghost Debug Info
    private void setupGhostDebugButton() {
        Button dbgBtn = findViewById(R.id.btnShowGhostDebug);
        dbgBtn.setOnClickListener(v -> {
            String info = com.securecall.app.ghostnet.GhostNetSession.getDebugInfo();
            updateGhostDebug(info);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupGhostDebugButton(); // neuer Debug-Button
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // BACKEND-25: Session Soft-Reset bei App-Start
        com.securecall.app.ghostnet.GhostNetSession.softReset();
    }

    // BACKEND-26: Manuelles Forcieren des Reconnect-Prozesses
    private void setupForceReconnectButton() {
        Button btn = findViewById(R.id.btnForceReconnect);
        btn.setOnClickListener(v -> {
            if (wsService != null) {
                android.util.Log.w("MAIN", "[DEBUG] Forcing WS reconnect");
                wsService.forceReconnect();
            } else {
                android.util.Log.w("MAIN", "[DEBUG] WS service is null");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupForceReconnectButton();
    }

    // BACKEND-27: Simulation eines GhostNet Pre-Handshake
    private void setupSimulateHandshakeButton() {
        Button b = findViewById(R.id.btnSimulateGhostHandshake);
        b.setOnClickListener(v -> {

            // Fake Relay Hints vorbereiten
            java.util.List<com.securecall.app.ghostnet.GhostNetRelayHint> relays =
                new java.util.ArrayList<>();
            relays.add(new com.securecall.app.ghostnet.GhostNetRelayHint("relay1.example.net", 3478));
            relays.add(new com.securecall.app.ghostnet.GhostNetRelayHint("relay2.example.net", 3478));

            // Session füllen
            com.securecall.app.ghostnet.GhostNetSession.setSessionData(
                "SIMULATED-SESSION-" + System.currentTimeMillis(),
                relays
            );

            // Debug Panel aktualisieren
            String dbg = com.securecall.app.ghostnet.GhostNetSession.getDebugInfo();
            updateGhostDebug(dbg);

            android.util.Log.d("MAIN", "[SIM] Simulated Ghost Pre-Handshake: " + dbg);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSimulateHandshakeButton();
    }

    // BACKEND-28: Validierte Session für Simulation (optional)
    private void applyValidatedSimulatedSession() {
        java.util.List<com.securecall.app.ghostnet.GhostNetRelayHint> list =
                com.securecall.app.ghostnet.GhostNetSession.getSafeRelayHints();

        String dbg = "Validated: " +
                com.securecall.app.ghostnet.GhostNetSession.getSafeSessionId() +
                " | relays=" + list.size();

        updateGhostDebug(dbg);
        android.util.Log.d("MAIN", "[SIM-VALIDATED] " + dbg);
    }

    // BACKEND-28: Validierte Session für Simulation (optional)
    private void applyValidatedSimulatedSession() {
        java.util.List<com.securecall.app.ghostnet.GhostNetRelayHint> list =
                com.securecall.app.ghostnet.GhostNetSession.getSafeRelayHints();

        String dbg = "Validated: " +
                com.securecall.app.ghostnet.GhostNetSession.getSafeSessionId() +
                " | relays=" + list.size();

        updateGhostDebug(dbg);
        android.util.Log.d("MAIN", "[SIM-VALIDATED] " + dbg);
    }

    // BACKEND-29: State Update für die Simulation
    private void applySimulatedPreparedState() {
        com.securecall.app.ghostnet.GhostNetSession.markPrepared();
        updateGhostDebug("State → PREPARED");
    }

    // BACKEND-30: Farbige Anzeige des GhostNet-Status
    private void updateGhostStateBar() {
        TextView bar = findViewById(R.id.ghostStateBar);
        com.securecall.app.ghostnet.GhostNetSession.State s =
                com.securecall.app.ghostnet.GhostNetSession.getState();

        bar.setText("STATE: " + s.name());

        switch (s) {
            case INIT:
                bar.setBackgroundColor(android.graphics.Color.parseColor("#555555"));
                break;
            case PREPARED:
                bar.setBackgroundColor(android.graphics.Color.parseColor("#DDBB00")); // gelb
                break;
            case ACTIVE:
                bar.setBackgroundColor(android.graphics.Color.parseColor("#00AA00")); // grün
                break;
            case DEAD:
                bar.setBackgroundColor(android.graphics.Color.parseColor("#AA0000")); // rot
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGhostStateBar();
    }

    // BACKEND-30: After preparing simulated session
    private void applySimulatedPreparedStateUI() {
        updateGhostStateBar();
    }

    // BACKEND-30: Debug-Panel automatisch aktualisieren, wenn State sich ändert
    private void refreshDebugAfterStateChange() {
        String dbg =
            com.securecall.app.ghostnet.GhostNetSession.getDebugInfo() +
            "\nSTATE = " +
            com.securecall.app.ghostnet.GhostNetSession.getState().name();

        updateGhostDebug(dbg);
        updateGhostStateBar();
    }

    // BACKEND-31: State Listener für UI
    private final com.securecall.app.ghostnet.GhostNetSession.StateListener ghostStateListener =
            state -> runOnUiThread(() -> {
                updateGhostStateBar();
                refreshDebugAfterStateChange();
            });

    @Override
    protected void onStart() {
        super.onStart();
        com.securecall.app.ghostnet.GhostNetSession.addStateListener(ghostStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.securecall.app.ghostnet.GhostNetSession.removeStateListener(ghostStateListener);
    }

    // BACKEND-32: Lifecycle Listener (für UI)
    private final com.securecall.app.ghostnet.GhostNetSession.LifecycleListener lifecycleListener =
        new com.securecall.app.ghostnet.GhostNetSession.LifecycleListener() {
            @Override
            public void onPrepared() {
                runOnUiThread(() -> {
                    updateGhostDebug("Lifecycle: PREPARED");
                    updateGhostStateBar();
                });
            }

            @Override
            public void onActivated() {
                runOnUiThread(() -> {
                    updateGhostDebug("Lifecycle: ACTIVE");
                    updateGhostStateBar();
                });
            }

            @Override
            public void onDead() {
                runOnUiThread(() -> {
                    updateGhostDebug("Lifecycle: DEAD");
                    updateGhostStateBar();
                });
            }
        };

    @Override
    protected void onStart() {
        super.onStart();
        com.securecall.app.ghostnet.GhostNetSession.addLifecycleListener(lifecycleListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.securecall.app.ghostnet.GhostNetSession.removeLifecycleListener(lifecycleListener);
    }

    // BACKEND-33: Test-Button – Transport manuell starten
    private void setupDebugActivateTransportButton() {
        Button b = new Button(this);
        b.setText("Force ACTIVE (Transport)");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.GhostNetSession.markActive();
        });

        ((android.widget.LinearLayout) findViewById(R.id.rootLayout))
                .addView(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupDebugActivateTransportButton();
    }

    // BACKEND-34: Debug Button — enqueue test frame
    private void setupTestFrameButton() {
        Button b = new Button(this);
        b.setText("Queue TestFrame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestFrame();
        });

        ((android.widget.LinearLayout) findViewById(R.id.rootLayout))
                .addView(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupTestFrameButton();
    }

    // BACKEND-35: Root-Layout Getter
    private LinearLayout getRootLayout() {
        return findViewById(R.id.rootLayout);
    }

    // BACKEND-35: Utility zum Hinzufügen von Debug-Buttons
    private void addDebugButton(Button b) {
        getRootLayout().addView(b);
    }

    // Beispiel: Nutzung in bestehenden Debug-Setups
    private void setupTestFrameButtonFixed() {
        Button b = new Button(this);
        b.setText("Queue TestFrame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestFrame();
        });
        addDebugButton(b);
    }

    // BACKEND-38: Button für Test-AudioFrame
    private void setupTestAudioFrameButton() {
        Button b = new Button(this);
        b.setText("Queue Test AudioFrame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestAudioFrame();
        });
        addDebugButton(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupTestAudioFrameButton();
    }

    // BACKEND-39: Debug Button — Builder-Test
    private void setupTestBuilderButton() {
        Button b = new Button(this);
        b.setText("Queue Builder Frame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueBuilderTest();
        });
        addDebugButton(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupTestBuilderButton();
    }

    // BACKEND-40: Debug Button — Capture AudioFrame
    private void setupDebugCaptureButton() {
        Button b = new Button(this);
        b.setText("Capture Fake AudioFrame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueCapturedAudioFrame();
        });
        addDebugButton(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupDebugCaptureButton();
    }

    // BACKEND-40: Debug Button — Capture AudioFrame
    private void setupDebugCaptureButton() {
        Button b = new Button(this);
        b.setText("Capture Fake AudioFrame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueCapturedAudioFrame();
        });
        addDebugButton(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupDebugCaptureButton();
    }

    // BACKEND-41: Session-Status aktualisieren
    private void updateSessionStatus(com.securecall.app.ghostnet.session.GhostNetSessionState state) {
        android.widget.TextView tv = findViewById(R.id.sessionStatus);
        if (tv == null) return;

        String label;
        int color;

        if (state == com.securecall.app.ghostnet.session.GhostNetSessionState.ACTIVE) {
            label = "SESSION: ACTIVE";
            color = android.graphics.Color.parseColor("#00AA00");
        } else if (state == com.securecall.app.ghostnet.session.GhostNetSessionState.CONNECTING) {
            label = "SESSION: CONNECTING";
            color = android.graphics.Color.parseColor("#FFA500");
        } else if (state == com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD) {
            label = "SESSION: DEAD";
            color = android.graphics.Color.parseColor("#CC0000");
        } else {
            label = "SESSION: IDLE";
            color = android.graphics.Color.parseColor("#666666");
        }

        tv.setText(label);
        tv.setTextColor(color);
    }

    // BACKEND-41: Helfer, um den aktuellen Modellzustand abzuholen
    private void refreshSessionStatusFromModel() {
        com.securecall.app.ghostnet.session.GhostNetSessionState state =
            com.securecall.app.ghostnet.session.GhostNetSession.get().getState();
        updateSessionStatus(state);
    }

    // BACKEND-41: Ergänzung für onResume – Sessionstatus auffrischen
    @Override
    protected void onResume() {
        super.onResume();
        // Bestehende Debug-Setup-Calls bleiben wie gehabt.
        refreshSessionStatusFromModel();
    }

    // BACKEND-42: Debug-Button — aktuellen Session-Status ins Log schreiben
    private void setupPrintSessionStateButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Print Session State");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.session.GhostNetSessionState state =
                com.securecall.app.ghostnet.session.GhostNetSession.get().getState();
            android.util.Log.d("MAIN", "Current GhostNetSessionState = " + state);
        });
        addDebugButton(b);
    }

    // BACKEND-43: Debug — zufälligen Frame erzeugen
    private void setupRandomFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Random Frame");
        b.setOnClickListener(v -> {
            byte[] data = new byte[64];
            new java.util.Random().nextBytes(data);
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .enqueueTestFrame(); // nutzt bereits Dummy-Daten
        });
        addDebugButton(b);
    }

    // BACKEND-45: Debug — Test-AudioFrame erzeugen
    private void setupDecodeTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Decode TestFrame");
        b.setOnClickListener(v -> {
            byte[] pcm = new byte[96];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (byte)(i & 0x7F);
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .enqueueTestAudioFrame();
        });
        addDebugButton(b);
    }

    // BACKEND-46: Test — AudioFrame-Ausgabe simulieren
    private void setupPlayTestPcmButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Play Test PCM");
        b.setOnClickListener(v -> {
            byte[] pcm = new byte[128];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (byte)(i % 64);
            com.securecall.app.audio.output.AudioOutput.play(pcm);
        });
        addDebugButton(b);
    }

    // BACKEND-47: AudioTrack Init (Placeholder)
    private void setupAudioTrackInitButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Init AudioTrack");
        b.setOnClickListener(v -> {
            com.securecall.app.audio.output.AudioOutput.initTrack();
        });
        addDebugButton(b);
    }

    // BACKEND-48: Debug — JitterBuffer Größe anzeigen
    private void setupJitterSizeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Jitter Size");
        b.setOnClickListener(v -> {
            int s = com.securecall.app.audio.jitter.JitterBuffer.size();
            android.util.Log.d("MAIN", "JitterBuffer size = " + s);
        });
        addDebugButton(b);
    }

    // BACKEND-49: Debug — Test-ControlFrame an Parser schicken
    private void setupControlFrameTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test ControlFrame");
        b.setOnClickListener(v -> {
            // Beispiel: [0] = 0x02 (ControlFrame), [1] = 0x03 (PING)
            byte[] ctrl = new byte[] {
                (byte)0x02,
                (byte)0x03,
                (byte)0x00,
                (byte)0x00
            };
            com.securecall.app.ghostnet.control.ControlFrameParser.parse(ctrl);
        });
        addDebugButton(b);
    }

    // BACKEND-51: Debug — ControlFrameBuilder testen
    private void setupControlBuilderTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Build ControlFrame");
        b.setOnClickListener(v -> {
            byte[] f = com.securecall.app.ghostnet.control.ControlFrameBuilder.ping();
            android.util.Log.d("MAIN", "Built ControlFrame (opcode=PING, len=" + f.length + ")");
        });
        addDebugButton(b);
    }

    // BACKEND-52: Debug — ControlFrame über Transport schicken
    private void setupSendControlButtons() {

        // Ping
        android.widget.Button b1 = new android.widget.Button(this);
        b1.setText("Send PING");
        b1.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendPing();
        });
        addDebugButton(b1);

        // Mute
        android.widget.Button b2 = new android.widget.Button(this);
        b2.setText("Send MUTE");
        b2.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendMute();
        });
        addDebugButton(b2);

        // Unmute
        android.widget.Button b3 = new android.widget.Button(this);
        b3.setText("Send UNMUTE");
        b3.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendUnmute();
        });
        addDebugButton(b3);
    }

    // BACKEND-53: Debug — Ping Roundtrip Zeit messen
    private void setupPingRttButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Ping RTT");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendPingWithTimestamp();
        });
        addDebugButton(b);
    }

    // BACKEND-55: Debug — Session künstlich auf DEAD setzen
    private void setupForceDeadSessionButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Force DEAD Session");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.session.GhostNetSession
                .get()
                .setState(com.securecall.app.ghostnet.session.GhostNetSessionState.DEAD);
        });
        addDebugButton(b);
    }

    // BACKEND-56: Debug – künstlich WS-Disconnect auslösen
    private void setupForceWsDisconnectButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Force WS Disconnect");
        b.setOnClickListener(v -> {
            if (wsService != null) {
                wsService.forceDisconnectForDebug(); // gleich implementiert
            }
        });
        addDebugButton(b);
    }

    // BACKEND-57: Full WS/Session/Transport reconnect testen
    private void setupTestFullReconnectButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Full Reconnect");
        b.setOnClickListener(v -> {
            if (wsService != null) {
                wsService.reconnectFlow("DEBUG_BUTTON");
            }
        });
        addDebugButton(b);
    }

    // BACKEND-58: Debug – manuelle Transport-Reinitialisierung
    private void setupTransportReinitButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Transport ReInit");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().reinitAfterReconnect();
        });
        addDebugButton(b);
    }

    // BACKEND-59: Debug — Router-Rebind manuell triggern
    private void setupRouterRebindButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Router Rebind");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.control.GhostControlRouter.rebind();
        });
        addDebugButton(b);
    }

    // BACKEND-60: Flow-Collector für Session-Status
    private void observeSessionState() {
        kotlinx.coroutines.GlobalScope.INSTANCE.launch(
            kotlinx.coroutines.Dispatchers.getMain(), () -> {
                com.securecall.app.ghostnet.session.GhostNetSession
                    .get()
                    .getStateFlow()
                    .collect(state -> {
                        updateSessionStatus(state);
                        return kotlin.Unit.INSTANCE;
                    });
                return kotlin.Unit.INSTANCE;
            }
        );
    }

    // BACKEND-60: SessionState observer starten
    @Override
    protected void onStart() {
        super.onStart();
        observeSessionState();
    }

    // BACKEND-60: Debug — StateFlow Wert anzeigen
    private void setupPrintStateFlowButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("StateFlow Snapshot");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.session.GhostNetSessionState s =
                com.securecall.app.ghostnet.session.GhostNetSession.get().getStateFlow().getValue();
            android.util.Log.d("MAIN", "StateFlow current = " + s);
        });
        addDebugButton(b);
    }

    // BACKEND-61: Hinweis im Log, dass Frame-Router v2 aktiv ist
    private void logRouterV2ActiveOnce() {
        android.util.Log.d("MAIN", "GhostControlRouter v2 (routeIncoming) is active as central frame router");
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logRouterV2ActiveOnce(); // BACKEND-61: Marker im Log
    }

    // BACKEND-62: Debug — sende manuell einen reinen PING-Frame
    private void setupSendPingButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send PING");
        b.setOnClickListener(v -> {
            byte[] f = com.securecall.app.ghostnet.control.ControlFrameBuilder.ping();
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendControlFrame(f);
        });
        addDebugButton(b);
    }

    // BACKEND-63: Debug — Test-MediaFrame erzeugen
    private void setupSendTestMediaFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send Test MediaFrame");
        b.setOnClickListener(v -> {
            byte[] data = new byte[128];
            new java.util.Random().nextBytes(data);
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestFrame(data);
        });
        addDebugButton(b);
    }

    // BACKEND-64: Debug — simulierter verschlüsselter MediaFrame
    private void setupSendEncryptedMediaFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Encrypted MediaFrame");
        b.setOnClickListener(v -> {
            byte[] fakeEnc = new byte[64];
            new java.util.Random().nextBytes(fakeEnc);
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestFrame(fakeEnc);
        });
        addDebugButton(b);
    }

    // BACKEND-65: Dummy-Audio über Transport senden
    private void setupSendAudioTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Audio Test");
        b.setOnClickListener(v -> {
            byte[] audio = new byte[160];
            new java.util.Random().nextBytes(audio);
            com.securecall.app.ghostnet.transport.GhostTransport.get().enqueueTestFrame(audio);
        });
        addDebugButton(b);
    }

    // BACKEND-66: Debug — AudioTrack stoppen/freigeben
    private void setupStopAudioButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Stop Audio");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.playback.AudioPlayer.stopAndRelease();
        });
        addDebugButton(b);
    }

    // CRYPTO-03: JNI Self-Test
    private void setupCryptoSelfTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Crypto SelfTest");
        b.setOnClickListener(v -> {
            boolean ok = com.securecall.crypto.CoreCrypto.selfTest();
            android.util.Log.d("MAIN", "JNI Crypto SelfTest: " + ok);
        });
        addDebugButton(b);
    }

    // BACKEND-65: Debug — Session Key ableiten
    private void setupTestSessionKeyButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test Derive Key");

        b.setOnClickListener(v -> {
            byte[] localPriv = new byte[32];
            byte[] remotePub = new byte[32];
            new java.util.Random().nextBytes(localPriv);
            new java.util.Random().nextBytes(remotePub);

            com.securecall.app.ghostnet.session.SessionKeyController.INSTANCE
                .deriveSessionKey(localPriv, remotePub);

            android.util.Log.d("MAIN", "SessionKey? " +
                com.securecall.app.ghostnet.session.SessionKeyController.INSTANCE.hasSessionKey());
        });

        addDebugButton(b);
    }

    // BACKEND-66: Debug — Ephemeral Keypair erzeugen und anzeigen
    private void setupEphemeralKeyDebugButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Generate Ephemeral Keys");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.INSTANCE.generateEphemeralKeypair();
            byte[] pub = com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.INSTANCE.getEphemeralPub();
            byte[] priv = com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.INSTANCE.getEphemeralPriv();

            android.util.Log.d("MAIN", "Ephemeral PUB=" + java.util.Arrays.toString(pub));
            android.util.Log.d("MAIN", "Ephemeral PRIV=" + java.util.Arrays.toString(priv));
        });

        addDebugButton(b);
    }

    // PATCH 199: Debug — Outgoing Handshake testen
    private void setupTestOutgoingHandshakeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test Outgoing HS");

        b.setOnClickListener(v -> {
            byte[] remotePub = new byte[32];
            new java.util.Random().nextBytes(remotePub);

            com.securecall.app.ghostnet.handshake.HandshakeController.INSTANCE
                .startOutgoing(remotePub);

            android.util.Log.d(
                "MAIN",
                "HandshakeState (outgoing) = " +
                    com.securecall.app.ghostnet.handshake.HandshakeController.INSTANCE.getState()
            );
        });

        addDebugButton(b);
    }

    // PATCH 199: Debug — Incoming Handshake testen
    private void setupTestIncomingHandshakeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test Incoming HS");

        b.setOnClickListener(v -> {
            byte[] remotePub = new byte[32];
            new java.util.Random().nextBytes(remotePub);

            com.securecall.app.ghostnet.handshake.HandshakeController.INSTANCE
                .acceptIncoming(remotePub);

            android.util.Log.d(
                "MAIN",
                "HandshakeState (incoming) = " +
                    com.securecall.app.ghostnet.handshake.HandshakeController.INSTANCE.getState()
            );
        });

        addDebugButton(b);
    }

    // PATCH 200: Debug — Outgoing Handshake über GhostNetSession
    private void setupDebugOutgoingSessionHandshakeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Session: Outgoing HS");

        b.setOnClickListener(v -> {
            byte[] rp = new byte[32];
            new java.util.Random().nextBytes(rp);

            com.securecall.app.ghostnet.session.GhostNetSession.get()
                .startOutgoingHandshake(rp);

            com.securecall.app.ghostnet.session.GhostNetSessionState state =
                com.securecall.app.ghostnet.session.GhostNetSession.get().getState();

            android.util.Log.d("MAIN", "SessionState after outgoing HS = " + state);
        });

        addDebugButton(b);
    }

    // PATCH 200: Debug — Incoming Handshake über GhostNetSession
    private void setupDebugIncomingSessionHandshakeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Session: Incoming HS");

        b.setOnClickListener(v -> {
            byte[] rp = new byte[32];
            new java.util.Random().nextBytes(rp);

            com.securecall.app.ghostnet.session.GhostNetSession.get()
                .acceptIncomingHandshake(rp);

            com.securecall.app.ghostnet.session.GhostNetSessionState state =
                com.securecall.app.ghostnet.session.GhostNetSession.get().getState();

            android.util.Log.d("MAIN", "SessionState after incoming HS = " + state);
        });

        addDebugButton(b);
    }

    // PATCH 201: Debug — KeyOffer senden
    private void setupSendKeyOfferButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send KeyOffer");

        b.setOnClickListener(v -> {
            byte[] pub = com.securecall.app.ghostnet.keys.GhostNetKeyMaterial.INSTANCE.getLocalPub();
            String msg = new com.securecall.app.net.signal.KeyOffer(pub).toJson();
            if (wsService != null) wsService.sendMessage(msg);
            android.util.Log.d("MAIN", "KeyOffer sent");
        });

        addDebugButton(b);
    }

    // PATCH 202: Letzte Call-ID merken (nur Debug)
    private String lastDebugCallId = null;

    // PATCH 202: Debug — CALL_INIT senden
    private void setupSendCallInitButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send CALL_INIT");

        b.setOnClickListener(v -> {
            com.securecall.app.net.signal.CallInit ci =
                new com.securecall.app.net.signal.CallInit();
            String json = ci.toJson();
            lastDebugCallId = ci.getCallId();

            if (wsService != null) {
                wsService.sendMessage(json);
                android.util.Log.d("MAIN", "CALL_INIT sent: " + json);
            } else {
                android.util.Log.w("MAIN", "wsService == null, cannot send CALL_INIT");
            }
        });

        addDebugButton(b);
    }

    // PATCH 202: Debug — CALL_BYE senden
    private void setupSendCallByeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send CALL_BYE");

        b.setOnClickListener(v -> {
            if (lastDebugCallId == null) {
                android.util.Log.w("MAIN", "No lastDebugCallId set, skipping CALL_BYE");
                return;
            }

            com.securecall.app.net.signal.CallBye cb =
                new com.securecall.app.net.signal.CallBye(lastDebugCallId);
            String json = cb.toJson();

            if (wsService != null) {
                wsService.sendMessage(json);
                android.util.Log.d("MAIN", "CALL_BYE sent: " + json);
            } else {
                android.util.Log.w("MAIN", "wsService == null, cannot send CALL_BYE");
            }
        });

        addDebugButton(b);
    }

    // PATCH 203: Debug — Incoming Call simulieren
    private void setupSimulateIncomingCallButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Sim Incoming Call");

        b.setOnClickListener(v -> {
            String callId = java.util.UUID.randomUUID().toString();
            com.securecall.app.call.CallController.INSTANCE.incomingCall(callId);
            android.util.Log.d("MAIN", "Incoming call: " + callId);
        });

        addDebugButton(b);
    }

    // PATCH 203: Debug — Outgoing Call simulieren
    private void setupSimulateOutgoingCallButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Sim Outgoing Call");

        b.setOnClickListener(v -> {
            String callId = java.util.UUID.randomUUID().toString();
            com.securecall.app.call.CallController.INSTANCE.outgoingCall(callId);
            android.util.Log.d("MAIN", "Outgoing call: " + callId);
        });

        addDebugButton(b);
    }

    // PATCH 203: Debug — Call annehmen
    private void setupAcceptCallButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Accept Call");

        b.setOnClickListener(v -> {
            com.securecall.app.call.CallController.INSTANCE.acceptCall();
            android.util.Log.d(
                "MAIN",
                "Call accepted → state=" +
                com.securecall.app.call.CallController.INSTANCE.getState()
            );
        });

        addDebugButton(b);
    }

    // PATCH 203: Debug — Call beenden
    private void setupEndCallButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("End Call");

        b.setOnClickListener(v -> {
            com.securecall.app.call.CallController.INSTANCE.endCall();
            android.util.Log.d(
                "MAIN",
                "Call ended → state=" +
                com.securecall.app.call.CallController.INSTANCE.getState()
            );
        });

        addDebugButton(b);
    }

    // PATCH 206 — Debug: Transport-Status anzeigen
    private void setupCheckTransportStatusButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Transport Status");

        b.setOnClickListener(v -> {
            boolean running =
                com.securecall.app.ghostnet.transport.GhostTransport.get().isRunning();
            android.util.Log.d("MAIN", "Transport running = " + running);
        });

        addDebugButton(b);
    }

    // PATCH 207: Debug — Media-Pipeline START
    private void setupStartMediaPipelineButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Start MediaPipeline");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.GhostMediaPipeline.INSTANCE.start();
            android.util.Log.d(
                "MAIN",
                "MediaPipeline started, running=" +
                    com.securecall.app.ghostnet.media.GhostMediaPipeline.INSTANCE.isRunning()
            );
        });

        addDebugButton(b);
    }

    // PATCH 207: Debug — Media-Pipeline STOP
    private void setupStopMediaPipelineButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Stop MediaPipeline");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.GhostMediaPipeline.INSTANCE.stop();
            android.util.Log.d(
                "MAIN",
                "MediaPipeline stopped, running=" +
                    com.securecall.app.ghostnet.media.GhostMediaPipeline.INSTANCE.isRunning()
            );
        });

        addDebugButton(b);
    }

    // PATCH 208: Debug — MediaPipeline Status
    private void setupCheckMediaPipelineStatusButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("MediaPipeline Status");

        b.setOnClickListener(v -> {
            boolean running =
                com.securecall.app.ghostnet.media.GhostMediaPipeline.INSTANCE.isRunning();
            android.util.Log.d("MAIN", "MediaPipeline running = " + running);
        });

        addDebugButton(b);
    }

    // PATCH 209: Debug — Fake-Frame decodieren
    private void setupDecodeFakeFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Decode FakeFrame");

        b.setOnClickListener(v -> {
            byte[] payload = new byte[32];
            new java.util.Random().nextBytes(payload);

            com.securecall.app.ghostnet.media.MediaFrame mf =
                new com.securecall.app.ghostnet.media.MediaFrame(payload, System.currentTimeMillis());

            short[] pcm =
                com.securecall.app.ghostnet.media.audio.AudioDecoder.INSTANCE.decode(mf);

            android.util.Log.d("MAIN", "Decoded PCM samples: " + pcm.length);
        });

        addDebugButton(b);
    }

    // PATCH 210: Debug — Fake PCM Playback
    private void setupFakePlaybackButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Fake Playback");

        b.setOnClickListener(v -> {
            short[] pcm = new short[128];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short)(Math.sin(i * 0.1) * 2000);
            }

            com.securecall.app.ghostnet.media.audio.AudioPlayback.INSTANCE.play(pcm);
            android.util.Log.d("MAIN", "Fake PCM played (log only)");
        });

        addDebugButton(b);
    }

    // PATCH 211: Debug — gesamter Pipeline-Test
    private void setupPipelineTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Pipeline Test");
        b.setOnClickListener(v -> {
            byte[] raw = new byte[128];
            new java.util.Random().nextBytes(raw);

            com.securecall.app.ghostnet.media.MediaFrame frame =
                new com.securecall.app.ghostnet.media.MediaFrame(raw, System.currentTimeMillis());

            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE.route(frame);

            android.util.Log.d("MAIN", "Pipeline test executed.");
        });
        addDebugButton(b);
    }

    // PATCH 212: Debug — Fake Sinus über echten AudioTrack
    private void setupAudioTrackTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("AudioTrack Test");

        b.setOnClickListener(v -> {
            short[] pcm = new short[480]; // 10ms bei 48kHz
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short)(Math.sin(i * 0.1) * 3000);
            }
            com.securecall.app.ghostnet.media.audio.AudioPlayback.INSTANCE.play(pcm);

            android.util.Log.d("MAIN", "AudioTrack test PCM queued.");
        });

        addDebugButton(b);
    }

    // PATCH 214: Debug — Full Codec → Playback Pipeline
    private void setupCodecPipelineButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Codec Pipeline");

        b.setOnClickListener(v -> {
            byte[] fake = new byte[96];
            new java.util.Random().nextBytes(fake);

            com.securecall.app.ghostnet.media.decoder.AudioDecoder.INSTANCE.init();

            com.securecall.app.ghostnet.media.MediaFrame frame =
                new com.securecall.app.ghostnet.media.MediaFrame(fake, System.currentTimeMillis());

            // neue Pipeline
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE.handleWithCodec(frame);

            android.util.Log.d("MAIN", "Codec pipeline executed");
        });

        addDebugButton(b);
    }

    // PATCH 224: Debug — decoder reset
    private void setupResetDecoderButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Reset Decoder");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.GhostMediaRouter.get().resetDecoderStub();
        });
        addDebugButton(b);
    }

    // PATCH 225: Debug — force decode pipeline
    private void setupDecodePipelineButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Decode Pipeline Test");
        b.setOnClickListener(v -> {
            byte[] enc = new byte[32];
            new java.util.Random().nextBytes(enc);
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .enqueueTestFrame(enc);
        });
        addDebugButton(b);
    }

    // PATCH 226: Debug — generate a beep tone
    private void setupBeepButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Beep");
        b.setOnClickListener(v -> {
            int sr = 48000;
            short[] beep = new short[480];
            for (int i = 0; i < beep.length; i++) {
                beep[i] = (short)(Math.sin(2 * Math.PI * 440 * i / sr) * 3000);
            }
            com.securecall.app.ghostnet.media.GhostMediaRouter.get()
                .testBeep(beep);
        });
        addDebugButton(b);
    }

    // PATCH 227: full decode → playback test
    private void setupFullDecodePlaybackButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Full Decode → Play");

        b.setOnClickListener(v -> {
            byte[] fakeEnc = new byte[64];
            new java.util.Random().nextBytes(fakeEnc);

            // Transport nimmt enc → Router → Decoder → Player
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .enqueueTestFrame(fakeEnc);
        });

        addDebugButton(b);
    }

    // PATCH 228: Debug — print transport load
    private void setupPrintTransportLoadButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Transport Load");

        b.setOnClickListener(v -> {
            int q = com.securecall.app.ghostnet.transport.GhostTransport.get().queueSize();
            android.util.Log.d("MAIN", "TransportQueue size = " + q);
        });

        addDebugButton(b);
    }

    // PATCH 229: Debug — Call-Statemachine steuern
    private void setupCallStateDebugButtons() {
        // Start Outgoing
        android.widget.Button bStart = new android.widget.Button(this);
        bStart.setText("Call: Start");
        bStart.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallController.startOutgoingCall();
        });
        addDebugButton(bStart);

        // Mark Active
        android.widget.Button bActive = new android.widget.Button(this);
        bActive.setText("Call: Active");
        bActive.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallController.markCallActive();
        });
        addDebugButton(bActive);

        // Terminate
        android.widget.Button bTerm = new android.widget.Button(this);
        bTerm.setText("Call: Terminate");
        bTerm.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallController.terminateCall();
        });
        addDebugButton(bTerm);

        // Ended
        android.widget.Button bEnd = new android.widget.Button(this);
        bEnd.setText("Call: Ended");
        bEnd.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallController.markCallEnded();
        });
        addDebugButton(bEnd);

        // Hard Reset
        android.widget.Button bReset = new android.widget.Button(this);
        bReset.setText("Call: Reset");
        bReset.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallController.hardReset();
        });
        addDebugButton(bReset);
    }

    // PATCH 229: Debug — aktuellen CallState loggen
    private void setupPrintCallStateButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Call: Print State");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallState s =
                com.securecall.app.ghostnet.call.GhostCallController.getState();
            android.util.Log.d("MAIN", "GhostCallState = " + s);
        });
        addDebugButton(b);
    }

    // PATCH 230: Debug — print call state
    private void setupPrintCallMachineButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Print Call Machine");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallState s =
                com.securecall.app.ghostnet.call.GhostCallController.getState();
            android.util.Log.d("MAIN", "Call Machine State = " + s);
        });
        addDebugButton(b);
    }

    // PATCH 231 — Debug button for quiet shutdown
    private void setupQuietShutdownButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Quiet Shutdown");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.call.GhostCallController.performQuietShutdown();
        });
        addDebugButton(b);
    }

    // PATCH 233: Debug — print GhostNet session state
    private void setupPrintSessionStateButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Print Session State");
        b.setOnClickListener(v -> {
            String id = com.securecall.app.ghostnet.session.GhostNetSessionManager.get().getState().name();
            android.util.Log.d("MAIN", "GhostNetSessionState = " + id);
        });
        addDebugButton(b);
    }

    // PATCH 235: Update GhostNetSessionState in UI
    private void updateSessionNetState() {
        com.securecall.app.ghostnet.session.GhostNetSessionState st =
            com.securecall.app.ghostnet.session.GhostNetSessionManager.get().getState();

        android.widget.TextView tv = findViewById(R.id.sessionNetState);
        if (tv == null) return;

        tv.setText("NETSESSION: " + st.name());

        int color;
        switch (st) {
            case NEGOTIATING: color = android.graphics.Color.parseColor("#FFA500"); break;
            case ACTIVE:       color = android.graphics.Color.parseColor("#00AA00"); break;
            case TERMINATING:  color = android.graphics.Color.parseColor("#CC0000"); break;
            case DEAD:         color = android.graphics.Color.parseColor("#990000"); break;
            default:           color = android.graphics.Color.parseColor("#666666");
        }
        tv.setTextColor(color);
    }

    // PATCH 235: hook UI session update into onResume
    @Override
    protected void onResume() {
        super.onResume();
        updateSessionNetState();
    }

    // PATCH 235: Debug — manual refresh
    private void setupRefreshSessionNetButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Refresh NetSession");
        b.setOnClickListener(v -> updateSessionNetState());
        addDebugButton(b);
    }

    // PATCH 236: install session + call state observers
    private void installStateObservers() {
        // CallState
        com.securecall.app.ghostnet.call.GhostCallController.addListener(
            newState -> runOnUiThread(() -> updateSessionNetState())
        );

        // SessionState
        com.securecall.app.ghostnet.session.GhostNetSessionManager.addListener(
            newState -> runOnUiThread(() -> updateSessionNetState())
        );
    }

    // PATCH 236: integrate observers at startup
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installStateObservers();
    }

// PATCH 237: Debug — Full Reset
private void setupFullResetButton() {
    android.widget.Button b = new android.widget.Button(this);
    b.setText("FULL RESET");
    b.setOnClickListener(v -> {
        com.securecall.app.ghostnet.call.GhostCallController.fullReset();
        android.util.Log.d("MAIN", "Full Reset invoked");
    });
    addDebugButton(b);
}

// PATCH 237: hook reset button into UI debug panel
@Override
protected void onCreate(android.os.Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setupFullResetButton();
}

    // PATCH 238: Debug-Button – softer Transportfehler
    private void setupSoftTransportErrorButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Soft Transport Error");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.debug.TransportErrorInjector.triggerSoftTransportError();
        });
        addDebugButton(b);
    }

    // PATCH 238: Debug-Button – harter Transportfehler (Full Reset)
    private void setupHardTransportErrorButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Hard Transport Error");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.debug.TransportErrorInjector.triggerHardTransportError();
        });
        addDebugButton(b);
    }

    // PATCH 238: Debug-Button – Session Drop
    private void setupSessionDropButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Session DROP");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.debug.TransportErrorInjector.triggerSessionDrop();
        });
        addDebugButton(b);
    }

    // PATCH 238: Debug-Button – Packet-Loss-Burst (nur Logging vorerst)
    private void setupPacketLossBurstButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("PacketLoss Burst");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.debug.TransportErrorInjector.simulatePacketLossBurst();
        });
        addDebugButton(b);
    }

    // PATCH 238: zusätzliche Debug-Buttons registrieren
    private void setupTransportErrorDebugButtons() {
        setupSoftTransportErrorButton();
        setupHardTransportErrorButton();
        setupSessionDropButton();
        setupPacketLossBurstButton();
    }

    // PATCH 238: Hook in bestehende Debug-Initialisierung
    private void initAllDebugTools() {
        // vorhandene Debug-Setup-Aufrufe bleiben gültig
        setupFullResetButton();
        setupTransportErrorDebugButtons();
    }

    // PATCH 238: Debug-Initialisierung an onCreate anhängen
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initAllDebugTools();
    }

    // PATCH 239: Debug – Dummy Recorder Start
    private void setupStartDummyRecorderButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Dummy Rec START");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.DummyAudioRecorder.start();
        });
        addDebugButton(b);
    }

    // PATCH 239: Debug – Dummy Recorder Stop
    private void setupStopDummyRecorderButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Dummy Rec STOP");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.DummyAudioRecorder.stop();
        });
        addDebugButton(b);
    }

    // PATCH 239: Hook in existing debug init
    private void initDummyAudioDebugTools() {
        setupStartDummyRecorderButton();
        setupStopDummyRecorderButton();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDummyAudioDebugTools();
    }

    // PATCH 240: Debug-Event-Listener-Feld
    private final com.securecall.app.debug.GhostDebugEventBus.Listener debugEventListener =
        event -> runOnUiThread(() -> appendDebugLogLine(event));

    // PATCH 240: Zeile ins Log-View anhängen
    private void appendDebugLogLine(com.securecall.app.debug.GhostDebugEventBus.Event event) {
        android.widget.TextView tv = findViewById(R.id.debugLogView);
        android.widget.ScrollView scroll = findViewById(R.id.debugLogScroll);
        if (tv == null || scroll == null) return;

        String existing = tv.getText() != null ? tv.getText().toString() : "";
        String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(new java.util.Date(event.getTimestamp()));
        String line = "[" + ts + "][" + event.getTag() + "] " + event.getMessage();

        if (!existing.isEmpty()) {
            existing = existing + "\n" + line;
        } else {
            existing = line;
        }

        tv.setText(existing);

        // automatisch nach unten scrollen
        scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN));
    }

    // PATCH 240: Listener in Lifecycle einklinken
    @Override
    protected void onStart() {
        super.onStart();
        com.securecall.app.debug.GhostDebugEventBus.addListener(debugEventListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.securecall.app.debug.GhostDebugEventBus.removeListener(debugEventListener);
    }

    // PATCH 241: global GhostNet init
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.securecall.app.ghostnet.GhostNetSystem.init();
    }

    // PATCH 243: Debug – derive ephemeral SessionKeys und ins Debug-Event-Log schreiben
    private void setupDeriveSessionKeysButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Derive SessionKeys");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.SessionKeys keys =
                com.securecall.app.ghostnet.crypto.SessionKeyDerivation.INSTANCE.deriveEphemeral();

            com.securecall.app.debug.GhostDebugEventBus.postSessionKeysPreview(
                "KEYS",
                keys.getRxKey(),
                keys.getTxKey(),
                keys.getSalt()
            );
        });
        addDebugButton(b);
    }

    // PATCH 244: DeriveSessionKeys in Debug-Pipeline einhängen
    private void initDebugKeyTools() {
        setupDeriveSessionKeysButton();
    }

    // PATCH 244: integrate key-tools into global debug init
    private void initAllDebugTools() {
        initDebugKeyTools();
    }

    // PATCH 245: Debug – Mock Handshake
    private void setupMockHandshakeButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Mock Handshake");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.handshake.HandshakeResult res =
                com.securecall.app.ghostnet.handshake.HandshakeEngine.performMockHandshake();

            com.securecall.app.debug.GhostDebugEventBus.post(
                "HSK",
                "Handshake simulated: shared=" + res.getSharedSecret().length
                    + " localPub=" + res.getLocalEphemeralPub().length
                    + " remotePub=" + res.getRemoteEphemeralPub().length
            );
        });
        addDebugButton(b);
    }

    // PATCH 245: hook mock handshake into debug tools
    private void initHandshakeDebugTools() {
        setupMockHandshakeButton();
    }

    // PATCH 245: extend global debug init
    private void initAllDebugTools() {
        initHandshakeDebugTools();
    }

    // PATCH 248: Debug – create SessionCryptoContext via mock handshake
    private void setupCreateCryptoContextButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Create CryptoContext");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.SessionCryptoContext ctx =
                com.securecall.app.ghostnet.crypto.SessionCryptoContext.fromMockHandshake();

            // Debug-Ausgabe ins EventBus
            com.securecall.app.debug.GhostDebugEventBus.post(
                "CRYPTO_CTX",
                "created context " + ctx.debugSummary()
            );
        });
        addDebugButton(b);
    }

    // PATCH 248: hook crypto-context tool
    private void initCryptoContextDebugTools() {
        setupCreateCryptoContextButton();
    }

    // PATCH 248: extend global debug init
    private void initAllDebugTools() {
        initCryptoContextDebugTools();
    }

    // PATCH 249: Debug – CryptoManager Info
    private void setupCryptoManagerInfoButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("CryptoMgr Info");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.SessionCryptoContext ctx =
                com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.getContext();

            com.securecall.app.debug.GhostDebugEventBus.post(
                "CRYPTO_MGR",
                "context summary: " + ctx.debugSummary()
            );
        });
        addDebugButton(b);
    }

    // PATCH 249: hook crypto-manager debug tools
    private void initCryptoManagerDebugTools() {
        setupCryptoManagerInfoButton();
    }

    // PATCH 249: extend global debug init
    private void initAllDebugTools() {
        initCryptoManagerDebugTools();
    }

    // PATCH 252: Debug – CryptoContext aus dem Transport abrufen
    private void setupTransportCryptoQueryButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Crypto? Transport");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.SessionCryptoContext ctx =
                com.securecall.app.ghostnet.transport.GhostTransport.get().getCryptoContext();

            if (ctx == null) {
                com.securecall.app.debug.GhostDebugEventBus.post(
                    "CRYPTO_T", "NO CONTEXT"
                );
            } else {
                com.securecall.app.debug.GhostDebugEventBus.post(
                    "CRYPTO_T",
                    "Transport CryptoContext: " + ctx.debugSummary()
                );
            }
        });
        addDebugButton(b);
    }

    // PATCH 252: Debug – CryptoContext aus dem MediaRouter abrufen
    private void setupMediaCryptoQueryButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Crypto? Media");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.SessionCryptoContext ctx =
                com.securecall.app.ghostnet.media.GhostMediaRouter.getCryptoContext();

            if (ctx == null) {
                com.securecall.app.debug.GhostDebugEventBus.post(
                    "CRYPTO_M", "NO CONTEXT"
                );
            } else {
                com.securecall.app.debug.GhostDebugEventBus.post(
                    "CRYPTO_M",
                    "Media CryptoContext: " + ctx.debugSummary()
                );
            }
        });
        addDebugButton(b);
    }

    // PATCH 252: combine transport+media crypto query tools
    private void initCryptoQueryDebugTools() {
        setupTransportCryptoQueryButton();
        setupMediaCryptoQueryButton();
    }

    // PATCH 252: extend global debug init
    private void initAllDebugTools() {
        initCryptoQueryDebugTools();
    }

    // PATCH 253: Debug – CryptoContext Clear
    private void setupClearCryptoContextButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Clear CryptoContext");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.clearContext();
            com.securecall.app.debug.GhostDebugEventBus.post("CRYPTO", "Manual Clear → CryptoContext cleared");
        });
        addDebugButton(b);
    }

    // PATCH 253: hook clear-crypto tool
    private void initCryptoResetDebugTools() {
        setupClearCryptoContextButton();
    }

    // PATCH 253: extend global debug init
    private void initAllDebugTools() {
        initCryptoResetDebugTools();
    }

    // PATCH 254: Debug – Outbound Encrypt-Test
    private void setupEncryptDummyFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Encrypt Dummy Frame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get().debugEncryptDummyFrame();
        });
        addDebugButton(b);
    }

    // PATCH 254: include encrypt-dummy tool
    private void initCryptoOutboundDebugTools() {
        setupEncryptDummyFrameButton();
    }

    // PATCH 254: extend global debug init
    private void initAllDebugTools() {
        initCryptoOutboundDebugTools();
    }

    // CRYPTO-04: Debug-Tools für ECDH-Fake
    private void setupECDHDebugButtons() {

        android.widget.Button gen = new android.widget.Button(this);
        gen.setText("ECDH: Generate Local Key");
        gen.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.generateLocalECDHKeyPair();
        });
        addDebugButton(gen);

        android.widget.Button setRemote = new android.widget.Button(this);
        setRemote.setText("ECDH: Set Fake Remote Key");
        setRemote.setOnClickListener(v -> {
            byte[] fake = new byte[32];
            new java.util.Random().nextBytes(fake);
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.setRemotePublicKey(fake);
        });
        addDebugButton(setRemote);

        android.widget.Button derive = new android.widget.Button(this);
        derive.setText("ECDH: Derive Secret");
        derive.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager.deriveFakeSharedSecret();
        });
        addDebugButton(derive);
    }

    // CRYPTO-04: ECDH debug
    private void initECDHDebugTools() {
        setupECDHDebugButtons();
    }

    // CRYPTO-04: extend global init
    private void initAllDebugTools() {
        initECDHDebugTools();
    }

    // CRYPTO-05: Debug — HKDF aus sharedSecret ableiten
    private void setupHkdfDebugButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("HKDF: Derive Keys");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.crypto.GhostNetCryptoManager
                    .deriveSymmetricKeysFromSharedSecret();
        });
        addDebugButton(b);
    }

    // CRYPTO-06: Debug – decrypt dummy with recvKey
    private void setupDecryptWithKeyButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Decrypt Dummy (recvKey)");
        b.setOnClickListener(v -> {
            byte[] fake = new byte[32];
            new java.util.Random().nextBytes(fake);

            com.securecall.app.ghostnet.media.MediaFrame frame =
                new com.securecall.app.ghostnet.media.MediaFrame(fake, System.currentTimeMillis());

            com.securecall.app.ghostnet.media.GhostMediaRouter router =
                com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE;

            router.debugDecryptWithKey(frame);
        });
        addDebugButton(b);
    }

    // CRYPTO-07: Debug – Test Frame Header Build/Parse
    private void setupTestFrameHeaderButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test Frame Header");
        b.setOnClickListener(v -> {
            byte[] dummy = new byte[16];
            new java.util.Random().nextBytes(dummy);

            // Build (transport)
            byte[] raw = com.securecall.app.ghostnet.transport.GhostTransport
                    .get()
                    .debugBuildHeader(dummy);

            // Parse (media)
            com.securecall.app.ghostnet.media.GhostMediaRouter
                .INSTANCE
                .debugParseInbound(raw);
        });

        addDebugButton(b);
    }

    // CRYPTO-07
    private void initFrameHeaderDebugTools() {
        setupTestFrameHeaderButton();
    }

    // CRYPTO-07 extend global init
    private void initAllDebugTools() {
        initFrameHeaderDebugTools();
    }

    // CRYPTO-08: Debug – Nonce-Manager + Header
    private void setupTestNonceManagerButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test Nonce Manager");
        b.setOnClickListener(v -> {
            byte[] dummy = new byte[8];
            new java.util.Random().nextBytes(dummy);

            // Header mit NonceManager bauen
            byte[] raw = com.securecall.app.ghostnet.transport.GhostTransport
                    .get()
                    .debugBuildHeaderNonceManaged(dummy);

            // Header wieder parsen
            com.securecall.app.ghostnet.media.GhostMediaRouter
                .INSTANCE
                .debugParseInbound(raw);
        });
        addDebugButton(b);
    }

    // CRYPTO-08: Erweiterung Debug-Init
    private void initNonceDebugTools() {
        setupTestNonceManagerButton();
    }

    // CRYPTO-08: Hook in globale Debug-Initialisierung (falls vorhanden)
    private void initAllDebugTools() {
        initFrameHeaderDebugTools();
        initNonceDebugTools();
    }

    // CRYPTO-09: Debug – spam test for nonce guard
    private void setupTestNonceSpamButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Nonce Spam Test");
        b.setOnClickListener(v -> {
            for (int i = 0; i < 50; i++) {
                byte[] d = new byte[4];
                new java.util.Random().nextBytes(d);

                com.securecall.app.ghostnet.transport.GhostTransport
                        .get()
                        .buildHeaderForOutboundNonceManaged_debugWrap(d);
            }
        });
        addDebugButton(b);
    }

    private void initNonceSpamDebugTools() {
        setupTestNonceSpamButton();
    }

    private void initAllDebugTools() {
        initNonceSpamDebugTools();
    }

    // CRYPTO-10: Debug – CiphertextFrame Pipeline testen
    private void setupTestCiphertextFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test CiphertextFrame");
        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.crypto.CiphertextFrame cf =
                com.securecall.app.ghostnet.transport.GhostTransport
                    .get()
                    .debugBuildCiphertextFrameDummy();

            com.securecall.app.ghostnet.media.GhostMediaRouter
                .INSTANCE
                .debugInspectCiphertextFrame(cf);
        });
        addDebugButton(b);
    }

    // CRYPTO-10: Crypto-Debug-Init
    private void initCryptoFrameDebugTools() {
        setupTestCiphertextFrameButton();
    }

    // CRYPTO-10: Kombination in bestehende Debug-Init integrieren
    private void initAllCryptoDebugTools() {
        initCryptoFrameDebugTools();
    }

    // CRYPTO-11: Debug – WireFormat roundtrip
    private void setupTestWireFormatButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test WireFormat");
        b.setOnClickListener(v -> {
            // build
            byte[] raw = com.securecall.app.ghostnet.transport.GhostTransport
                    .get()
                    .debugBuildWireFrameDummy();

            // parse
            com.securecall.app.ghostnet.media.GhostMediaRouter
                .INSTANCE
                .debugParseWireFrame(raw);
        });

        addDebugButton(b);
    }

    private void initWireFormatDebugTools() {
        setupTestWireFormatButton();
    }

    private void initAllDebugTools() {
        initWireFormatDebugTools();
    }

    // CRYPTO-12: Debug – WireFrame + Validator testen
    private void setupValidateWireFormatButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Validate WireFrame");
        b.setOnClickListener(v -> {
            // Dummy-WireFrame aus dem Transport
            byte[] raw = com.securecall.app.ghostnet.transport.GhostTransport
                    .get()
                    .debugBuildWireFrameDummy();

            // Validierung im MediaRouter
            com.securecall.app.ghostnet.media.GhostMediaRouter
                    .INSTANCE
                    .debugValidateWireFrame(raw);
        });
        addDebugButton(b);
    }

    private void initWireValidationDebugTools() {
        setupValidateWireFormatButton();
    }

    // CRYPTO-13: Debug – Replay Detection Test
    private void setupReplayDetectionTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Replay Test");
        b.setOnClickListener(v -> {

            java.util.ArrayList<byte[]> list =
                com.securecall.app.ghostnet.transport.GhostTransport
                    .get()
                    .debugGenerateWireFrameListForReplayTest();

            for (byte[] raw : list) {
                com.securecall.app.ghostnet.media.GhostMediaRouter
                    .INSTANCE
                    .debugParseWireFrame_withReplay(raw);
            }
        });
        addDebugButton(b);
    }

    private void initReplayDebugTools() {
        setupReplayDetectionTestButton();
    }

    // CRYPTO-14: Security Mode Buttons
    private void setupSecurityModeButtons() {

        addDebugButton(makeModeButton("SEC: OFF", () ->
            com.securecall.app.ghostnet.security.SecurityStateMachine.setMode(
                com.securecall.app.ghostnet.security.SecurityMode.OFF)));

        addDebugButton(makeModeButton("SEC: LOG_ONLY", () ->
            com.securecall.app.ghostnet.security.SecurityStateMachine.setMode(
                com.securecall.app.ghostnet.security.SecurityMode.LOG_ONLY)));

        addDebugButton(makeModeButton("SEC: STRICT", () ->
            com.securecall.app.ghostnet.security.SecurityStateMachine.setMode(
                com.securecall.app.ghostnet.security.SecurityMode.STRICT)));

        addDebugButton(makeModeButton("SEC: MANDATORY", () ->
            com.securecall.app.ghostnet.security.SecurityStateMachine.setMode(
                com.securecall.app.ghostnet.security.SecurityMode.MANDATORY)));

        addDebugButton(makeModeButton("SEC: LOCKDOWN", () ->
            com.securecall.app.ghostnet.security.SecurityStateMachine.setMode(
                com.securecall.app.ghostnet.security.SecurityMode.LOCKDOWN)));
    }

    private android.widget.Button makeModeButton(String text, Runnable action) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void initSecurityModeDebugTools() {
        setupSecurityModeButtons();
    }

    // CRYPTO-16 Debug: test replay detector
    private void setupReplayDetectorButtons() {

        // NONCE FORWARD (increasing)
        android.widget.Button bF = new android.widget.Button(this);
        bF.setText("Nonce Forward");
        bF.setOnClickListener(v -> {
            com.securecall.app.ghostnet.security.ReplayDetector.checkAndReport(100);
        });
        addDebugButton(bF);

        // NONCE BACKWARD (smaller nonce)
        android.widget.Button bB = new android.widget.Button(this);
        bB.setText("Nonce Backward");
        bB.setOnClickListener(v -> {
            com.securecall.app.ghostnet.security.ReplayDetector.checkAndReport(50);
        });
        addDebugButton(bB);

        // REPLAY (same nonce again)
        android.widget.Button bR = new android.widget.Button(this);
        bR.setText("Nonce Replay");
        bR.setOnClickListener(v -> {
            com.securecall.app.ghostnet.security.ReplayDetector.checkAndReport(100);
        });
        addDebugButton(bR);
    }

    // CRYPTO-16: call from debug-initializer
    private void initReplayDebugTools() {
        setupReplayDetectorButtons();
    }

    // CRYPTO-16: insert into onResume or your existing debug initializer
    private void initAllCryptoDebugTools() {
        initReplayDebugTools();
        // weitere initX() folgen später
    }

    // CRYPTO-17: Debug Button – random header test
    private void setupWireHeaderTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test WireHeader");
        b.setOnClickListener(v -> {
            byte[] raw = new byte[16];
            new java.util.Random().nextBytes(raw);
            com.securecall.app.ghostnet.media.GhostMediaRouter router =
                com.securecall.app.ghostnet.media.GhostMediaRouter.getInstance();

            router.debugParseWireHeader(raw);
        });
        addDebugButton(b);
    }

    private void initWireHeaderDebugTools() {
        setupWireHeaderTestButton();
    }

    private void initAllHeaderDebugTools() {
        initWireHeaderDebugTools();
    }

    // CRYPTO-18: Debug Button – Entire inbound pipeline test
    private void setupPipelineTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Pipeline Test");

        b.setOnClickListener(v -> {
            byte[] raw = new byte[32];
            new java.util.Random().nextBytes(raw);

            com.securecall.app.ghostnet.media.GhostMediaRouter
                    .INSTANCE
                    .processInboundRaw(raw);
        });

        addDebugButton(b);
    }

    private void initMediaPipelineDebugTools() {
        setupPipelineTestButton();
    }

    // CRYPTO-19: Debug Button – Wire Roundtrip Test
    private void setupWireRoundtripButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Wire Roundtrip");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.media.GhostMediaRouter.INSTANCE
                    .debugEncodeRoundtrip(32);
        });

        addDebugButton(b);
    }

    private void initWireRoundtripDebugTools() {
        setupWireRoundtripButton();
    }

    private void initAllCryptoDebugTools() {
        initWireHeaderDebugTools();
        initMediaPipelineDebugTools();
        initWireRoundtripDebugTools();
    }

    // CRYPTO-20: Debug Button – Send Outbound WireFrame
    private void setupSendTestWireFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send WireFrame");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .sendTestWireFrame(32);
        });

        addDebugButton(b);
    }

    private void initOutboundWireDebugTools() {
        setupSendTestWireFrameButton();
    }

    private void initAllCryptoDebugTools() {
        initWireHeaderDebugTools();
        initMediaPipelineDebugTools();
        initWireRoundtripDebugTools();
        initOutboundWireDebugTools();
    }

    // CRYPTO-22: Debug – Send AudioFrame
    private void setupSendAudioFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send AudioFrame");

        b.setOnClickListener(v -> {
            byte[] dummy = new byte[64];
            new java.util.Random().nextBytes(dummy);
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .sendAudioFrame(dummy);
        });

        addDebugButton(b);
    }

    // CRYPTO-22: Debug – Send ControlFrame
    private void setupSendControlFrameButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send ControlFrame");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .sendControlFrame(200, "call-end");
        });

        addDebugButton(b);
    }

    // CRYPTO-22: Debug – Send KeepAlive
    private void setupSendKeepAliveButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send KeepAlive");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .sendKeepAlive();
        });

        addDebugButton(b);
    }

    private void initFrameTypeDebugTools() {
        setupSendAudioFrameButton();
        setupSendControlFrameButton();
        setupSendKeepAliveButton();
    }

    private void initAllCryptoDebugTools() {
        initWireHeaderDebugTools();
        initMediaPipelineDebugTools();
        initWireRoundtripDebugTools();
        initOutboundWireDebugTools();
        initFrameTypeDebugTools();   // << NEW
    }

    // CRYPTO-23: Debug – AudioFrame über WireCryptoStub schicken
    private void setupSendAudioFrameCryptoStubButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("AudioFrame (CryptoStub)");

        b.setOnClickListener(v -> {
            byte[] dummy = new byte[64];
            new java.util.Random().nextBytes(dummy);
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .sendAudioFrameWithCryptoStub(dummy);
        });

        addDebugButton(b);
    }

    // CRYPTO-23: Erweiterung der Crypto-Debug-Tools
    private void initCryptoStubDebugTools() {
        setupSendAudioFrameCryptoStubButton();
    }

    // CRYPTO-23: Hook – Crypto-Stub-Tools mit initialisieren
    private void initAllCryptoDebugToolsWithStub() {
        initAllCryptoDebugTools();
        initCryptoStubDebugTools();
    }

    // CRYPTO-26: Debug – ControlFrame-End (structured)
    private void setupSendControlFrameEndButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("ControlFrame END");

        b.setOnClickListener(v -> {
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .sendControlFrame(200, "call-end");
        });

        addDebugButton(b);
    }

    private void initStructuredControlDebugTools() {
        setupSendControlFrameEndButton();
    }

    private void initAllCryptoDebugToolsWithStructuredFrames() {
        initAllCryptoDebugToolsWithStub();
        initStructuredControlDebugTools();
    }

    // CRYPTO-27: Debug – einfacher SessionCipher-Test
    private void setupSessionCipherDebugButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test SessionCipher");

        b.setOnClickListener(v -> {
            byte[] sample = new byte[16];
            new java.util.Random().nextBytes(sample);

            com.securecall.app.ghostnet.crypto.SessionCipherContext ctx =
                new com.securecall.app.ghostnet.crypto.SessionCipherContext(
                    "debug-session",
                    1,
                    sample, // rxKey placeholder
                    sample  // txKey placeholder
                );

            byte[] enc = com.securecall.app.ghostnet.crypto.SessionCipherEngine.encrypt(ctx, sample);
            byte[] dec = com.securecall.app.ghostnet.crypto.SessionCipherEngine.decrypt(ctx, enc);

            android.util.Log.d("MAIN", "SessionCipherDebug: encSize=" + enc.length + " decSize=" + dec.length);
        });

        addDebugButton(b);
    }

    private void initSessionCipherDebugTools() {
        setupSessionCipherDebugButton();
    }

    // CRYPTO-28: SessionCipherBinding Test-Button
    private void setupTestSessionBindingButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Test Session Binding");

        b.setOnClickListener(v -> {
            byte[] test = new byte[32];
            new java.util.Random().nextBytes(test);

            com.securecall.app.ghostnet.crypto.SessionCipherContext ctx =
                new com.securecall.app.ghostnet.crypto.SessionCipherContext(
                    "session-test",
                    1,
                    test,
                    test
                );

            com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession = ctx;

            com.securecall.app.ghostnet.media.MediaFrame f =
                new com.securecall.app.ghostnet.media.MediaFrame(test, System.currentTimeMillis());

            byte[] out = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.decryptFrame(f);

            android.util.Log.d("MAIN", "bindingTest resultSize=" + out.length);
        });

        addDebugButton(b);
    }

    // CRYPTO-29: Debug — PCM -> Encrypt -> GhostTransport-Stub
    private void setupEncryptPcmAndSendButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("PCM -> Encrypt -> Transport");

        b.setOnClickListener(v -> {
            // Dummy-PCM-Daten (64 Bytes)
            byte[] pcm = new byte[64];
            new java.util.Random().nextBytes(pcm);

            // Aufruf der neuen Stub-Methode
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .enqueueEncryptedFrameFromPcm(pcm);
        });

        addDebugButton(b);
    }

    // CRYPTO-29: Aufruf des neuen Debug-Buttons
    private void setupCrypto29Buttons() {
        setupEncryptPcmAndSendButton();
    }

    // CRYPTO-29: globaler Hook für alle Crypto-29 Buttons
    private void initCrypto29DebugTools() {
        // ruft die zuvor definierte Methode auf
        setupCrypto29Buttons();
    }

    // CRYPTO-29: Hook am Ende von onCreate() → Buttons erscheinen immer
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto29DebugTools();
    }

    // CRYPTO-30: Debug — Dummy PCM senden
    private void setupSendPcmButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("Send Dummy PCM");

        b.setOnClickListener(v -> {
            byte[] pcm = new byte[80];
            new java.util.Random().nextBytes(pcm);
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendPcm(pcm);
        });

        addDebugButton(b);
    }

    private void initCrypto30DebugTools() {
        setupSendPcmButton();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto30DebugTools();
    }

    // CRYPTO-31: Debug — NetworkSender-Stub testen
    private void setupNetworkSenderDebugButtons() {
        // Start-Button
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Start NetSender");
            b.setOnClickListener(v ->
                com.securecall.app.ghostnet.transport.net.GhostNetworkSender.start()
            );
            addDebugButton(b);
        }

        // Dummy-Frame senden
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Send Dummy EncryptedFrame");
            b.setOnClickListener(v -> {
                byte[] data = new byte[48];
                new java.util.Random().nextBytes(data);
                com.securecall.app.ghostnet.transport.EncryptedFrame frame =
                    new com.securecall.app.ghostnet.transport.EncryptedFrame(data);
                com.securecall.app.ghostnet.transport.net.GhostNetworkSender.enqueue(frame);
            });
            addDebugButton(b);
        }
    }

    private void initCrypto31DebugTools() {
        setupNetworkSenderDebugButtons();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto31DebugTools();
    }

    // CRYPTO-32: FullPath-Test (PCM -> Encrypt -> TransportThread -> NetworkSender)
    private void setupFullPathTestButton() {
        android.widget.Button b = new android.widget.Button(this);
        b.setText("FULL PATH TEST");

        b.setOnClickListener(v -> {
            byte[] pcm = new byte[96];
            new java.util.Random().nextBytes(pcm);
            com.securecall.app.ghostnet.transport.GhostTransport.get().sendPcmWithNetwork(pcm);
        });

        addDebugButton(b);
    }

    private void initCrypto32DebugTools() {
        setupFullPathTestButton();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto32DebugTools();
    }

    // CRYPTO-33: Debug — Inbound-Pipeline testen
    private void setupInboundDebugButtons() {

        // Start inbound pipeline
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Start Inbound Pipeline");
            b.setOnClickListener(v ->
                com.securecall.app.ghostnet.transport.GhostTransport.get().startInboundPipeline()
            );
            addDebugButton(b);
        }

        // Dummy inbound
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Inject Dummy Inbound");
            b.setOnClickListener(v -> {
                byte[] dummy = new byte[40];
                new java.util.Random().nextBytes(dummy);
                com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver.injectIncomingDummy(dummy);
            });
            addDebugButton(b);
        }
    }

    private void initCrypto33InboundTools() {
        setupInboundDebugButtons();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto33InboundTools();
    }

    // CRYPTO-36: Debug — send Audio/Control/KeepAlive via FrameHeaderV1
    private void setupFrameHeaderV1DebugButtons() {

        // AUDIO
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Send AUDIO v1");
            b.setOnClickListener(v -> {
                byte[] audio = new byte[80];
                new java.util.Random().nextBytes(audio);
                com.securecall.app.ghostnet.transport.GhostTransport.get().sendAudioFrameV1(audio);
            });
            addDebugButton(b);
        }

        // CONTROL
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Send CONTROL v1");
            b.setOnClickListener(v ->
                com.securecall.app.ghostnet.transport.GhostTransport.get()
                    .sendControlFrameV1(200, "TEST")
            );
            addDebugButton(b);
        }

        // KEEPALIVE
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Send KEEPALIVE v1");
            b.setOnClickListener(v ->
                com.securecall.app.ghostnet.transport.GhostTransport.get().sendKeepAliveFrameV1()
            );
            addDebugButton(b);
        }
    }

    private void initCrypto36DebugTools() {
        setupFrameHeaderV1DebugButtons();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto36DebugTools();
    }

    // CRYPTO-37: Debug inbound for AUDIO / CONTROL / KEEPALIVE
    private void setupInboundFlagTestButtons() {

        // AUDIO inbound
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Inject Inbound AUDIO");
            b.setOnClickListener(v -> {
                byte[] p = new byte[60];
                new java.util.Random().nextBytes(p);

                com.securecall.app.ghostnet.transport.EncryptedFrame frame =
                    new com.securecall.app.ghostnet.transport.EncryptedFrame(
                        com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
                            .encryptAudioFrameV1(p)
                    );

                com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver
                    .injectIncomingDummy(frame.data);
            });
            addDebugButton(b);
        }

        // CONTROL inbound
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Inject Inbound CONTROL");
            b.setOnClickListener(v -> {
                byte[] p = "200:OK".getBytes();
                com.securecall.app.ghostnet.transport.EncryptedFrame frame =
                    new com.securecall.app.ghostnet.transport.EncryptedFrame(
                        com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
                            .encryptControlFrameV1(p)
                    );

                com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver
                    .injectIncomingDummy(frame.data);
            });
            addDebugButton(b);
        }

        // KEEPALIVE inbound
        {
            android.widget.Button b = new android.widget.Button(this);
            b.setText("Inject Inbound KEEPALIVE");
            b.setOnClickListener(v -> {
                byte[] p = new byte[] {0x00};
                com.securecall.app.ghostnet.transport.EncryptedFrame frame =
                    new com.securecall.app.ghostnet.transport.EncryptedFrame(
                        com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding
                            .encryptKeepAliveFrameV1(p)
                    );

                com.securecall.app.ghostnet.transport.net.GhostNetworkReceiver
                    .injectIncomingDummy(frame.data);
            });
            addDebugButton(b);
        }
    }

    private void initCrypto37InboundFlagsTests() {
        setupInboundFlagTestButtons();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCrypto37InboundFlagsTests();
    }

// CRYPTO-40: Debug – send CONTROL "CALL-INVITE"
private void setupSendCallInviteButton() {
    android.widget.Button b = new android.widget.Button(this);
    b.setText("Send CALL-INVITE");
    b.setOnClickListener(v -> {
        var ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession;
        if (ctx != null) {
            com.securecall.app.ghostnet.transport.GhostTransport.get()
                .getNetworkSender()
                .sendControlFrameV1(100, "CALL-INVITE");
        } else {
            android.util.Log.w("DEBUG", "No active session – cannot send");
        }
    });
    addDebugButton(b);
}
