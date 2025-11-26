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
