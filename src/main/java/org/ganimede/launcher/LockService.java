package org.ganimede.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Quick Lock (double-tap the home screen or the alphabet to turn the screen off) and the Ganimede button's system
 * actions: screenshot, recent apps, power menu, notification shade, quick settings. It only performs these global
 * actions; it receives no events and reads no screen content.
 */
public class LockService extends AccessibilityService {
    private static volatile LockService running;

    @Override
    protected void onServiceConnected() {
        running = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent i) {
        running = null;
        return super.onUnbind(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {}

    @Override
    public void onInterrupt() {}

    /** True when the screen was locked; false means the service isn't switched on. */
    static boolean lock() {
        return global(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** One of Android's global actions (lock, screenshot, recents, power menu, quick settings); false if switched off. */
    static boolean global(int action) {
        if (action == GLOBAL_ACTION_LOCK_SCREEN && android.os.Build.VERSION.SDK_INT < 28) return false; // Android 9+
        LockService s = running;
        return s != null && s.performGlobalAction(action);
    }
}
