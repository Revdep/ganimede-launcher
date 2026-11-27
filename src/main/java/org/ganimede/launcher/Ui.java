package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** The home screen's look (read from settings on every refresh) and the small view helpers every part shares. */
final class Ui {
    final Activity a;
    final SharedPreferences prefs;
    int fg = Colors.WHITE, accent = Colors.ACCENT;
    Typeface face = Typeface.SANS_SERIF;
    String shape = "theme";
    String themedShape = Icons.DEFAULT_THEMED_SHAPE;
    /** Where the last touch on this screen began: a pop-up opens from the thing under it. */
    float touchX = -1, touchY = -1;

    Ui(Activity a, SharedPreferences prefs) {
        this.a = a;
        this.prefs = prefs;
    }

    void read() {
        fg = darkText(prefs) ? Colors.INK : Colors.WHITE;
        accent = accent(prefs);
        face = font(prefs.getString("font", "sans-serif"));
        shape = prefs.getString("icon_shape", Icons.DEFAULT_SHAPE);
        themedShape = prefs.getString("themed_shape", Icons.DEFAULT_THEMED_SHAPE);
    }

    /** Dark text chosen for a light wallpaper (shared by the home, the settings and their preview). */
    static boolean darkText(SharedPreferences p) {
        return "dark".equals(p.getString("text_color", "light"));
    }

    static int accent(SharedPreferences p) {
        return p.getInt("accent", Colors.ACCENT);
    }

    /** The dock shelf's colour: white (or black under dark text) at the chosen opacity, 0 = transparent. */
    static int dockColor(SharedPreferences p, boolean darkText) {
        return Colors.alpha(darkText ? 0x000000 : 0xFFFFFF, Math.round(dockAlpha(p) * 2.55f));
    }

    /** The dock shelf's opacity in percent; before it had a slider it was only on (20) or off. */
    static int dockAlpha(SharedPreferences p) { return p.getInt("dock_alpha", p.getBoolean("dock_bg", true) ? 20 : 0); }

    /** Light text on the wallpaper (the default), which gets a shadow; dark text gets none. */
    boolean light() {
        return fg == Colors.WHITE;
    }

    String tr(String en) {
        return T.t(a, en);
    }

    String f(String en, Object... args) {
        return T.f(a, en, args);
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    float slop() {
        return ViewConfiguration.get(a).getScaledTouchSlop();
    }

    boolean haptics() {
        return prefs.getBoolean("haptics", true);
    }

    /** The typeface a font choice names (the one place a font preference becomes a Typeface). */
    static Typeface font(String name) {
        return Typeface.create(name, Typeface.NORMAL);
    }

    /** Text on the wallpaper, in the theme's colour and font. */
    TextView text(float sp, boolean bold) {
        TextView t = new TextView(a);
        t.setTextColor(fg);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(face, bold ? Typeface.BOLD : Typeface.NORMAL));
        if (light()) t.setShadowLayer(dp(5), 0, dp(1), 0x66000000);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A plain label for dialogs (no wallpaper shadow). */
    TextView plain(float sp, boolean bold, int color) {
        TextView t = new TextView(a);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(face, bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    ImageView icon(Drawable d, boolean fromPack, int size) {
        return icon(d, fromPack, size, shape);
    }

    ImageView icon(Drawable d, boolean fromPack, int size, String iconShape) {
        ImageView icon = new ImageView(a);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Icons.apply(icon, d, fromPack, iconShape, themedShape, fg, accent, size);
        return icon;
    }

    /** App-name sizes follow the "label size" setting (100% = the built-in size). */
    float labelSp(float base) {
        return base * prefs.getInt("label_scale", 100) / 100f;
    }

    /** A home-screen dialog: it grows out of what was touched and closes back onto it. */
    Ask ask() {
        return new Ask(this);
    }

    /** A screen that closes its dialogs itself when it goes: no leaked window, and each dialog's OnDismiss runs. */
    interface Owner {
        void own(Dialog d);
    }

    /** The settings' dialogs, in the system's dark style; an Owner screen is told of each one. */
    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert) {
            @Override
            public AlertDialog create() {
                AlertDialog d = super.create();
                if (c instanceof Owner o) o.own(d);
                return d;
            }
        };
    }

    void open(Intent i) {
        start(a, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void appInfo(Context c) { start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()))); }

    /** Starts a screen of another app, or says none can open it. */
    static void start(Context c, Intent i) {
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            // not the exception itself: its message holds the whole Intent, data URI included
            Log.w("Ganimede", "Could not start " + i.getAction() + " " + i.getComponent() + ": " + e.getClass().getName());
            cantOpen(c);
        }
    }

    static void cantOpen(Context c) {
        toast(c, T.t(c, "No app can open this"), Toast.LENGTH_SHORT);
    }

    /** The one way a message is shown: from the application's context, so it also shows from a screen that just closed. */
    static void toast(Context c, String text, int length) {
        Toast.makeText(c.getApplicationContext(), text, length).show();
    }

    void toast(String s) { toast(a, s, Toast.LENGTH_SHORT); }

    /** A message with more to read. */
    void toastLong(String s) { toast(a, s, Toast.LENGTH_LONG); }

    /** Whether Ganimede's notification listener is switched on in Android. */
    static boolean notifAllowed(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(new ComponentName(c, NotifService.class).flattenToString());
    }

    /** The notification-access explanation, then Android's screen; declined runs if the person does not go on (may be null). */
    static void explainNotif(Activity a, Runnable declined) {
        explainRestricted(a, "Notification access", "Switch on \"Ganimede\" on the next screen. Message text never leaves your phone.",
                Build.VERSION.SDK_INT >= 30
                        ? new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(a, NotifService.class).flattenToString())
                        : new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), declined);
    }

    /** The Accessibility (Quick Lock and button actions) explanation, then Android's screen. */
    static void explainLock(Activity a, Runnable declined) {
        explainRestricted(a, "Quick Lock",
                "Switch on \"Ganimede Quick Lock\" under Accessibility. It only runs system actions (lock, screenshot, recent apps…) and reads nothing on screen.",
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), declined);
    }

    /**
     * Notification access and Accessibility are "restricted settings" on Android 13+ for apps installed from a file:
     * their switch stays grey until the person allows it in App info. This says how, then opens the right screen.
     * Cancel, App info or a dismissed dialog run declined (may be null): the caller puts its switch back off.
     */
    private static void explainRestricted(Activity a, String what, String message, Intent target, Runnable declined) {
        boolean[] opened = {false};
        dialog(a)
                .setTitle(T.t(a, what))
                .setMessage(T.t(a, message) + "\n\n" + T.t(a, "1. Open and switch it on.\n2. If Android blocks it, press OK.\n"
                        + "3. App info → ⋮ (top right) → \"Allow restricted settings\".\n4. Come back and switch it on again.\n"
                        + "Android asks this of every app installed from a file instead of a store."))
                .setPositiveButton(T.t(a, "Open"), (d, w) -> {
                    opened[0] = true;
                    try {
                        a.startActivity(target);
                    } catch (Exception e) {
                        if (Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS.equals(target.getAction()))
                            start(a, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                        else appInfo(a);
                    }
                })
                .setNeutralButton(T.t(a, "App info"), (d, w) -> appInfo(a))
                .setNegativeButton(T.t(a, "Cancel"), null)
                .setOnDismissListener(d -> {
                    if (!opened[0] && declined != null) declined.run();
                })
                .show();
    }

    void settings() {
        a.startActivity(new Intent(a, SettingsActivity.class));
    }
}
