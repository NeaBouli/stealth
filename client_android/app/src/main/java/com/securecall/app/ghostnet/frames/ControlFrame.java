package com.securecall.app.ghostnet.frames;

public class ControlFrame {

    public enum ControlType {
        HELLO,
        SESSION_INFO,
        HEARTBEAT
    }

    private final ControlType type;
    private final String message;

    public ControlFrame(ControlType type, String message) {
        this.type = type;
        this.message = message;
    }

    public ControlType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public GhostFrame toGhostFrame() {
        return new GhostFrame(GhostFrame.FrameType.CONTROL, message.getBytes());
    }
}
