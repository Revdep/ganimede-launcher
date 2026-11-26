package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.Set;

/** Starting apps and pinned shortcuts from their icons, so their windows grow out of (and close back into) them. */
final class Launcher {
    private final Ui ui;
    private final Apps apps;
    private final LauncherApps launcherApps;
    private final Handler handler;
    private final Runnable showHome;
    private final Landing landing;
    /** Set when an app was opened from here: coming back from it, the home doesn't play its own entrance. */
    private boolean launched;

    Launcher(Ui ui, Apps apps, LauncherApps launcherApps, Handler handler, Runnable showHome, Landing landing) {
        this.ui = ui;
        this.apps = apps;
        this.launcherApps = launcherApps;
        this.handler = handler;
        this.showHome = showHome;
        this.landing = landing;
    }

    /** The launch target inside a row or cell, or the view itself when it has none. */
    static View iconIn(View v) {
        View marked = markedIcon(v);
        if (marked != null) return marked; // a folder's composite icon wins over one of its miniature app icons
        return firstImage(v);
    }

    private static View markedIcon(View v) {
        if (v.getTag() == Entries.LAUNCH_ICON) return v;
        if (!(v instanceof ViewGroup)) return null;
        for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View found = markedIcon(((ViewGroup) v).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static View firstImage(View v) {
        if (v instanceof ImageView || !(v instanceof ViewGroup)) return v;
        for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View found = firstImage(((ViewGroup) v).getChildAt(i));
            if (found instanceof ImageView) return found;
        }
        return v;
    }

    /** Whether an app was opened since the last call. */
    boolean takeLaunched() {
        boolean was = launched;
        launched = false;
        return was;
    }

    void launch(Item it, View from) {
        // The icon itself, not the whole row: Android grows the window out of this square and, on the way back home,
        // closes it into the same square (makeScaleUpAnimation on the icon, width == height).
        View icon = iconIn(from);
        Rect bounds = new Rect();
        icon.getGlobalVisibleRect(bounds);
        try {
            Bundle opts = ActivityOptions.makeScaleUpAnimation(icon, 0, 0, icon.getWidth(), icon.getHeight()).toBundle();
            if (it.info.getUser().equals(Process.myUserHandle())) {
                // A plain launcher intent carrying the icon's bounds. Android keeps them with the app's
                // task, and its swipe-home animation closes the window back into that rectangle.
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(it.info.getComponentName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                i.setSourceBounds(bounds);
                ui.a.startActivity(i, opts);
            } else {
                launcherApps.startMainActivity(it.info.getComponentName(), it.info.getUser(), bounds, opts); // work profile
            }
            landing.opened(it.key, from);
            launched = true;
            ui.prefs.edit().putInt("use:" + it.key, apps.uses(it) + 1).apply();
            if (ui.prefs.getStringSet("breaker_apps", Set.of()).contains(it.key))
                BreakReceiver.schedule(ui.a, apps.label(it), ui.prefs.getInt("breaker_min", 15));
            handler.postDelayed(showHome, 400);
        } catch (Exception e) {
            ui.toast(ui.f("Could not open %s", apps.label(it)));
        }
    }

    /** A pinned shortcut or web app ("s:pkg|id|name"), growing out of its icon. */
    void openShortcut(String entry, View from) {
        String[] p = entry.substring(2).split("\\|", 3);
        try {
            View icon = iconIn(from);
            Rect b = new Rect();
            icon.getGlobalVisibleRect(b);
            launcherApps.startShortcut(p[0], p[1], b, ActivityOptions.makeScaleUpAnimation(icon, 0, 0,
                    icon.getWidth(), icon.getHeight()).toBundle(), Process.myUserHandle());
        } catch (Exception e) {
            ui.toast(ui.tr("This shortcut is no longer available"));
        }
    }

    /** Asks Android to uninstall an app, in the profile it belongs to (a work app from the work profile). */
    void uninstall(Item it) {
        ui.open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.pkg)).putExtra(Intent.EXTRA_USER, it.info.getUser()));
    }
}
