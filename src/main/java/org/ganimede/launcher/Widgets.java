package org.ganimede.launcher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** The widget stack on the home screen (swipe between them) and the widgets inside pop-up folders. */
final class Widgets {
    private static final int REQ_BIND = 11, REQ_CONF = 12;

    private final Activity a;
    private final Ui ui;
    private final Layout layout;
    private final Runnable refresh;
    private final Handler handler;
    private final AppWidgetHost host;
    private final AppWidgetManager awm;
    private ClassicHome classic;
    /** The home's widget strip, one widget per page. */
    final HorizontalScrollView strip;
    private final LinearLayout row;
    private String target = "stack";
    private int pending = -1;
    /** The widget list and sizes the row was last built for: a widget host view is costly, so it's only rebuilt on a change. */
    private String built = "";
    /** One live host view per id; classic refreshes move these views instead of recreating their remote UI. */
    private final Map<String, AppWidgetHostView> views = new HashMap<>();

    Widgets(Activity a, Ui ui, Layout layout, Handler handler, Runnable refresh) {
        this.a = a;
        this.ui = ui;
        this.layout = layout;
        this.refresh = refresh;
        this.handler = handler;
        host = new AppWidgetHost(a, 1024);
        awm = AppWidgetManager.getInstance(a);
        strip = new HorizontalScrollView(a);
        strip.setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(a);
        strip.addView(row);
        strip.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol && r > l) v.post(() -> { built = ""; rebuild(); });
        });
        strip.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP && row.getChildCount() > 0) {
                int w = strip.getWidth();
                int page = Math.round((float) strip.getScrollX() / Math.max(1, w));
                handler.post(() -> strip.smoothScrollTo(page * w, 0)); // snap: one widget per page, swipe to switch
            }
            return false;
        });
    }

    void setClassic(ClassicHome classic) { this.classic = classic; }

    void start() {
        try {
            host.startListening();
        } catch (Exception ignored) {
            // Widget service restarting; widgets refresh on the next start.
        }
    }

    void stop() {
        try {
            host.stopListening();
        } catch (Exception ignored) {
            // Already stopped.
        }
    }

    List<String> stack() {
        return layout.widgetStack();
    }

    /** A stored widget id, or -1 when the text isn't one. */
    private static int id(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    /** A widget by its stored id, sized for a pop-up; null when it's gone or the id isn't one. */
    View view(String stored) {
        int id = id(stored);
        if (id < 0) return null;
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) { views.remove(stored); return null; }
        AppWidgetHostView v = views.get(stored);
        if (v == null) {
            v = host.createView(a.getApplicationContext(), id, info);
            v.setAppWidget(id, info);
            views.put(stored, v);
        }
        int h = Math.max(info.minHeight, ui.dp(110));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.min(h, ui.dp(280))));
        return v;
    }

    void size(View view, int width, int height) {
        if (!(view instanceof AppWidgetHostView) || width <= 0 || height <= 0) return;
        float density = a.getResources().getDisplayMetrics().density;
        int w = Math.max(1, Math.round(width / density)), h = Math.max(1, Math.round(height / density));
        ((AppWidgetHostView) view).updateAppWidgetSize(new Bundle(), w, h, w, h);
    }

    /** Frees the widgets of a folder being deleted. */
    void deleteAll(List<String> stored) {
        for (String w : stored) if (id(w) >= 0) {
            views.remove(w);
            host.deleteAppWidgetId(id(w));
        }
    }

    void delete(String stored) {
        delete(List.of(stored));
    }

    void delete(List<String> stored) {
        Set<String> used = new HashSet<>(stack());
        for (org.ganimede.launcher.core.Blocks.Item b : layout.blocks()) used.add(b.id);
        for (String widget : stored) if (!used.contains(widget) && id(widget) >= 0) {
            views.remove(widget);
            host.deleteAppWidgetId(id(widget));
        }
    }

    /** The home's stack, rebuilt only when its widgets or their size changed. */
    void rebuild() {
        if (layout.classic()) { strip.setVisibility(View.GONE); return; }
        int w = strip.getWidth();
        if (w <= 0) w = a.getResources().getDisplayMetrics().widthPixels - ui.dp(40 + 72);
        int wh = ui.prefs.getInt("list_widget_h", 0);
        String want = stack() + "|" + w + "|" + wh;
        if (want.equals(built)) {
            strip.setVisibility(row.getChildCount() > 0 ? View.VISIBLE : View.GONE);
            return;
        }
        built = want;
        row.removeAllViews();
        for (String id : stack()) {
            View v = view(id);
            if (v == null) { built = ""; continue; } // its app may be paused or updating: try again next time
            v.setTag(v.getLayoutParams().height); // its own height, used when the size setting is "auto"
            v.setLayoutParams(new LinearLayout.LayoutParams(w, wh > 0 ? ui.dp(wh) : v.getLayoutParams().height));
            row.addView(v);
        }
        strip.setVisibility(row.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** The height setting of the current mode, in dp; 0 = each widget's own height. */
    void applyHeight(int dpHeight) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View w = row.getChildAt(i);
            ViewGroup.LayoutParams lp = w.getLayoutParams();
            int h = dpHeight > 0 ? ui.dp(dpHeight) : (int) w.getTag();
            if (lp.height != h) { lp.height = h; w.setLayoutParams(lp); }
        }
    }

    /**
     * Widget picker built from the installed providers (works on phones without a system picker). Their names are
     * read on a background thread (one package lookup each), then the list opens.
     */
    void pick(String forTarget) {
        new Thread(() -> {
            List<AppWidgetProviderInfo> l = new ArrayList<>();
            List<String> names = new ArrayList<>();
            PackageManager pm = a.getPackageManager();
            try {
                for (UserHandle u : a.getSystemService(LauncherApps.class).getProfiles()) l.addAll(awm.getInstalledProvidersForProfile(u));
            } catch (RuntimeException e) { // a profile going away right now: what was read is offered
                // keep the list read so far
            }
            for (AppWidgetProviderInfo info : l) {
                String app;
                try {
                    app = pm.getApplicationLabel(pm.getApplicationInfo(info.provider.getPackageName(), 0)).toString();
                } catch (Exception e) {
                    app = info.provider.getPackageName();
                }
                names.add(app + " · " + info.loadLabel(pm));
            }
            Integer[] order = new Integer[names.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (x, y) -> names.get(x).compareToIgnoreCase(names.get(y)));
            String[] sorted = new String[order.length];
            for (int i = 0; i < order.length; i++) sorted[i] = names.get(order[i]);
            handler.post(() -> {
                if (!a.isDestroyed()) ui.ask().setTitle(ui.tr("Add widget"))
                        .setItems(sorted, (d, w) -> bind(l.get(order[w]), forTarget)).show();
            });
        }).start();
    }

    private void bind(AppWidgetProviderInfo info, String forTarget) {
        target = forTarget;
        pending = host.allocateAppWidgetId();
        // Survives Android recreating the home while the permission or setup screen is open.
        ui.prefs.edit().putInt("pending_widget", pending).putString("pending_widget_for", target).apply();
        try {
            if (awm.bindAppWidgetIdIfAllowed(pending, info.getProfile(), info.provider, null)) {
                configure(pending);
                return;
            }
            a.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile()), REQ_BIND);
        } catch (RuntimeException e) { // its app just went away, or this phone has no screen to allow it: the id is freed
            host.deleteAppWidgetId(pending);
            pending = -1;
            ui.prefs.edit().remove("pending_widget").remove("pending_widget_for").apply();
            Ui.cantOpen(a);
        }
    }

    private void configure(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(a, id, 0, REQ_CONF, null);
                return;
            } catch (Exception e) {
                // Some widgets can't be configured from outside; add them as they are.
            }
        }
        add(id);
    }

    private void add(int id) {
        pending = -1;
        target = ui.prefs.getString("pending_widget_for", target);
        ui.prefs.edit().remove("pending_widget").remove("pending_widget_for").apply();
        if (target.startsWith("classic:")) {
            AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
            if (classic == null || info == null) { host.deleteAppWidgetId(id); return; }
            int page;
            try { page = Integer.parseInt(target.substring(8)); } catch (NumberFormatException e) { page = 0; }
            classic.addWidget(id, page, info);
            return;
        } else if (target.equals("stack")) {
            // Four widgets per stack: enough to swipe through, few enough to stay light.
            if (!layout.addStackWidget(String.valueOf(id))) {
                host.deleteAppWidgetId(id);
                ui.toast(ui.tr("Widget stack full: remove a widget first"));
                return;
            }
        } else {
            JSONObject f = layout.folder(target);
            List<String> w = Layout.strings(f.optJSONArray("widgets"));
            w.add(String.valueOf(id));
            try {
                if (!f.has("name") && !target.equals("button")) f.put("name", ui.tr("Folder")); // the button's pop-up takes its name from the app
                f.put("widgets", new JSONArray(w));
            } catch (Exception ignored) { return; }
            layout.saveFolder(target, f);
        }
        refresh.run();
    }

    void removeDialog() {
        List<String> s = stack();
        if (s.isEmpty()) { ui.toast(ui.tr("No widgets yet")); return; }
        String[] names = new String[s.size()];
        for (int i = 0; i < names.length; i++) {
            AppWidgetProviderInfo info = awm.getAppWidgetInfo(id(s.get(i)));
            names[i] = info == null ? ui.tr("Missing widget") : info.loadLabel(a.getPackageManager());
        }
        ui.ask().setTitle(ui.tr("Remove widget"))
                .setItems(names, (d, w) -> {
                    String removed = s.remove(w);
                    ui.prefs.edit().putString("stack", String.join(",", s)).apply();
                    delete(removed);
                    refresh.run();
                }).show();
    }

    /** The bind permission or the widget's own setup screen answered. */
    void onResult(int req, int res) {
        if (req != REQ_BIND && req != REQ_CONF) return;
        if (pending < 0) pending = ui.prefs.getInt("pending_widget", -1); // the home was recreated meanwhile
        if (pending < 0) return;
        if (res != Activity.RESULT_OK) { host.deleteAppWidgetId(pending); pending = -1; ui.prefs.edit().remove("pending_widget").apply(); return; }
        if (req == REQ_BIND) configure(pending); else add(pending);
    }
}
