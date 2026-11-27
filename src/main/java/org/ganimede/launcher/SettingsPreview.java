package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Favourites;
import org.ganimede.launcher.core.HomeColumn;
import org.ganimede.launcher.core.Search;
import org.ganimede.launcher.core.Grid;
import org.ganimede.launcher.core.Blocks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A miniature of the real home screen: laid out at the phone's own size with the same pieces, paddings, positions and
 * limits as MainActivity (same prefs, same Positions), then drawn shrunk over the real wallpaper. refresh() re-reads
 * the settings at once, so every change shows here as it will on the home screen.
 */
final class SettingsPreview extends FrameLayout {
    private final SharedPreferences p;
    private final float dp;
    private final int screenW, screenH;
    private final ClockView clock;
    private final TextView status, line, fab;
    /** Blocks switched on but with nothing to show (next event, media player, music apps): dashed outlines over the miniature that take no room. */
    private final TextView[] holders = new TextView[3];
    /** See-through views over the miniature that name its parts: it is hidden from accessibility, they are not (the e2e reads their bounds). */
    private final Map<View, View> marks = new LinkedHashMap<>();
    private final LinearLayout home, favs, dock, dots;
    private final PreviewGrid grid;
    private final TextView widget;
    private final FrameLayout stage;
    private final SettingsZoom zoom;
    private final AlphabetView alpha;
    private final Layout layout;
    private final Map<String, Apps.Item> all = new HashMap<>();
    private final List<Apps.Item> items = new ArrayList<>();
    private List<Apps.Item> visible = new ArrayList<>();
    private String pack;
    private boolean classic;
    /** Which page of the classic grid shows: 0 the home, 1 the first app page (drawer off). */
    private int page;
    /** Called when an icon pack finished loading, so tiles that show sample icons can redraw. */
    private Runnable onIcons;
    /** Called after each layout, once positions are fitted (Settings shows the values that really apply). */
    private Runnable afterFit;

    /** Shows this mode ("list" or "classic") whatever is chosen; null follows the setting. */
    private final String forcedMode;
    /** The layer on top that picks, moves and sizes the bricks of the home screen (off unless Settings turns it on). */
    final BrickOverlay edit;
    private final GradientDrawable frame = new GradientDrawable();
    /** After every layout, the parts are fitted exactly as on the home screen; only while shown, or it outlives the view. */
    private final ViewTreeObserver.OnGlobalLayoutListener fitter = this::fit;

    SettingsPreview(Context c) {
        this(c, 0, null);
    }

    /** heightPx: how tall the miniature is drawn (0: half the screen). */
    SettingsPreview(Context c, int heightPx, String forcedMode) {
        super(c);
        this.forcedMode = forcedMode;
        p = Prefs.of(c);
        layout = new Layout(c, p);
        dp = getResources().getDisplayMetrics().density;
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;

        stage = new FrameLayout(c);
        status = new TextView(c);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(px(40), 0, px(40), 0); // clear of the rounded corners, as a real status bar
        int[] in = insets();
        stage.addView(status, new FrameLayout.LayoutParams(-1, in[0]));
        home = new LinearLayout(c);
        home.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-1, -1);
        hl.topMargin = in[0];
        hl.bottomMargin = in[1];
        stage.addView(home, hl);
        widget = new TextView(c);
        widget.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, px(140));
        wl.bottomMargin = px(24);
        home.addView(widget, wl);
        clock = new ClockView(c);
        home.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        line = new TextView(c);
        line.setTextSize(18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(px(4), px(6), 0, px(6));
        home.addView(line, lp);
        favs = new LinearLayout(c) { // as tall as its apps, up to the chosen share of the screen (then it scrolls)
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, Positions.fixedHeightSpec(h, Layout.appsHeight(p, screenH)));
            }
        };
        favs.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = px(12);
        home.addView(favs, flp);
        grid = new PreviewGrid(c);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = px(16);
        home.addView(grid, glp);
        dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER);
        home.addView(dots, new LinearLayout.LayoutParams(-1, px(16)));
        dock = new LinearLayout(c);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPaddingRelative(px(6), px(4), px(6), px(4));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = px(12);
        home.addView(dock, dl);
        alpha = new AlphabetView(c); // the home's own alphabet, drawn with the home's own geometry
        alpha.setEnabled(false);
        alpha.setContentDescription(null);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-1, -1);
        al.topMargin = in[0];
        al.bottomMargin = in[1];
        stage.addView(alpha, al);
        for (int i = 0; i < holders.length; i++) {
            holders[i] = new TextView(c);
            holders[i].setGravity(Gravity.CENTER_VERTICAL);
            holders[i].setPadding(px(12), 0, px(12), 0);
            holders[i].setTextSize(15);
            stage.addView(holders[i], new FrameLayout.LayoutParams(-2, px(32), Gravity.TOP | Gravity.LEFT));
            marks.put(holders[i], new View(c));
        }
        marks.put(favs, new View(c));
        marks.put(clock, new View(c));
        marks.put(grid, new View(c));
        fab = new TextView(c);
        fab.setText("✳");
        fab.setTextSize(24);
        fab.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(px(60), px(60), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, px(28), px(36));
        stage.addView(fab, fp);
        edit = new BrickOverlay(c, p);
        edit.onChange = this::refresh;
        stage.addView(edit, new FrameLayout.LayoutParams(-1, -1));

        zoom = new SettingsZoom(c, stage, 0.5f, screenW, screenH);
        zoom.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius());
            }
        });
        zoom.setClipToOutline(true);
        frame.setStroke(Math.max(1, px(1.5f)), 0x77FFFFFF);
        zoom.setForeground(frame);
        setHeight(heightPx > 0 ? heightPx : screenH / 2);
        addView(zoom, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        for (View m : marks.values()) {
            m.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // out of TalkBack's way; uiautomator still reads them
            addView(m, new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
        }
        setPadding(0, px(4), 0, px(8));
        loadApps(c);
        refresh();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(fitter);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(fitter);
        afterFit = null; // a page built again (or left) has new position rows, or none
        super.onDetachedFromWindow();
    }

    void onIcons(Runnable r) { onIcons = r; }

    void afterFit(Runnable r) { afterFit = r; }

    void showPage(int page) {
        if (this.page == page) return;
        this.page = page;
        refresh();
    }

    /** The miniature uses the same whole-height cell geometry as the real classic page. */
    private final class PreviewGrid extends FrameLayout {
        PreviewGrid(Context c) { super(c); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            for (int i = 0; i < getChildCount(); i++) {
                int[] at = (int[]) getChildAt(i).getTag();
                int w = width * (at[0] + at[2]) / layout.cols() - width * at[0] / layout.cols();
                int h = height * (at[1] + at[3]) / layout.rows() - height * at[1] / layout.rows();
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left, height = bottom - top;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int[] at = (int[]) child.getTag();
                int l = width * at[0] / layout.cols(), t = height * at[1] / layout.rows();
                child.layout(l, t, width * (at[0] + at[2]) / layout.cols(),
                        height * (at[1] + at[3]) / layout.rows());
            }
        }
    }

    /** How tall the miniature is on screen: it keeps the phone's shape. */
    void setHeight(int px) {
        zoom.setScale(Math.max(0.02f, (float) px / screenH));
        edit.scale = zoom.getScaleOf();
        frame.setCornerRadius(radius());
        zoom.invalidateOutline();
    }

    /** A phone's own screen corners (about 48 dp) at the miniature's size, so the curves match what's inside. */
    private float radius() {
        return 48 * dp * zoom.getScaleOf();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private void loadApps(Context c) {
        try {
            for (Apps.Item a : Apps.named(c)) { all.put(a.key, a); items.add(a); }
        } catch (Exception ignored) {
            // No launcher service: the miniature shows no icons.
        }
    }


    private void loadPack(String want) {
        pack = want;
        Context c = getContext();
        new Thread(() -> {
            Icons.load(c, want);
            post(() -> {
                if (!want.equals(pack)) return;
                refresh();
                if (onIcons != null) onIcons.run();
            });
        }).start();
    }

    private Drawable iconOf(Apps.Item a) {
        Drawable d = Icons.fromPack(a.info.getComponentName());
        return d != null ? d : a.info.getBadgedIcon(0);
    }

    private void style(TextView t, int fg, Typeface face, boolean bold) {
        t.setTextColor(fg);
        t.setTypeface(Typeface.create(face, bold ? Typeface.BOLD : Typeface.NORMAL));
        if (fg == 0xFFFFFFFF) t.setShadowLayer(5 * dp, 0, dp, 0x66000000); else t.setShadowLayer(0, 0, 0, 0);
    }

    void refresh() {
        Context c = getContext();
        visible = Apps.visible(items, p); // once per refresh: the alphabet and the suggestions both use it
        String want = p.getString("icon_pack", "");
        if (!want.equals(pack)) loadPack(want);
        boolean dark = Ui.darkText(p);
        int fg = dark ? Colors.INK : Colors.WHITE, accent = Ui.accent(p);
        Typeface face = Ui.font(p.getString("font", "sans-serif"));
        String shape = p.getString("icon_shape", Icons.DEFAULT_SHAPE);
        String themedShape = p.getString("themed_shape", Icons.DEFAULT_THEMED_SHAPE);
        boolean wasClassic = classic;
        classic = "classic".equals(forcedMode != null ? forcedMode : p.getString("mode", "list"));
        if (classic != wasClassic || edit.bricks.length == 0) edit.bricks = Brick.of(classic);

        clock.look(p.getString("clock_style", "overlap"), Prefs.clock24(c, p), fg, accent, face);
        clock.locale = T.locale(c);
        clock.scale = p.getInt("list_clock_size", 100) / 100f;
        clock.setVisibility(classic ? GONE : VISIBLE);
        clock.requestLayout();
        clock.invalidate();

        int battery = Outlook.battery(c);
        style(status, fg, face, false);
        status.setText(Prefs.time(c, System.currentTimeMillis()) + (battery > 0 ? "        " + battery + "%" : ""));
        status.setVisibility(p.getBoolean("hide_status_bar", false) ? INVISIBLE : VISIBLE);

        StringBuilder s = new StringBuilder(Outlook.dateLine(c, p, clock.drawsDate()));
        boolean showWeather = p.getBoolean("show_weather", true);
        if (showWeather)
            s.append(s.length() > 0 ? "     " : "").append(Outlook.weatherText(c, WeatherStore.current(p), p.getBoolean("fahrenheit", false)));
        style(line, fg, face, true);
        line.setPaddingRelative(0, showWeather ? px(4) : 0, 0, showWeather ? px(4) : 0);
        line.setText(s.length() == 0 ? " " : s); // keep an otherwise empty line brick pickable
        line.setVisibility(classic ? GONE : VISIBLE);
        String[][] blocks = {{"Next event", "● "}, {"Media player", "▶  "}, {"Music apps", ""}};
        boolean[] on = {p.getBoolean("event_preview", true), p.getBoolean("media_player", true),
                p.getBoolean("music_on_headset", true) && !p.getStringSet("music_apps", java.util.Set.of()).isEmpty()};
        for (int i = 0; i < holders.length; i++) {
            style(holders[i], fg, face, false);
            holders[i].setText(blocks[i][1] + T.t(c, blocks[i][0]));
            GradientDrawable outline = new GradientDrawable();
            outline.setCornerRadius(px(8));
            outline.setStroke(px(1.5f), Colors.alpha(fg, 0x66), px(6), px(4));
            holders[i].setBackground(outline);
            holders[i].setAlpha(0.5f);
            holders[i].setVisibility(!classic && on[i] ? VISIBLE : GONE);
            marks.get(holders[i]).setContentDescription(T.t(c, "Placeholder: " + blocks[i][0]));
        }
        marks.get(favs).setContentDescription(T.t(c, "Preview: favourites"));
        marks.get(clock).setContentDescription(T.t(c, "Preview: clock"));
        marks.get(grid).setContentDescription(T.t(c, "Preview: grid"));

        // Widgets: a placeholder of the chosen height when the home screen has any.
        widget.setVisibility(classic || p.getString("stack", "").isEmpty() ? GONE : VISIBLE);
        int wh = p.getInt("list_widget_h", 0);
        widget.getLayoutParams().height = px(wh > 0 ? wh : 140);
        GradientDrawable wb = new GradientDrawable();
        wb.setCornerRadius(px(24));
        wb.setColor(0x22FFFFFF);
        wb.setStroke(px(2), Colors.alpha(fg, 0x66), px(8), px(6));
        widget.setBackground(wb);
        widget.setText(T.t(c, "Widgets")); // says what the box is, so it's clear where the widget area sits
        widget.setTextSize(15);
        widget.setTextColor(Colors.alpha(fg, 0xAA));
        boolean below = p.getBoolean("widget_below", false);
        if (!classic && below != home.indexOfChild(widget) > home.indexOfChild(clock)) {
            home.removeView(widget);
            home.addView(widget, below ? home.indexOfChild(line) + 1 : 0);
        }

        boolean labels = !p.getBoolean("hide_labels", false);
        favs.removeAllViews();
        grid.removeAllViews();
        dock.removeAllViews();
        dots.removeAllViews();
        if (classic) buildClassic(fg, accent, face, shape, themedShape, labels, dark);
        else buildList(fg, accent, face, shape, themedShape, labels);
        favs.setVisibility(classic ? GONE : VISIBLE);
        grid.setVisibility(classic ? VISIBLE : GONE);
        LinearLayout.LayoutParams gridLp = (LinearLayout.LayoutParams) grid.getLayoutParams();
        gridLp.height = classic ? 0 : -2;
        gridLp.weight = classic ? 1 : 0;
        gridLp.topMargin = classic ? 0 : px(16);
        grid.setLayoutParams(gridLp);
        if (!classic) dock.setVisibility(GONE);
        dots.setVisibility(classic && p.getInt("grid_pages", 1) > 1 ? VISIBLE : GONE);

        // Same paddings and edge distances as HomeLook.apply.
        int[] col = HomeColumn.list(p.getInt("alpha_offset", 0), p.getInt("home_bottom", 64),
                p.getInt("clock_offset", 40), p.getInt("line_offset", 40), p.getInt("apps_offset", 40));
        if (classic) {
            home.setGravity(Gravity.TOP);
            home.setPadding(px(p.getInt("classic_pad_l", 8)), px(p.getInt("classic_pad_t", 16)),
                    px(p.getInt("classic_pad_r", 8)), px(p.getInt("classic_pad_b", 16)));
        } else {
            home.setGravity(Gravity.BOTTOM);
            home.setPaddingRelative(0, 0, px(col[0]), px(col[1]));
        }
        int clockStart = classic ? 8 : col[2];
        startMargin(clock, px(clockStart));
        startMargin(line, px(classic ? clockStart : col[3]));
        startMargin(widget, px(classic ? 0 : clockStart)); // the widget strip stays aligned to the clock
        for (View v : new View[]{clock, line, widget}) v.setTranslationX(0);
        startMargin(favs, px(col[4]));

        int tint = p.getInt("alpha_tint", 0);
        alpha.color = tint == 0 ? fg : tint;
        alpha.accent = accent;
        alpha.sizeSp = p.getInt("alpha_size", 16);
        alpha.offsetDp = p.getInt("alpha_offset", 0);
        alpha.setLetters(Search.sections(visible.stream().map(ap -> Apps.label(p, ap)).collect(java.util.stream.Collectors.toList())));
        alpha.setVisibility(p.getBoolean("alpha_home", true) && !classic ? VISIBLE : GONE);
        alpha.setTranslationY(px(p.getInt("alpha_y", 0)));
        // Left-hand use mirrors the whole preview, like the home screen.
        stage.setLayoutDirection(p.getBoolean("left_handed", false) ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        stage.setBackgroundColor(Colors.veil(p.getInt("dim_home", 0), dark)); // the home wallpaper darkened as on the phone
        QuickButton.paint(fab, p, accent);
        fab.setVisibility(p.getBoolean("fab", false) ? VISIBLE : GONE);
        setContentDescription(T.t(c, page > 0 ? "Preview of your app pages" : "Preview of your home screen"));
        home.requestLayout();
        invalidate();
    }

    /** Set again on the view, or a changed start margin isn't taken up until the view is next attached. */
    private static void startMargin(View v, int px) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.setMarginStart(px);
        v.setLayoutParams(lp);
    }

    /** The status bar's and navigation bar's room, as the home screen's own insets (the system's, or its dimensions before attach). */
    private int[] insets() {
        android.content.res.Resources r = getResources();
        int top = r.getDimensionPixelSize(r.getIdentifier("status_bar_height", "dimen", "android")),
                bottom = r.getDimensionPixelSize(r.getIdentifier("navigation_bar_height", "dimen", "android"));
        WindowInsets w = getRootWindowInsets();
        if (w != null && android.os.Build.VERSION.SDK_INT >= 30) {
            top = Math.max(w.getInsets(WindowInsets.Type.systemBars()).top,
                    w.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top);
            bottom = w.getInsets(WindowInsets.Type.systemBars()).bottom;
        }
        return new int[]{top, bottom};
    }

    private void applyInsets() {
        int[] in = insets();
        for (View v : new View[]{home, alpha}) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            if (lp.topMargin == in[0] && lp.bottomMargin == in[1]) continue;
            lp.topMargin = in[0];
            lp.bottomMargin = in[1];
            v.setLayoutParams(lp);
        }
        if (status.getLayoutParams().height != in[0]) {
            status.getLayoutParams().height = in[0];
            status.requestLayout();
        }
    }


    private ImageView iconView(Apps.Item a, String shape, String themedShape, int fg, int accent, int size) {
        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Icons.apply(icon, iconOf(a), Icons.fromPack(a.info.getComponentName()) != null,
                shape, themedShape, fg, accent, size);
        return icon;
    }

    private void buildList(int fg, int accent, Typeface face, String shape, String themedShape, boolean labels) {
        Context c = getContext();
        int size = px(p.getInt("icon_size", 54)), gap = px(p.getInt("fav_gap", 8));
        List<String> favourites = layout.favourites();
        List<String> shown = Favourites.shown(favourites, p.getStringSet("hidden", java.util.Set.of()), k -> layout.entryExists(k, all::containsKey));
        List<String> faint = new ArrayList<>();
        if (p.getBoolean("suggest", false))
            faint = Favourites.suggested(visible.stream().map(ap -> ap.key).collect(java.util.stream.Collectors.toList()),
                    favourites, p.getStringSet("unsuggested", java.util.Set.of()), k -> p.getInt("use:" + k, 0));
        if (shown.isEmpty() && faint.isEmpty()) { // as the home: say how to fill the column
            TextView hint = new TextView(c);
            hint.setTextSize(15);
            style(hint, fg, face, false);
            hint.setAlpha(0.8f);
            hint.setText(T.t(c, "Long-press any app in the list to add it here.\nSwipe up or touch the letters to see all apps."));
            favs.addView(hint);
            return;
        }
        for (String k : shown) favs.addView(row(k, size, gap, fg, accent, face, shape, themedShape, labels));
        for (String k : faint) {
            View r = row(k, size, gap, fg, accent, face, shape, themedShape, labels);
            r.setAlpha(0.6f);
            favs.addView(r);
        }
    }

    private View row(String entry, int size, int gap, int fg, int accent, Typeface face, String shape, String themedShape, boolean labels) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(0, gap, 0, gap);
        Apps.Item a = all.get(entry);
        row.addView(a != null ? iconView(a, shape, themedShape, fg, accent, size) : tile(entry, size, fg),
                new LinearLayout.LayoutParams(size + px(4), size + px(2)));
        if (labels) {
            TextView name = new TextView(c);
            name.setTextSize(22 * p.getInt("label_scale", 100) / 100f);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            style(name, fg, face, true);
            name.setText(a != null ? Apps.label(p, a) : entryName(entry));
            name.setPaddingRelative(px(22), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return row;
    }

    private String entryName(String entry) {
        return entry.startsWith("s:") ? Entries.shortcutName(entry) : layout.folder(entry.substring(2)).optString("name", T.t(getContext(), "Folder"));
    }

    /** A folder's or shortcut's icon: a soft tile with its mark. */
    private View tile(String entry, int icon, int fg) {
        View v = new View(getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(icon / 3f);
        bg.setColor(Colors.alpha(Colors.LAVENDER, 0x55));
        v.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{bg,
                new android.graphics.drawable.InsetDrawable(new Glyph(entry.startsWith("f:") ? "folder" : "link", fg), icon / 4)}));
        return v;
    }

    /** The first page of the real grid (its saved cells, or the favourites before anything was placed) and the dock. */
    private void buildClassic(int fg, int accent, Typeface face, String shape, String themedShape,
                              boolean labels, boolean dark) {
        Context c = getContext();
        int cols = layout.cols(), rows = layout.rows(), icon = px(p.getInt("grid_icon", 48));
        // The app pages (drawer off) may carry their own look: the cells of the page shown, not the dock.
        boolean app = layout.appPage(page);
        int cellIcon = app ? px(layout.drawerInt("grid_icon", 48)) : icon;
        Typeface cellFace = app ? Ui.font(layout.drawerString("font", "sans-serif")) : face;
        String cellShape = app ? layout.drawerString("icon_shape", shape) : shape;
        boolean cellLabels = app ? layout.drawerLabels() : labels;
        int cellScale = app ? layout.drawerInt("label_scale", 100) : p.getInt("label_scale", 100);
        String[] cells = new String[cols * rows];
        // Before the home first lays out its grid it starts from the favourites; the preview doesn't save that.
        Map<Integer, String> saved;
        List<Blocks.Item> blocks;
        if (layout.classic()) {
            blocks = layout.blocks(); // migration, when needed, also updates the grid read next
            saved = p.contains("grid") ? layout.grid() : Grid.seed(layout.favourites(), cells.length);
        } else {
            saved = p.contains("grid") ? layout.grid() : Grid.seed(layout.favourites(), cells.length);
            String old = p.getString("stack", "");
            Blocks.Result preview = Blocks.migrate(saved, old.isEmpty() ? List.of() : java.util.Arrays.asList(old.split(",")),
                    true, layout.pages(), cols, rows);
            blocks = preview.blocks;
            saved = preview.cells;
        }
        saved = Blocks.evict(blocks, saved, layout.pages(), cols, rows).cells; // as the home: no icon under a block
        for (int i = 0; i < cells.length; i++) cells[i] = saved.getOrDefault(page * cells.length + i, "");
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) {
                View cell = cell(cells[r * cols + k], cellLabels, cellIcon, fg, accent, cellFace, cellShape, themedShape, cellScale);
                cell.setTag(new int[]{k, r, 1, 1});
                grid.addView(cell);
            }
        }
        for (Blocks.Item b : blocks) if (b.page == page) {
            View block;
            if (b.id.equals("clock")) {
                LinearLayout box = new LinearLayout(c);
                box.setOrientation(LinearLayout.VERTICAL);
                ClockView bc = new ClockView(c);
                bc.look(clock.style, clock.h24, fg, accent, face);
                bc.locale = T.locale(c);
                box.addView(bc, new LinearLayout.LayoutParams(-1, 0, 1));
                TextView bl = new TextView(c);
                style(bl, fg, face, true);
                bl.setText(line.getText());
                bl.setTextSize(18);
                box.addView(bl, new LinearLayout.LayoutParams(-1, -2));
                block = box;
            } else {
                TextView w = new TextView(c);
                w.setText(T.t(c, "Widget"));
                w.setTextSize(15);
                w.setTextColor(Colors.alpha(fg, 0xAA));
                w.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(px(24));
                bg.setColor(0x22FFFFFF);
                bg.setStroke(px(2), Colors.alpha(fg, 0x66), px(8), px(6));
                w.setBackground(bg);
                block = w;
            }
            block.setTag(new int[]{b.col, b.row, b.colSpan, b.rowSpan});
            grid.addView(block);
        }
        for (int i = 0; i < layout.pages(); i++) {
            View d = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == page ? fg : Colors.alpha(fg, 0x66));
            d.setBackground(g);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(px(7), px(7));
            l.setMargins(px(4), 0, px(4), 0);
            dots.addView(d, l);
        }
        String[] slot = layout.dockSlots();
        dock.setVisibility(slot.length == 0 ? GONE : VISIBLE);
        dock.setWeightSum(Math.max(1, slot.length));
        for (String s : slot)
            dock.addView(cell(s, false, icon, fg, accent, face, shape, themedShape), new LinearLayout.LayoutParams(0, icon + px(20), 1));
        GradientDrawable shelf = new GradientDrawable();
        shelf.setCornerRadius(px(28));
        shelf.setColor(Ui.dockColor(p, dark));
        dock.setBackground(shelf);
    }

    private View cell(String entry, boolean labels, int icon, int fg, int accent, Typeface face, String shape, String themedShape) {
        return cell(entry, labels, icon, fg, accent, face, shape, themedShape, p.getInt("label_scale", 100));
    }

    private View cell(String entry, boolean labels, int icon, int fg, int accent, Typeface face, String shape, String themedShape,
                      int labelScale) {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        String name = null;
        View iv = null;
        Apps.Item a = all.get(entry);
        if (a != null) {
            iv = iconView(a, shape, themedShape, fg, accent, icon);
            name = Apps.label(p, a);
        } else if (layout.entryExists(entry, k -> false)) { // a folder that is kept, or a shortcut
            iv = tile(entry, icon, fg);
            name = entryName(entry);
        }
        if (iv == null) { // empty slot: a faint outline, so rows, columns and dock size show even on an empty grid
            View slot = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(icon / 3f);
            g.setStroke(px(1.5f), Colors.alpha(fg, 0x40), px(5), px(4));
            slot.setBackground(g);
            box.addView(slot, new LinearLayout.LayoutParams(icon * 3 / 4, icon * 3 / 4));
            return box;
        }
        box.addView(iv, new LinearLayout.LayoutParams(icon, icon));
        if (labels && name != null) {
            TextView t = new TextView(c);
            t.setTextSize(13 * labelScale / 100f);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, px(7), 0, 0);
            style(t, fg, face, false);
            t.setText(name);
            box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        return box;
    }

    /** Positions, exactly as MainActivity.fitMoves does them. */
    private void fit() {
        applyInsets();
        if (home.getHeight() == 0) return;
        List<View[]> blocks = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        boolean below = p.getBoolean("widget_below", false);
        if (classic) {
            blocks.add(new View[]{grid}); keys.add("classic_grid_y");
            blocks.add(new View[]{dots}); keys.add(null);
            blocks.add(new View[]{dock}); keys.add("classic_dock_y");
        } else {
            Positions.listBlocks(blocks, keys, below, new View[]{widget}, new View[]{clock}, new View[]{line},
                    new View[]{favs});
        }
        List<int[]> spans = new ArrayList<>();
        for (View[] b : blocks) spans.add(span(b, true));
        Positions.fitBlocks(p, blocks, keys, spans, px(4), home.getHeight() - px(4), dp);
        int right = screenW - px(4);
        if (!classic) Positions.fitSide(p, new View[]{widget}, span(new View[]{widget}, false), "list_widget_x", px(4), right, dp);
        if (classic) {
            Positions.fitSide(p, new View[]{grid}, span(new View[]{grid}, false), "classic_grid_x", px(4), right, dp);
            Positions.fitSide(p, new View[]{dock}, span(new View[]{dock}, false), "classic_dock_x", px(4), right, dp);
        }
        // Where each brick is now, for the editing layer.
        for (Brick b : edit.bricks) {
            switch (b.id) {
                case "clock": box(b, clock); break;
                case "line": box(b, new View[]{line}); break;
                case "widget": box(b, widget); break;
                case "apps": box(b, favs); break;
                case "grid": box(b, grid); break;
                case "dock": box(b, dock); break;
                default: { // the alphabet: only as tall as its letters, in its 58 dp column
                    b.shown = alpha.getVisibility() == VISIBLE;
                    float left = alpha.columnStart(), column = px(58);
                    float top = alpha.getTop() + alpha.top() + alpha.getTranslationY();
                    b.at.set(left, top, left + column, top + alpha.height());
                }
            }
        }
        // Sideways, List mode's column ends before the alphabet; Classic's parts may go 4 dp from the screen's sides.
        edit.area.set(classic ? px(4) : Math.max(px(4), home.getPaddingLeft()), home.getTop() + px(4),
                screenW - (classic ? px(4) : Math.max(px(4), home.getPaddingRight())), home.getBottom() - px(4));
        edit.invalidate();
        placeHolders();
        if (afterFit != null) afterFit.run();
    }

    /** The favourites panel keeps its chosen height whatever it holds; the rows end where the last one does. */
    private static int contentHeight(LinearLayout panel) {
        int n = panel.getChildCount();
        return n == 0 ? panel.getHeight() : Math.min(panel.getHeight(), panel.getChildAt(n - 1).getBottom());
    }

    /** The empty blocks' outlines hang under the text line over whatever is below: they never move another part. */
    private void placeHolders() {
        float y = home.getTop() + line.getBottom() + line.getTranslationY();
        for (TextView h : holders) {
            if (h.getVisibility() != VISIBLE) continue;
            h.setTranslationX(home.getLeft() + line.getLeft());
            h.setTranslationY(y);
            y += px(36);
        }
        float s = zoom.getScaleOf();
        for (Map.Entry<View, View> e : marks.entrySet()) {
            View t = e.getKey(), m = e.getValue();
            boolean on = t.getVisibility() == VISIBLE && t.getHeight() > 0;
            m.setVisibility(on ? VISIBLE : GONE);
            if (!on) continue;
            boolean inHome = t.getParent() == home;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) m.getLayoutParams();
            int l = zoom.getLeft() + Math.round(((inHome ? home.getLeft() : 0) + t.getLeft() + t.getTranslationX()) * s),
                    tp = zoom.getTop() + Math.round(((inHome ? home.getTop() : 0) + t.getTop() + t.getTranslationY()) * s),
                    w = Math.round(t.getWidth() * s), h = Math.round((t == favs ? contentHeight(favs) : t.getHeight()) * s);
            if (lp.leftMargin == l && lp.topMargin == tp && lp.width == w && lp.height == h) continue;
            lp.leftMargin = l; lp.topMargin = tp; lp.width = w; lp.height = h;
            m.setLayoutParams(lp);
        }
    }

    private void box(Brick b, View... vs) {
        int[] h = span(vs, false), v = span(vs, true);
        b.shown = h != null && v != null;
        if (b.shown) b.at.set(h[0] + vs[0].getTranslationX(), home.getTop() + v[0] + vs[0].getTranslationY(),
                h[1] + vs[0].getTranslationX(), home.getTop() + v[1] + vs[0].getTranslationY());
    }

    private int[] span(View[] vs, boolean vertical) {
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
        for (View v : vs) {
            if (v.getVisibility() != VISIBLE || v.getHeight() == 0) continue;
            if (vertical) { a = Math.min(a, v.getTop()); b = Math.max(b, v.getBottom()); continue; }
            int l = v.getLeft(), r = v.getRight();
            if (v == clock) {
                int w = Math.round(clock.drawnWidth());
                if (clock.getLayoutDirection() == LAYOUT_DIRECTION_RTL) l = r - w; else r = l + w;
            }
            a = Math.min(a, l + home.getLeft()); b = Math.max(b, r + home.getLeft());
        }
        return a == Integer.MAX_VALUE ? null : new int[]{a, b};
    }
}
