package com.securecall.app.ghostnet.frames;

public class GhostFrame {

    public enum FrameType {
        AUDIO,
        CONTROL
    }

    private final FrameType type;
    private final byte[] payload;

    public GhostFrame(FrameType type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public FrameType getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }
}
