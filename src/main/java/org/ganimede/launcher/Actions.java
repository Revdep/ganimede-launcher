package org.ganimede.launcher;

import org.ganimede.launcher.core.Target;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * What the Ganimede button can do on tap, double tap, triple tap and long press. An action is stored as a short
 * string: a built-in id ("camera", "torch", ...), "app:<component key>", "sc:<package>|<shortcut id>|<label>"
 * or "link:<name>|<uri>".
 */
final class Actions {
    static final String[] GESTURES = {"fab_tap", "fab_double", "fab_triple", "fab_long"};
    static final String[] GESTURE_NAMES = {"Tap", "Double tap", "Triple tap", "Long press"};
    static final String[] DEFAULTS = {"popup", "none", "none", "ganimede_notifs"};

    /** Built-in actions: id, English name, glyph. */
    static final String[][] BUILT_IN = {
            {"none", "Nothing", "close"},
            {"popup", "Open the button's pop-up", "grid"},
            {"search", "Search", "search"},
            {"ganimede_notifs", "Notifications (Ganimede)", "bell"},
            {"shade", "Notification shade", "down"},
            {"quick_settings", "Quick settings", "gear"},
            {"theme_toggle", "Switch light / dark look", "contrast"},
            {"screenshot", "Screenshot", "image"},
            {"camera", "Camera", "camera"},
            {"video", "Video camera", "camera"},
            {"torch", "Flashlight on / off", "flash"},
            {"media", "Play / pause music", "play"},
            {"lock", "Lock the screen", "lock"},
            {"recents", "Recent apps", "layers"},
            {"power", "Power menu", "alert"},
            {"agenda", "Agenda", "calendar"},
            {"forecast", "Weather forecast", "cloud"},
            {"alarm", "New alarm", "clock"},
            {"timer", "New timer", "clock"},
            {"wifi", "Wi-Fi panel", "globe"},
            {"volume", "Volume panel", "music"},
            {"ganimede_settings", "Ganimede settings", "gear"}};

    /** Things only the launcher itself can do (open its own screens). */
    interface Home {
        void showFolder(String id);
        void showList(boolean typing);
        void showNotifications();
        void showAgenda();
        void showForecast();
        void restyle();
    }

    /** Each flash's real state, as Android reports it (turned on from quick settings too). */
    private static final java.util.Map<String, Boolean> torchOn = new java.util.HashMap<>();
    private static boolean torchWatched;

    static String name(Context c, String action) {
        if (action == null || action.isEmpty()) return T.t(c, "Nothing");
        for (String[] b : BUILT_IN) if (b[0].equals(action)) return T.t(c, b[1]);
        if (action.startsWith("app:")) {
            String label = Apps.labelOf(c, action.substring(4));
            return label == null ? T.t(c, "An app that is no longer installed") : T.f(c, "Open %s", label);
        }
        if (action.startsWith("sc:") || action.startsWith("link:")) {
            String[] p = action.substring(action.indexOf(':') + 1).split("\\|", 3);
            return action.startsWith("sc:") ? (p.length > 2 ? p[2] : p[p.length - 1]) : p[0];
        }
        return action;
    }

    static void run(Activity c, Home home, String action) {
        if (action == null) return;
        try {
            switch (action) {
                case "none": return;
                case "popup": home.showFolder("button"); return;
                case "search": home.showList(true); return;
                case "ganimede_notifs": home.showNotifications(); return;
                case "agenda": home.showAgenda(); return;
                case "forecast": home.showForecast(); return;
                case "shade": if (!LockService.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) openNotificationShade(c); return;
                case "quick_settings": if (!LockService.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) needsService(c); return;
                case "screenshot":
                    if (Build.VERSION.SDK_INT < 28 || !LockService.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) needsService(c);
                    return;
                case "lock":
                    if (Build.VERSION.SDK_INT < 28) Ui.toast(c, T.t(c, "Needs Android 9 or newer"), Toast.LENGTH_SHORT);
                    else if (!LockService.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) needsService(c);
                    return;
                case "recents": if (!LockService.global(AccessibilityService.GLOBAL_ACTION_RECENTS)) needsService(c); return;
                case "power": if (!LockService.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) needsService(c); return;
                case "theme_toggle": {
                    boolean dark = Ui.darkText(Prefs.of(c));
                    Prefs.of(c).edit().putString("text_color", dark ? "light" : "dark").apply();
                    home.restyle();
                    return;
                }
                case "camera": start(c, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); return;
                case "video": start(c, new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)); return;
                case "torch": torch(c); return;
                case "media": {
                    AudioManager am = c.getSystemService(AudioManager.class);
                    long t = SystemClock.uptimeMillis();
                    am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                    am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                    return;
                }
                case "alarm": start(c, new Intent(AlarmClock.ACTION_SET_ALARM)); return;
                case "timer": start(c, new Intent(AlarmClock.ACTION_SET_TIMER)); return;
                case "wifi": start(c, new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS)); return;
                case "volume": start(c, new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_VOLUME : Settings.ACTION_SOUND_SETTINGS)); return;
                case "ganimede_settings": start(c, new Intent(c, SettingsActivity.class)); return;
                default:
            }
            if (action.startsWith("app:")) {
                String key = action.substring(4);
                long serial = Target.serial(key);
                ComponentName cn = ComponentName.unflattenFromString(Target.component(key));
                if (cn == null) throw new IllegalArgumentException("bad component");
                UserHandle user = serial < 0 ? Process.myUserHandle() : c.getSystemService(UserManager.class).getUserForSerialNumber(serial);
                if (user == null) throw new IllegalStateException("profile gone"); // never open the personal copy instead
                c.getSystemService(LauncherApps.class).startMainActivity(cn, user, null, null);
            } else if (action.startsWith("sc:")) {
                String[] p = action.substring(3).split("\\|", 3);
                c.getSystemService(LauncherApps.class).startShortcut(p[0], p[1], null, null, Process.myUserHandle());
            } else if (action.startsWith("link:")) {
                String[] p = action.substring(5).split("\\|", 2);
                Intent i = p.length > 1 ? Sunflower.intent(p[1]) : null;
                if (i != null) start(c, i);
            }
        } catch (Exception e) {
            if (!(e instanceof android.content.ActivityNotFoundException || e instanceof SecurityException
                    || e instanceof IllegalStateException || e instanceof IllegalArgumentException))
                android.util.Log.w("Ganimede", "Action failed: " + e.getClass().getName()); // a bug of ours, not a missing app
            Ui.cantOpen(c);
        }
    }

    private static void start(Context c, Intent i) {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Flashlight through Android's torch switch (needs no camera permission). */
    private static void torch(Context c) throws Exception {
        CameraManager cm = c.getSystemService(CameraManager.class);
        if (!torchWatched) {
            torchWatched = true;
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean on) { torchOn.put(id, on); }
            }, null);
        }
        for (String id : cm.getCameraIdList()) {
            Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (flash != null && flash) {
                cm.setTorchMode(id, !torchOn.getOrDefault(id, false));
                return;
            }
        }
        Ui.toast(c, T.t(c, "This phone has no flashlight"), Toast.LENGTH_SHORT);
    }

    @SuppressWarnings({"JavaReflectionMemberAccess", "WrongConstant"})
    static void openNotificationShade(Context c) {
        try {
            Object sb = c.getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception ignored) {
            // Blocked on some phones.
        }
    }

    private static void needsService(Activity a) { Ui.explainLock(a, null); }

    private Actions() {}
}
