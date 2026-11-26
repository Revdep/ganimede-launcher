package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Favourites;
import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.Morph;
import org.ganimede.launcher.core.Search;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The home screen: widget stack, clock, date, weather, next event, media player, favourites with pop-up folders (List
 * mode) or grid pages and dock (Classic mode), the quick button, and the A–Z list with smart search. It builds the
 * screen and wires the parts together; each part lives in its own class.
 */
public class MainActivity extends Activity implements Actions.Home {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LauncherApps launcherApps;
    private Ui ui;
    private Layout layout;
    private Apps apps;
    private Launcher launcher;
    private Notices notices;
    private Landing landing;
    private Entries entries;
    private Widgets widgets;
    private DragDrop drag;
    private ClassicHome classic;
    private Folders folders;
    private Menus menus;
    private MediaRows media;
    private AppList list;
    private QuickButton fab;
    private HomeScreen screen;
    private HomeLook look;

    private FrameLayout root;
    boolean listMode;
    private boolean started, notificationsStale, isResumed;
    private long lastEnter;
    private MotionEvent alphabetEdgeDown;
    private boolean alphabetEdgeDragging;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener prefWatch = (p, key) -> {
        if (WeatherStore.KEY.equals(key)) handler.post(this::updateWeather);
        else if ("fab_icon".equals(key)) handler.post(() -> fab.style(!listMode)); // a late import, restore or fallback
    };
    private final LauncherApps.Callback appsChanged = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String p, UserHandle u) { Icons.forget(p); loadAppsSoon(); }
        @Override public void onPackageAdded(String p, UserHandle u) { Icons.forget(p); loadAppsSoon(); }
        @Override public void onPackageChanged(String p, UserHandle u) { Icons.forget(p); loadAppsSoon(); }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) { loadAppsSoon(); }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) { loadAppsSoon(); }
        @Override public void onPackagesSuspended(String[] p, UserHandle u) { loadAppsSoon(); }
        @Override public void onPackagesUnsuspended(String[] p, UserHandle u) { loadAppsSoon(); }
    };
    /** A work profile paused, resumed or unlocked: its apps leave or come back. */
    private final BroadcastReceiver profiles = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadAppsSoon(); }
    };
    private final Runnable loadSoon = this::loadApps;
    /** Notifications changed (NotifService already throttles): off screen the home only notes it must refresh. */
    private final Runnable notified = () -> {
        if (started) refreshNotifications(); else notificationsStale = true;
    };

    /** An update touches several packages at once: one reload after the last event, not a thread per event. */
    private void loadAppsSoon() {
        handler.removeCallbacks(loadSoon);
        handler.postDelayed(loadSoon, 250);
    }

    private void loadApps() {
        apps.load(this::refreshAll);
    }

    // ---------------------------------------------------------------- Actions.Home

    @Override public void showFolder(String id) { folders.show(id); }
    @Override public void showNotifications() { notices.show(null); }
    @Override public void showAgenda() { screen.outlook.showAgenda(); }
    @Override public void showForecast() { screen.outlook.showForecast(); }
    @Override public void restyle() { refreshAll(); }

    // ---------------------------------------------------------------- building the screen

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.of(this);
        launcherApps = getSystemService(LauncherApps.class);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        // The parts, each given what it works with; the ones made later are reached through lambdas.
        ui = new Ui(this, prefs);
        ui.read();
        layout = new Layout(this, prefs);
        apps = new Apps(this, prefs, launcherApps, handler);
        Sheets sheets = new Sheets(ui);
        root = new FrameLayout(this);
        landing = new Landing(this, layout, handler);
        launcher = new Launcher(ui, apps, launcherApps, handler, this::showHome, landing);
        notices = new Notices(ui, apps, root, launcher::launch);
        entries = new Entries(ui, apps, layout, launcher, notices, id -> folders.show(id), e -> menus.forEntry(e));
        widgets = new Widgets(this, ui, layout, handler, this::refreshAll);
        IconPicker icons = new IconPicker(ui, sheets, handler, entries::name, this::refreshAll, this::loadApps);
        drag = new DragDrop(ui, sheets, layout, apps, launcher, notices, handler, e -> menus.forEntry(e),
                (target, d) -> classic.dropOn(target, d), this::refreshAll);
        classic = new ClassicHome(ui, layout, apps, entries, widgets, drag, launcher, landing, handler, this::refreshAll,
                v -> menus.home((String) v.getTag()), id -> menus.block(id));
        widgets.setClassic(classic);
        folders = new Folders(ui, sheets, layout, apps, entries, launcher, widgets, icons, classic, this::refreshAll,
                (entry, folder) -> menus.shortcut(entry, folder));
        menus = new Menus(ui, sheets, layout, apps, launcher, launcherApps, folders, icons, classic, widgets, notices, this::refreshAll);
        media = new MediaRows(ui, apps, launcher, handler);
        list = new AppList(ui, apps, layout, entries, launcher, handler, () -> listMode);
        fab = new QuickButton(ui, handler, this);
        screen = new HomeScreen(this, prefs, handler, ui, layout, widgets, menus, media, classic, list, fab, drag, root);
        View content = screen.build();
        look = new HomeLook(this, prefs, ui, layout, widgets, media, classic, list, fab, screen, () -> listMode);
        setContentView(content);
        launcherApps.registerCallback(appsChanged, handler);
        IntentFilter pf = new IntentFilter(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        pf.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        pf.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(profiles, pf, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(profiles, pf);
        prefs.registerOnSharedPreferenceChangeListener(prefWatch);
        media.register();
        NotifService.listener = notified;
        loadApps();
        // Recreated by a language, font size or display size change (the screen is rebuilt for it): the list stays open.
        if (b != null && b.getBoolean("list")) {
            list.search.setText(b.getString("query", ""));
            showList(false);
        }
        // First run: the setup wizard (it marks itself done at its end, or when the defaults are chosen).
        if (!prefs.getBoolean("onboarded", false))
            startActivity(new Intent(this, SettingsActivity.class).putExtra("wizard", true));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("list", listMode);
        out.putString("query", list.search.getText().toString());
    }

    @Override
    protected void onDestroy() {
        clearAlphabetEdgeDrag();
        launcherApps.unregisterCallback(appsChanged);
        unregister(profiles);
        prefs.unregisterOnSharedPreferenceChangeListener(prefWatch);
        media.unregister();
        if (NotifService.listener == notified) NotifService.listener = null; // a newer home may have taken it already
        handler.removeCallbacksAndMessages(null); // queued refreshes, reloads and timeouts die with this screen
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (notificationsStale) { notificationsStale = false; refreshNotifications(); }
        widgets.start();
    }

    @Override
    protected void onStop() {
        started = false;
        widgets.stop();
        super.onStop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { ui.touchX = e.getRawX(); ui.touchY = e.getRawY(); }
        if (dispatchOppositeAlphabetEdge(e)) return true;
        screen.gestures.onTouchEvent(e);
        if (layout.classic() && screen.drawer.follow(e)) return true;
        return super.dispatchTouchEvent(e);
    }

    /** Lets the row under the new edge keep taps and sideways swipes, taking only a clearly vertical alphabet drag. */
    private boolean dispatchOppositeAlphabetEdge(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            clearAlphabetEdgeDrag();
            if (screen.alpha.isShown() && inOppositeAlphabetEdgeGap(e.getRawX()))
                alphabetEdgeDown = MotionEvent.obtain(e);
            return false;
        }
        if (alphabetEdgeDown == null) return false;
        if (!alphabetEdgeDragging && action == MotionEvent.ACTION_MOVE) {
            float dx = e.getRawX() - alphabetEdgeDown.getRawX();
            float dy = e.getRawY() - alphabetEdgeDown.getRawY();
            float slop = ui.slop();
            if (Gestures.startsDrag(dx, dy, slop)) { clearAlphabetEdgeDrag(); return false; }
            if (Gestures.startsDrag(dy, dx * 1.5f, slop)) {
                MotionEvent cancel = MotionEvent.obtain(e);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                screen.alpha.oppositeEdgeDrag = true;
                super.dispatchTouchEvent(alphabetEdgeDown);
                alphabetEdgeDragging = true;
            }
        }
        if (alphabetEdgeDragging) {
            super.dispatchTouchEvent(e);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) clearAlphabetEdgeDrag();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) clearAlphabetEdgeDrag();
        return false;
    }

    /** The opposite alphabet gesture owns a fixed strip at the window edge. */
    private boolean inOppositeAlphabetEdgeGap(float rawX) {
        View window = getWindow().getDecorView();
        Rect bounds = new Rect();
        return window.getGlobalVisibleRect(bounds) && Gestures.inOppositeEdgeGap(rawX - bounds.left, bounds.width(),
                getResources().getDisplayMetrics().density,
                screen.alpha.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
    }

    private void clearAlphabetEdgeDrag() {
        if (alphabetEdgeDown != null) alphabetEdgeDown.recycle();
        alphabetEdgeDown = null;
        alphabetEdgeDragging = false;
        if (screen != null && screen.alpha != null) screen.alpha.oppositeEdgeDrag = false;
    }

    boolean baseDispatchTouchEvent(MotionEvent e) { return super.dispatchTouchEvent(e); }

    private void applyLook() { look.apply(); }

    void fitMoves() { look.fitMoves(); }

    // ---------------------------------------------------------------- refreshing

    private void refreshAll() {
        if (isDestroyed()) return; // a background job finishing after the screen closed
        applyLook();
        list.clearCache(); // labels, icons or notifications may have changed
        landing.clear();
        boolean cl = layout.classic();
        if (cl) classic.build();
        screen.favBox.removeAllViews();
        List<String> favs = layout.favourites();
        Set<String> hidden = apps.hidden();
        List<String> shown = Favourites.shown(favs, hidden, entries::exists);
        for (int i = 0; i < shown.size(); i++) {
            String key = shown.get(i);
            View r;
            if (key.startsWith("f:")) r = entries.folderRow(key.substring(2), true);
            else if (key.startsWith("s:")) r = entries.shortcutRow(key);
            else r = entries.favouriteRow(apps.find(key));
            drag.favourite(r, key, i);
            if (!cl) landing.track(key, r); // in classic the grid slots own the keys; this column is hidden
            screen.favBox.addView(r);
        }
        List<Item> visible = apps.visible();
        if (prefs.getBoolean("suggest", false)) {
            List<String> visibleKeys = visible.stream().map(it -> it.key).collect(java.util.stream.Collectors.toList());
            for (String key : Favourites.suggested(visibleKeys, favs, prefs.getStringSet("unsuggested", Set.of()),
                    k -> apps.uses(apps.find(k)))) {
                View r = entries.appRow(apps.find(key), true);
                r.setAlpha(0.6f);
                screen.favBox.addView(r);
            }
        }
        if (screen.favBox.getChildCount() == 0) {
            TextView hint = ui.text(15, false);
            hint.setText(ui.tr("Long-press any app in the list to add it here.\nSwipe up or touch the letters to see all apps."));
            hint.setAlpha(0.8f);
            screen.favBox.addView(hint);
        }
        screen.alpha.setLetters(Search.sections(visible.stream().map(apps::label).collect(java.util.stream.Collectors.toList())));
        widgets.rebuild();
        media.updateMedia();
        media.updateMusic();
        screen.outlook.updateEvent();
        updateClock();
        list.rebuild();
    }

    private void refreshNotifications() {
        if (!isDestroyed()) entries.refreshNotifications();
    }

    private void updateClock() {
        screen.clock.invalidate();
        classic.blockClock.invalidate();
        String s = Outlook.dateLine(this, prefs, screen.clock.drawsDate());
        screen.date.setText(s);
        screen.date.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
        classic.blockDate.setText(s);
        classic.blockDate.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
        classic.blockDate.setContentDescription(screen.date.getContentDescription());
        classic.blockWeather.setText(screen.weather.getText());
        classic.blockWeather.setVisibility(screen.weather.getVisibility());
        classic.blockWeather.setContentDescription(screen.weather.getContentDescription());
    }

    private void updateWeather() {
        screen.outlook.updateWeather();
        updateClock();
    }

    // ---------------------------------------------------------------- home vs list

    void onLetter(String l) {
        if (AlphabetView.STAR.equals(l)) {
            showHome();
            return;
        }
        if (!listMode) showList(false);
        int i = list.indexOf(l);
        if (i >= 0) list.list.setSelectionFromTop(i, 0);
    }

    @Override
    public void showList(boolean typing) {
        listMode = true;
        root.setBackgroundColor(Colors.veil(prefs.getInt("dim", 60), !ui.light()));
        screen.home.setVisibility(View.GONE);
        fab.view.setVisibility(View.GONE);
        screen.alpha.setVisibility(View.VISIBLE);
        screen.alpha.showLetters = true;
        screen.alpha.invalidate();
        list.pane.setVisibility(View.VISIBLE);
        if (typing) {
            list.search.requestFocus();
            getSystemService(InputMethodManager.class).showSoftInput(list.search, InputMethodManager.SHOW_IMPLICIT);
        } else {
            list.search.clearFocus(); // browsing: no keyboard until the search field is tapped
            list.list.setSelection(0);
        }
    }

    void showHome() {
        listMode = false;
        root.setBackgroundColor(Colors.veil(prefs.getInt("dim_home", 0), !ui.light()));
        list.search.setText("");
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(list.search.getWindowToken(), 0);
        list.pane.setVisibility(View.GONE);
        screen.home.setVisibility(View.VISIBLE);
        applyLook();
        if (isResumed) enterAnim(); // not while an app just opened from here is still coming up
    }

    /** Back on the home screen after the drawer slid away: only the list's state is undone, the home stays as drawn. */
    void quietHome() {
        listMode = false;
        root.setBackgroundColor(Colors.veil(prefs.getInt("dim_home", 0), !ui.light()));
        if (!list.empty()) list.search.setText("");
        list.search.clearFocus();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(list.search.getWindowToken(), 0);
        list.pane.setVisibility(View.GONE);
        screen.home.setVisibility(View.VISIBLE);
        screen.alpha.setVisibility(View.GONE); // classic: the alphabet belongs to the drawer
        fab.show(true);
    }

    /**
     * Coming back home. From an app: nothing moves, so Android's own close animation is the only motion (a second
     * movement after it only stutters). Otherwise the favourites (or the grid's icons) glide up quickly, on the GPU.
     */
    private void enterAnim() {
        long now = SystemClock.uptimeMillis();
        if (listMode || now - lastEnter < 500 || !Anim.on()) return;
        lastEnter = now;
        if (launcher.takeLaunched()) return;
        List<View> items = new ArrayList<>();
        if (layout.classic()) items = classic.visibleIcons();
        else for (int i = 0; i < screen.favBox.getChildCount(); i++) items.add(screen.favBox.getChildAt(i));
        for (int i = 0; i < items.size(); i++) {
            Anim.enter(items.get(i), 0, ui.dp(16), Morph.stagger(i));
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home pressed on the home itself (Launcher3's test): classic goes back to page 1; from an app the page stays, for the landing.
        if (hasWindowFocus() && !listMode && (i.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0 && layout.classic()) {
            classic.stopResizing();
            classic.firstPage();
        }
        showHome();
        landing.answer(i);
    }

    @Override
    public void onBackPressed() {
        if (classic.stopResizing()) return;
        if (listMode) closeList();
    }

    /** Back home from the list: classic's drawer glides down onto the home screen, the list mode's list just goes. */
    void closeList() {
        if (layout.classic()) screen.drawer.slide(false);
        else showHome();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        // A hardware keyboard: start typing anywhere to search.
        if (!listMode && e.isPrintingKey()) {
            showList(true);
            return list.search.dispatchKeyEvent(e);
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        widgets.onResult(req, res);
    }

    /** Request code of the notification permission asked for when a break reminder is switched on. */
    static final int BREAK_PERMISSION = 7;

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == BREAK_PERMISSION && res.length > 0 && res[0] != android.content.pm.PackageManager.PERMISSION_GRANTED)
            ui.toast(ui.tr("Break reminders need the notification permission"));
        else screen.outlook.onPermissionResult(req);
    }

    void quickLock() {
        if (!prefs.getBoolean("quick_lock", false)) return;
        if (ui.haptics()) root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (!LockService.lock()) Ui.explainLock(this, null);
    }

    void expandNotifications() {
        Actions.openNotificationShade(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Becoming the home app sends Android "home": bring the setup screen straight back where it was.
        if (prefs.getBoolean("return_to_settings", false)) {
            prefs.edit().remove("return_to_settings").apply();
            // Wait for Android's own "go home" transition to end, or it lands on top of the setup again.
            handler.postDelayed(ui::settings, 700);
        }
        BreakReceiver.cancel(this); // back home: no break reminder needed
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(tick, f);
        media.resume();
        String pack = prefs.getString("icon_pack", "");
        if (!pack.equals(prefs.getString("icon_pack_loaded", ""))) {
            prefs.edit().putString("icon_pack_loaded", pack).apply();
            loadApps();
        }
        updateWeather();
        refreshAll();
        isResumed = true;
        enterAnim();
    }

    /** Unregisters a receiver whose registration may have failed (then there's nothing to undo). */
    private void unregister(BroadcastReceiver r) {
        try {
            unregisterReceiver(r);
        } catch (IllegalArgumentException notRegistered) {
            // nothing to undo
        }
    }

    @Override
    protected void onPause() {
        isResumed = false;
        unregister(tick);
        media.pause();
        notices.pause(); // also covers onDestroy, which always comes after it
        super.onPause();
    }
}
