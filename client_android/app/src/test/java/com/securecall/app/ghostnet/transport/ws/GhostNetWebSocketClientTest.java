package com.securecall.app.ghostnet.transport.ws;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

public class GhostNetWebSocketClientTest {

    @Before
    public void resetSingleton() throws Exception {
        Field instance = GhostNetWebSocketClient.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void testInitialState_isDisconnected() {
        assertEquals(
                GhostNetWebSocketClient.ConnectionState.DISCONNECTED,
                GhostNetWebSocketClient.getInstance().getConnectionState()
        );
    }

    @Test
    public void testIsConnected_initiallyFalse() {
        assertFalse(GhostNetWebSocketClient.getInstance().isConnected());
    }

    @Test
    public void testConnect_nullUrl_doesNotCrash() {
        GhostNetWebSocketClient.getInstance().connect(null);
        assertEquals(
                GhostNetWebSocketClient.ConnectionState.DISCONNECTED,
                GhostNetWebSocketClient.getInstance().getConnectionState()
        );
    }

    @Test
    public void testConnect_emptyUrl_doesNotCrash() {
        GhostNetWebSocketClient.getInstance().connect("");
        assertEquals(
                GhostNetWebSocketClient.ConnectionState.DISCONNECTED,
                GhostNetWebSocketClient.getInstance().getConnectionState()
        );
    }

    @Test
    public void testSendBinary_whenNotConnected_doesNotCrash() {
        GhostNetWebSocketClient.getInstance().sendBinary(new byte[]{1, 2, 3});
        // No exception = pass
    }

    @Test
    public void testSendControlHello_noSocket_doesNotCrash() {
        GhostNetWebSocketClient.getInstance().sendControlHello();
        // No exception = pass
    }

    @Test
    public void testSendControlBye_disconnected_doesNotCrash() {
        GhostNetWebSocketClient.getInstance().sendControlBye();
        // No exception = pass
    }

    @Test
    public void testDisconnect_noSocket_doesNotCrash() {
        GhostNetWebSocketClient.getInstance().disconnect();
        // No exception = pass
    }

    @Test
    public void testSingletonConsistency() {
        assertSame(
                GhostNetWebSocketClient.getInstance(),
                GhostNetWebSocketClient.getInstance()
        );
    }
}
