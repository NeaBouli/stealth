package com.securecall.app;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.securecall.app.ghostnet.GhostNetTransport;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private GhostNetTransport transport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        Log.d(TAG, "CallActivity created");

        transport = new GhostNetTransport();

        Log.d(TAG, "Starting GhostNetTransport...");
        transport.start();

        Log.d(TAG, "Local Session ID: " + transport.getLocalSessionId());
        Log.d(TAG, "Remote Peer (may be null): " + transport.getRemotePeer());

        boolean connected = transport.channelConnected();
        Log.d(TAG, "GhostNet Channel Connected: " + connected);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CallActivity destroyed → stopping transport");
        if (transport != null) {
            transport.stop();
        }
    }
}
