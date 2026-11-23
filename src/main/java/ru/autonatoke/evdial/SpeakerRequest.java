package ru.autonatoke.evdial;

/** Per-call user intent. A missing audio route must not disable the speaker button. */
final class SpeakerRequest {
    private Boolean desired;
    private boolean pending;
    private long revision;

    void toggle(Boolean actual) {
        desired = !selected(actual);
        pending = true;
        revision++;
    }

    void observe(Boolean actual, boolean beforeAnswer) {
        if (desired == null) return;
        pending = actual == null || !desired.equals(actual);
        // Keep an early selection through CONNECTING/DIALING -> ACTIVE, then
        // let headset/car controls own subsequent changes once it is confirmed.
        if (!beforeAnswer && !pending) desired = null;
    }

    boolean selected(Boolean actual) {
        return desired != null ? desired : Boolean.TRUE.equals(actual);
    }

    boolean pending() { return desired != null && pending; }
    boolean hasChoice() { return desired != null; }
    boolean desired() { return Boolean.TRUE.equals(desired); }
    long revision() { return revision; }

    void clear() {
        desired = null;
        pending = false;
        revision++;
    }
}
