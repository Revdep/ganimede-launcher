package org.ganimede.launcher;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Builds and owns the home screen's view tree and touch wiring. */
final class HomeScreen {
    final MainActivity activity;
    final SharedPreferences prefs;
    final Handler handler;
    final Ui ui;
    final Layout layout;
    final Widgets widgets;
    final Menus menus;
    final MediaRows media;
    final ClassicHome classic;
    final AppList list;
    final QuickButton fab;
    final DragDrop drag;
    final FrameLayout root;

    LinearLayout home, favBox;
    ScrollView favScroll;
    ClockView clock;
    TextView date, weather, eventLine;
    AlphabetView alpha;
    GestureDetector gestures;
    Outlook outlook;
    Drawer drawer;
    private boolean imeShown;

    HomeScreen(MainActivity activity, SharedPreferences prefs, Handler handler, Ui ui, Layout layout,
               Widgets widgets, Menus menus, MediaRows media, ClassicHome classic, AppList list,
               QuickButton fab, DragDrop drag, FrameLayout root) {
        this.activity = activity;
        this.prefs = prefs;
        this.handler = handler;
        this.ui = ui;
        this.layout = layout;
        this.widgets = widgets;
        this.menus = menus;
        this.media = media;
        this.classic = classic;
        this.list = list;
        this.fab = fab;
        this.drag = drag;
        this.root = root;
    }

    View build() {
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                // The status bar's room stays free even while it's hidden: when it slides back in, nothing is under it.
                int top = Math.max(s.top, ins.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top);
                v.setPadding(s.left, top, s.right, s.bottom);
                // Back on an empty search hides the keyboard: take that as "close the search" too. With text, results stay;
                // a menu or sheet taking the focus (and the keyboard) leaves the search as it is.
                boolean ime = ins.isVisible(WindowInsets.Type.ime());
                if (imeShown && !ime && activity.listMode && list.search.hasFocus() && list.empty() && root.hasWindowFocus()) handler.post(activity::closeList);
                imeShown = ime;
            } else {
                v.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                        ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
            }
            return ins;
        });

        // Home column, low on the screen so a thumb reaches everything.
        home = new LinearLayout(activity);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.BOTTOM);
        home.setPaddingRelative(ui.dp(40), 0, ui.dp(72), ui.dp(64)); // start 40dp, end 72dp, bottom 64dp
        home.setOnLongClickListener(v -> { menus.home(null); return true; });
        GestureDetector homeTaps = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { activity.quickLock(); return true; }
        });
        // A finger that moves is a swipe, never a long-press: cancel the long-press as soon as it travels.
        float slop = ui.slop();
        float[] down = new float[2];
        home.setOnTouchListener((v, e) -> {
            homeTaps.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { down[0] = e.getX(); down[1] = e.getY(); }
            else if (e.getActionMasked() == MotionEvent.ACTION_MOVE
                    && Math.hypot(e.getX() - down[0], e.getY() - down[1]) > slop) v.cancelLongPress();
            return false;
        });

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.bottomMargin = ui.dp(24);
        home.addView(widgets.strip, wlp);

        clock = new ClockView(activity);
        home.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        date = ui.text(18, true);
        weather = ui.text(18, true);
        weather.setPaddingRelative(ui.dp(14), ui.dp(4), ui.dp(8), ui.dp(4));
        line.addView(date);
        line.addView(weather);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(ui.dp(4), ui.dp(6), 0, ui.dp(6));
        home.addView(line, lp);

        eventLine = ui.text(15, false);
        eventLine.setPaddingRelative(ui.dp(4), ui.dp(2), 0, ui.dp(6));
        home.addView(eventLine);
        outlook = new Outlook(ui, handler, weather, eventLine);
        clock.setClockTapListener(ui, outlook);
        classic.clockClicks(outlook);
        date.setOnClickListener(v -> outlook.onDateTap());
        weather.setOnClickListener(v -> outlook.onWeatherTap());
        eventLine.setOnClickListener(v -> outlook.showAgenda());

        home.addView(media.media);
        home.addView(media.music);

        favBox = new LinearLayout(activity);
        favBox.setOrientation(LinearLayout.VERTICAL);
        // The favourites panel keeps its chosen height whatever it holds; past that it scrolls.
        favScroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int w, int h) {
                int max = Layout.appsHeight(prefs, getResources().getDisplayMetrics().heightPixels); // chosen in Settings
                super.onMeasure(w, Positions.fixedHeightSpec(h, max));
            }
        };
        // The panel's empty part is the home too: the list fills the panel so a long-press there reaches it
        // (a scroll view itself never reports long-presses).
        favScroll.setFillViewport(true);
        favBox.setOnLongClickListener(v -> { menus.home(null); return true; });
        favScroll.setVerticalScrollBarEnabled(false);
        favScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        favScroll.setFadingEdgeLength(ui.dp(24));
        favScroll.setVerticalFadingEdgeEnabled(true);
        favScroll.setClipChildren(false);
        favScroll.addView(favBox);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = ui.dp(12);
        home.addView(favScroll, flp);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1);
        home.addView(classic.pager, glp);
        home.addView(classic.dots, new LinearLayout.LayoutParams(-1, ui.dp(16)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = ui.dp(12);
        home.addView(classic.dock, dlp);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        home.getViewTreeObserver().addOnGlobalLayoutListener(activity::fitMoves); // after every layout: keep parts in bounds

        root.addView(list.pane, new FrameLayout.LayoutParams(-1, -1));

        alpha = new AlphabetView(activity);
        alpha.listener = activity::onLetter;
        alpha.onDoubleTap = activity::quickLock;
        root.addView(alpha, new FrameLayout.LayoutParams(-1, -1)); // full width: the wave can reach the screen centre
        FrameLayout.LayoutParams dbl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        dbl.setMargins(ui.dp(16), ui.dp(8), ui.dp(16), 0);
        root.addView(drag.bar, dbl);

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ui.dp(60), ui.dp(60), Gravity.BOTTOM | Gravity.END);
        fp.bottomMargin = ui.dp(36);
        fp.setMarginEnd(ui.dp(28));
        root.addView(fab.view, fp);

        drawer = new Drawer(ui, layout, root, home, list.pane, list.list, alpha, () -> activity.listMode, list::empty,
                () -> activity.showList(false), activity::quietHome, activity::baseDispatchTouchEvent);
        gestures = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            boolean listAtTop; // on touch-down: a downward fling then closes the list instead of scrolling it

            @Override
            public boolean onDown(MotionEvent e) {
                listAtTop = !list.list.canScrollVertically(-1);
                return false;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent e, float vx, float vy) {
                if (a == null || Math.abs(vy) < Math.abs(vx) * 1.5f) return false;
                if (drawer.dragging()) return false; // the classic drawer follows the finger instead
                if (activity.listMode) { // in the app list: a flick down anywhere (not on the letters) goes home
                    if (vy > 0 && listAtTop && !alpha.inColumn(a.getRawX())) activity.closeList();
                    return false;
                }
                // A fling that scrolls the favourites is not a "swipe up for search / down for notifications".
                Rect r = new Rect();
                favScroll.getGlobalVisibleRect(r);
                if (r.contains((int) a.getRawX(), (int) a.getRawY()) && favScroll.canScrollVertically(vy > 0 ? -1 : 1)) return false;
                if (vy > 0) activity.expandNotifications();
                else if (layout.classic()) { if (layout.drawerOn()) drawer.slide(true); } // browsing: the keyboard waits for a tap on search
                else if (prefs.getBoolean("swipe_search", true)) activity.showList(true);
                return true;
            }
        });
        return root;
    }
}
