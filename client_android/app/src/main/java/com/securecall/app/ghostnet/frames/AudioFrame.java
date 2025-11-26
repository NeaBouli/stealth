package com.securecall.app.ghostnet.frames;

public class AudioFrame {

    private final byte[] data;

    public AudioFrame(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public GhostFrame toGhostFrame() {
        return new GhostFrame(GhostFrame.FrameType.AUDIO, data);
    }
}
