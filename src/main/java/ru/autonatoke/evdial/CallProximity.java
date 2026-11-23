package ru.autonatoke.evdial;

import android.content.Context;
import android.os.PowerManager;

/** Android owns the near/far transition even while the call activity is paused. */
final class CallProximity {
    private final PowerManager.WakeLock lock;
    private boolean cellular, sip;

    CallProximity(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        lock = power != null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
                ? power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,"EVDial:CallEarpiece") : null;
        if (lock != null) lock.setReferenceCounted(false);
    }

    synchronized void cellular(boolean enabled) { cellular = enabled; update(); }
    synchronized void sip(boolean enabled) { sip = enabled; update(); }
    private void update() {
        if (lock == null) return;
        boolean needed = cellular || sip;
        if (needed && !lock.isHeld()) lock.acquire();
        else if (!needed && lock.isHeld()) lock.release();
    }
}
