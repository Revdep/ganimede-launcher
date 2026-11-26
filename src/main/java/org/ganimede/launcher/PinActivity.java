package org.ganimede.launcher;

import org.ganimede.launcher.core.Blocks;
import org.ganimede.launcher.core.Safe;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * "Add to home screen" from other apps: a browser's page shortcut or web app (PWA), an app's pinned shortcut, or a
 * widget an app offers to place. As the home app, Ganimede is asked to confirm; it asks where to put it.
 */
public class PinActivity extends Activity {
    private static final int REQ_BIND = 31, REQ_CONFIGURE = 32;
    private int pendingWidget = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null && b.containsKey("widget")) {
            pendingWidget = b.getInt("widget", -1);
            return; // the bind or configuration screen still owns the pending result
        }
        LauncherApps la = getSystemService(LauncherApps.class);
        LauncherApps.PinItemRequest req;
        try {
            req = la.getPinItemRequest(getIntent());
        } catch (Exception e) {
            req = null;
        }
        if (req == null || !req.isValid()) {
            finish();
            return;
        }
        SharedPreferences p = Prefs.of(this);
        try {
            if (req.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) askShortcut(p, req);
            else askWidget(p, req);
        } catch (RuntimeException bad) { // another app's request we can't read: nothing is added
            finish();
        }
    }

    /**
     * The name of an installed app, or null. Any app can send this screen a made-up request, so what's stored and
     * shown comes from the system's own record of the app.
     */
    private String appLabel(String pkg, UserHandle user) {
        try {
            return Safe.printable(getSystemService(LauncherApps.class).getApplicationInfo(pkg, 0, user).loadLabel(getPackageManager()).toString(), 64);
        } catch (Exception notInstalled) {
            return null;
        }
    }

    /** A dialog answer: the request may have expired or its app gone meanwhile, then nothing is added. */
    private void guarded(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException expired) {
            // nothing added
        }
        finish();
    }

    private void askShortcut(SharedPreferences p, LauncherApps.PinItemRequest req) {
        ShortcutInfo si = req.getShortcutInfo();
        String app = si == null || si.getPackage() == null ? null : appLabel(si.getPackage(), si.getUserHandle());
        CharSequence shortLabel = si == null ? null : si.getShortLabel();
        String entry = app == null ? null : Safe.shortcutEntry(si.getPackage(), si.getId(), shortLabel == null ? null : shortLabel.toString());
        if (entry == null) { finish(); return; }
        String label = Entries.shortcutName(entry);
        Layout layout = new Layout(this, p);
        List<String> ids = layout.folderIds(), names = new ArrayList<>();
        names.add("★  " + T.t(this, "Add to favourites"));
        for (String id : ids) names.add("▦  " + layout.folder(id).optString("name", T.t(this, "Folder")));
        Ui.dialog(this)
                .setTitle(T.f(this, "Add “%s”", label + " · " + app))
                .setItems(names.toArray(new String[0]), (d, w) -> guarded(() -> {
                    if (!req.accept()) return;
                    if (w == 0) {
                        List<String> fav = layout.favourites();
                        fav.add(entry);
                        layout.saveFavourites(fav);
                    } else {
                        layout.editFolderApps(ids.get(w - 1), a -> a.add(entry));
                    }
                    Ui.toast(this, T.t(this, "Added"), Toast.LENGTH_SHORT);
                }))
                .setNegativeButton(T.t(this, "Cancel"), (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    /** A widget offered by an app follows the same bind and placement rules as one chosen on the home screen. */
    private void askWidget(SharedPreferences p, LauncherApps.PinItemRequest req) {
        AppWidgetProviderInfo info = req.getAppWidgetProviderInfo(this);
        String app = info == null ? null : appLabel(info.provider.getPackageName(), info.getProfile());
        if (app == null) { finish(); return; }
        Layout layout = new Layout(this, p);
        if (!layout.classic() && layout.widgetStack().size() >= 4) {
            Ui.toast(this, T.t(this, "Widget stack full: remove a widget first"), Toast.LENGTH_LONG);
            finish();
            return;
        }
        // Ask first, as for a shortcut: an app can't put a widget on the home screen by itself.
        Ui.dialog(this)
                .setTitle(T.f(this, "Add “%s”", Safe.printable(info.loadLabel(getPackageManager()), 64) + " · " + app))
                .setPositiveButton(T.t(this, "Add"), (d, w) -> beginWidget(info))
                .setNegativeButton(T.t(this, "Cancel"), (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void beginWidget(AppWidgetProviderInfo info) {
        AppWidgetHost host = new AppWidgetHost(this, 1024);
        pendingWidget = host.allocateAppWidgetId();
        try {
            if (AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(pendingWidget, info.getProfile(), info.provider, null)) {
                configureWidget(info);
                return;
            }
            startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile()), REQ_BIND);
        } catch (RuntimeException failed) {
            abandonWidget();
        }
    }

    private LauncherApps.PinItemRequest request() {
        try { return getSystemService(LauncherApps.class).getPinItemRequest(getIntent()); }
        catch (RuntimeException expired) { return null; }
    }

    private void configureWidget(AppWidgetProviderInfo info) {
        if (info.configure != null) try {
            new AppWidgetHost(this, 1024).startAppWidgetConfigureActivityForResult(this, pendingWidget, 0, REQ_CONFIGURE, null);
            return;
        } catch (RuntimeException cannotConfigure) {
            // Add it with its defaults, as the home-screen picker does.
        }
        acceptWidget(info);
    }

    private void acceptWidget(AppWidgetProviderInfo info) {
        LauncherApps.PinItemRequest req = request();
        if (req == null || !req.isValid()) { abandonWidget(); return; }
        SharedPreferences p = Prefs.of(this);
        Layout layout = new Layout(this, p);
        Blocks.Result block = null;
        if (layout.classic()) {
            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            int cw = Math.max(1, m.widthPixels / layout.cols()), ch = Math.max(1, m.heightPixels / layout.rows());
            int cols = Math.max(1, Math.min(layout.cols(), (Math.max(1, info.minWidth) + cw - 1) / cw));
            int rows = Math.max(1, Math.min(layout.rows(), (Math.max(1, info.minHeight) + ch - 1) / ch));
            block = layout.placeBlock(String.valueOf(pendingWidget), cols, rows, 0);
            if (block.pages > 100) { abandonWidget(); return; }
        } else if (layout.widgetStack().size() >= 4) {
            Ui.toast(this, T.t(this, "Widget stack full: remove a widget first"), Toast.LENGTH_LONG);
            abandonWidget();
            return;
        }
        Bundle extras = new Bundle();
        extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget);
        try {
            if (!req.accept(extras)) { abandonWidget(); return; }
        } catch (RuntimeException expired) { abandonWidget(); return; }
        if (block != null) layout.savePlacement(block.blocks, block.cells, block.pages);
        else if (!layout.addStackWidget(String.valueOf(pendingWidget))) { abandonWidget(); return; }
        pendingWidget = -1;
        Ui.toast(this, T.t(this, "Added"), Toast.LENGTH_SHORT);
        finish();
    }

    private void abandonWidget() {
        if (pendingWidget >= 0) new AppWidgetHost(this, 1024).deleteAppWidgetId(pendingWidget);
        pendingWidget = -1;
        Ui.toast(this, T.t(this, "Add widgets with a long-press on the home screen"), Toast.LENGTH_LONG);
        finish();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return;
        if (resultCode != RESULT_OK || pendingWidget < 0) { abandonWidget(); return; }
        LauncherApps.PinItemRequest req = request();
        AppWidgetProviderInfo info = req == null ? null : req.getAppWidgetProviderInfo(this);
        if (info == null) { abandonWidget(); return; }
        if (requestCode == REQ_BIND) configureWidget(info); else acceptWidget(info);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingWidget >= 0) out.putInt("widget", pendingWidget);
    }
}
