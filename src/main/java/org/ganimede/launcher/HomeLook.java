package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.HomeColumn;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Applies home-screen appearance and keeps independently positioned parts in bounds. */
final class HomeLook {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Ui ui;
    private final Layout layout;
    private final Widgets widgets;
    private final MediaRows media;
    private final ClassicHome classic;
    private final AppList list;
    private final QuickButton fab;
    private final HomeScreen screen;
    private final BooleanSupplier listMode;

    HomeLook(Activity activity, SharedPreferences prefs, Ui ui, Layout layout, Widgets widgets, MediaRows media,
             ClassicHome classic, AppList list, QuickButton fab, HomeScreen screen, BooleanSupplier listMode) {
        this.activity = activity;
        this.prefs = prefs;
        this.ui = ui;
        this.layout = layout;
        this.widgets = widgets;
        this.media = media;
        this.classic = classic;
        this.list = list;
        this.fab = fab;
        this.screen = screen;
        this.listMode = listMode;
    }

    void apply() {
        ui.read();
        int fg = ui.fg;
        screen.clock.look(prefs.getString("clock_style", "overlap"), Prefs.clock24(activity, prefs), fg, ui.accent, ui.face);
        screen.clock.locale = T.locale(activity);
        screen.clock.scale = prefs.getInt("list_clock_size", 100) / 100f;
        screen.clock.setContentDescription(ui.tr("Clock, tap for alarms"));
        screen.clock.requestLayout();
        screen.clock.invalidate();
        classic.blockClock.look(screen.clock.style, screen.clock.h24, fg, ui.accent, ui.face);
        classic.blockClock.locale = screen.clock.locale;
        classic.blockClock.scale = 1;
        classic.blockClock.setContentDescription(ui.tr("Clock, tap for alarms"));
        classic.blockClock.requestLayout();
        classic.blockClock.invalidate();
        for (TextView t : new TextView[]{screen.date, screen.weather, screen.eventLine, classic.blockDate, classic.blockWeather}) {
            t.setTextColor(fg);
            t.setTypeface(Typeface.create(ui.face, t == screen.eventLine ? Typeface.NORMAL : Typeface.BOLD));
            if (ui.light()) t.setShadowLayer(ui.dp(5), 0, ui.dp(1), 0x66000000); else t.setShadowLayer(0, 0, 0, 0);
        }
        list.search.setTextColor(fg);
        list.search.setHint(ui.tr("Search apps, contacts, shortcuts…")); // follows a language change at once
        list.search.setHintTextColor(Colors.alpha(fg, 0xAA));
        screen.alpha.accent = ui.accent;
        screen.alpha.haptics = ui.haptics();
        // Always there to catch touches inside its column; hidden letters only appear while touched.
        screen.alpha.setVisibility(View.VISIBLE);
        screen.alpha.showLetters = listMode.getAsBoolean() || prefs.getBoolean("alpha_home", true);
        int tint = prefs.getInt("alpha_tint", 0);
        screen.alpha.color = tint == 0 ? fg : tint;
        screen.alpha.sizeSp = prefs.getInt("alpha_size", 16);
        screen.alpha.offsetDp = prefs.getInt("alpha_offset", 0);
        screen.alpha.requestLayout();
        screen.alpha.invalidate();
        // Left-hand use: the whole screen mirrored (alphabet on the left, everything aligned right).
        screen.root.setLayoutDirection(prefs.getBoolean("left_handed", false) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        boolean cl = layout.classic();
        View[] clockViews = {screen.clock};
        View[] lineViews = lineViews();
        widgets.applyHeight(prefs.getInt("list_widget_h", 0));
        // Every part moves on its own, in steps set in Settings (Position): x/y in dp, per mode.
        View strip = widgets.strip;
        boolean below = prefs.getBoolean("widget_below", false); // the widget area above the clock and line
        if (!cl) {
            for (View v : clockViews) move(v, 0, prefs.getInt("list_clock_y", 0));
            for (View v : lineViews) move(v, 0, prefs.getInt("list_line_y", 0));
            move(strip, prefs.getInt("list_widget_x", 0), prefs.getInt("list_widget_y", 0));
            if (below != screen.home.indexOfChild(strip) > screen.home.indexOfChild(screen.clock)) {
                screen.home.removeView(strip);
                screen.home.addView(strip, below ? screen.home.indexOfChild(media.music) + 1 : 0);
            }
        }
        if (cl) {
            // Classic blocks own the clock and widgets, so only the grid and dock consume column height.
            screen.home.setGravity(Gravity.TOP);
            screen.home.setPadding(ui.dp(prefs.getInt("classic_pad_l", 8)), ui.dp(prefs.getInt("classic_pad_t", 16)),
                    ui.dp(prefs.getInt("classic_pad_r", 8)), ui.dp(prefs.getInt("classic_pad_b", 16)));
            for (View v : clockViews) v.setVisibility(View.GONE);
            for (View v : lineViews) v.setVisibility(View.GONE);
            strip.setVisibility(View.GONE);
            screen.favScroll.setVisibility(View.GONE);
            classic.pager.setVisibility(View.VISIBLE);
            move(classic.pager, prefs.getInt("classic_grid_x", 0), prefs.getInt("classic_grid_y", 0));
            move(classic.dock, prefs.getInt("classic_dock_x", 0), prefs.getInt("classic_dock_y", 0));
            if (!listMode.getAsBoolean()) screen.alpha.setVisibility(View.GONE); // the drawer has the alphabet; the home grid doesn't
        } else {
            // List: the clock, text line and app column keep their own distance from the edge (never under the
            // alphabet: the column stops before it), and the whole home can be lifted from the bottom.
            screen.home.setGravity(Gravity.BOTTOM);
            int[] col = HomeColumn.list(prefs.getInt("alpha_offset", 0), prefs.getInt("home_bottom", 64),
                    prefs.getInt("clock_offset", 40), prefs.getInt("line_offset", 40), prefs.getInt("apps_offset", 40));
            screen.home.setPaddingRelative(0, 0, ui.dp(col[0]), ui.dp(col[1]));
            int clockX = col[2], lineX = col[3], appsX = col[4];
            for (View v : clockViews) { setStartMargin(v, ui.dp(clockX)); v.setTranslationX(0); }
            for (View v : lineViews) { setStartMargin(v, ui.dp(lineX)); v.setTranslationX(0); }
            ((View) screen.date.getParent()).setVisibility(View.VISIBLE);
            // Widgets deliberately continue to line up with the clock, independently of the text line.
            setStartMargin(strip, ui.dp(clockX));
            screen.clock.setVisibility(View.VISIBLE);
            setStartMargin(screen.favScroll, ui.dp(appsX));
            move(screen.favScroll, 0, prefs.getInt("list_apps_y", 0));
            screen.favScroll.setVisibility(View.VISIBLE);
            classic.pager.setVisibility(View.GONE);
            classic.dots.setVisibility(View.GONE);
            classic.dock.setVisibility(View.GONE);
        }
        screen.alpha.setTranslationY(ui.dp(prefs.getInt("alpha_y", 0)));
        screen.home.post(this::fitMoves);
        list.pane.setPaddingRelative(ui.dp(Gestures.OPPOSITE_EDGE_STRIP_DP), ui.dp(16),
                ui.dp(58 + prefs.getInt("alpha_offset", 0) + 6), 0);
        fab.style(!listMode.getAsBoolean());
        activity.setRequestedOrientation(prefs.getBoolean("rotate", false)
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        boolean hideBar = prefs.getBoolean("hide_status_bar", false);
        if (Build.VERSION.SDK_INT >= 30 && activity.getWindow().getInsetsController() != null) {
            if (hideBar) activity.getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
            else activity.getWindow().getInsetsController().show(WindowInsets.Type.statusBars());
        }
    }

    private View[] lineViews() {
        return new View[]{(View) screen.date.getParent(), screen.eventLine, media.media, media.music};
    }

    /** Keeps every moved part on screen and off its neighbours, saving back what could really move. */
    void fitMoves() {
        if (screen.home.getHeight() == 0 || listMode.getAsBoolean()) return;
        boolean cl = layout.classic();
        View[] clockViews = {screen.clock};
        View[] lineViews = lineViews();
        boolean below = prefs.getBoolean("widget_below", false);
        List<View[]> blocks = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        if (cl) {
            blocks.add(new View[]{classic.pager}); keys.add("classic_grid_y");
            blocks.add(new View[]{classic.dots}); keys.add(null); // stays put; nothing may cover it
            blocks.add(new View[]{classic.dock}); keys.add("classic_dock_y");
        } else {
            Positions.listBlocks(blocks, keys, below, new View[]{widgets.strip}, clockViews, lineViews,
                    new View[]{screen.favScroll});
        }
        List<int[]> spans = new ArrayList<>();
        for (View[] b : blocks) spans.add(span(b, true));
        float density = activity.getResources().getDisplayMetrics().density;
        Positions.fitBlocks(prefs, blocks, keys, spans, ui.dp(4), screen.home.getHeight() - ui.dp(4), density); // same rules as the preview
        // Sideways: the clock drawing, the widgets, the grid and the dock stay 4 dp inside the screen too.
        int right = screen.root.getWidth() - screen.root.getPaddingRight() - ui.dp(4);
        if (!cl) side(new View[]{widgets.strip}, "list_widget_x", right, density);
        if (cl) { side(new View[]{classic.pager}, "classic_grid_x", right, density); side(new View[]{classic.dock}, "classic_dock_x", right, density); }
    }

    private void side(View[] vs, String key, int right, float density) {
        int[] sp = span(vs, false);
        if (sp != null) Positions.fitSide(prefs, vs, new int[]{sp[0] + screen.home.getLeft(), sp[1] + screen.home.getLeft()}, key, ui.dp(4), right, density);
    }

    /** Where a group sits in the home column; null if none of it shows. */
    private int[] span(View[] vs, boolean vertical) {
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
        for (View v : vs) {
            if (v.getVisibility() != View.VISIBLE || v.getHeight() == 0) continue;
            if (vertical) {
                a = Math.min(a, v.getTop()); b = Math.max(b, v == classic.pager ? classic.gridBottom() : v.getBottom());
                continue;
            }
            int l = v.getLeft(), r = v.getRight();
            if (v == screen.clock) { // the view is as wide as the column; the drawing isn't
                int w = Math.round(screen.clock.drawnWidth());
                if (screen.clock.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) l = r - w; else r = l + w;
            } else if (v == screen.eventLine || v == media.media || v == media.music) continue; // text rows that end where they end
            a = Math.min(a, l); b = Math.max(b, r);
        }
        return a == Integer.MAX_VALUE ? null : new int[]{a, b};
    }

    private void move(View v, int xDp, int yDp) {
        v.setTranslationX(ui.dp(xDp));
        v.setTranslationY(ui.dp(yDp));
    }

    private static void setStartMargin(View v, int px) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (lp == null || lp.getMarginStart() == px) return;
        lp.setMarginStart(px);
        v.setLayoutParams(lp);
    }
}
