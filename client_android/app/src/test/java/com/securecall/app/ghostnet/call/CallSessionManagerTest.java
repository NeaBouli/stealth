package com.securecall.app.ghostnet.call;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;

public class CallSessionManagerTest {

    @Before
    public void resetSingleton() throws Exception {
        Field instance = CallSessionManager.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void testInitialStateIsIdle() {
        assertEquals(CallSessionManager.CallState.IDLE, CallSessionManager.getInstance().getState());
    }

    @Test
    public void testOnWebSocketConnected_transitionsToInCall() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        mgr.onWebSocketConnected();
        assertEquals(CallSessionManager.CallState.IN_CALL, mgr.getState());
    }

    @Test
    public void testOnWebSocketClosed_code1000_normalHangup() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        mgr.onWebSocketConnected();
        mgr.onWebSocketClosed(1000, "ok");
        assertEquals(CallSessionManager.CallState.IDLE, mgr.getState());
        assertEquals(CallSessionManager.CallEndReason.NORMAL_HANGUP, mgr.getLastEndReason());
    }

    @Test
    public void testOnWebSocketClosed_code1006_remoteClosed() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        mgr.onWebSocketConnected();
        mgr.onWebSocketClosed(1006, "abnormal");
        assertEquals(CallSessionManager.CallState.IDLE, mgr.getState());
        assertEquals(CallSessionManager.CallEndReason.REMOTE_CLOSED, mgr.getLastEndReason());
    }

    @Test
    public void testOnWebSocketClosed_code4000_remoteClosed() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        mgr.onWebSocketConnected();
        mgr.onWebSocketClosed(4000, "custom");
        assertEquals(CallSessionManager.CallEndReason.REMOTE_CLOSED, mgr.getLastEndReason());
    }

    @Test
    public void testOnWebSocketError_networkError() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        mgr.onWebSocketConnected();
        mgr.onWebSocketError(new IOException("connection refused"));
        assertEquals(CallSessionManager.CallState.IDLE, mgr.getState());
        assertEquals(CallSessionManager.CallEndReason.NETWORK_ERROR, mgr.getLastEndReason());
    }

    @Test
    public void testMultipleConnectDisconnectCycles() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        for (int i = 0; i < 5; i++) {
            mgr.onWebSocketConnected();
            assertEquals(CallSessionManager.CallState.IN_CALL, mgr.getState());
            mgr.onWebSocketClosed(1000, "ok");
            assertEquals(CallSessionManager.CallState.IDLE, mgr.getState());
        }
    }

    @Test
    public void testGetStateName() {
        CallSessionManager mgr = CallSessionManager.getInstance();
        assertEquals("IDLE", mgr.getStateName());
        mgr.onWebSocketConnected();
        assertEquals("IN_CALL", mgr.getStateName());
    }

    @Test
    public void testInitialEndReasonIsUnknown() {
        assertEquals(CallSessionManager.CallEndReason.UNKNOWN,
                CallSessionManager.getInstance().getLastEndReason());
    }

    @Test
    public void testSingletonReturnsSameInstance() {
        assertSame(CallSessionManager.getInstance(), CallSessionManager.getInstance());
    }
}
