package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** The widgets every settings page is built from, added to the page being built; the look comes from ui, read per page. */
final class SettingsKit {
    static final int CARD = 0xCC1E1A2E, GOOD = 0xFF9FD3C7, BAD = 0xFFF28B82, SUB = 0xB3FFFFFF;

    private final Activity a;
    private final SharedPreferences p;
    final Ui ui;
    final LinearLayout box, pinned;
    private final float dp;
    private final Runnable render, refresh;
    private final Consumer<Page> open;

    SettingsKit(Activity a, SharedPreferences p, Runnable render, Consumer<Page> open, Runnable refresh) {
        this.a = a;
        this.p = p;
        this.render = render;
        this.open = open;
        this.refresh = refresh;
        ui = new Ui(a, p);
        ui.read();
        dp = a.getResources().getDisplayMetrics().density;
        box = new LinearLayout(a);
        pinned = new LinearLayout(a);
    }

    /** The screen the pages are built in (views, system services, permissions, results): pages ask for it here, as a Fragment asks its host. */
    Activity activity() { return a; }

    /** The settings every page reads; single writes go through save or put. */
    SharedPreferences prefs() { return p; }

    void render() { render.run(); }

    void open(Page page) { open.accept(page); }

    /**
     * Makes a button act once on a tap and keep acting while held (arrows that move a brick, the − and + of a slider).
     * Nothing happens at the first touch: a finger that lands on it and moves on is scrolling the page.
     */
    void repeating(View button, Runnable step) {
        Runnable[] repeat = new Runnable[1];
        boolean[] held = new boolean[1];
        repeat[0] = () -> {
            held[0] = true;
            button.getParent().requestDisallowInterceptTouchEvent(true); // from here on it's a hold, not a scroll
            step.run();
            button.postDelayed(repeat[0], 60);
        };
        button.setOnClickListener(v -> step.run()); // taps from accessibility services
        button.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    held[0] = false;
                    v.setPressed(true);
                    v.postDelayed(repeat[0], 380);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    if (!held[0]) step.run();
                    // fall through
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    v.removeCallbacks(repeat[0]);
                    break;
            }
            return true;
        });
    }

    /** A round 48 dp button showing a line icon (e.g. an arrow), or the mark itself when there's no such icon. */
    FrameLayout glyphButton(String glyph, float rotation, String description) {
        TextView icon = text(Glyph.has(glyph) ? "" : glyph, 26, ui.accent);
        icon.setGravity(Gravity.CENTER);
        if (Glyph.has(glyph)) icon.setBackground(new InsetDrawable(new Glyph(glyph, ui.accent), ui.dp(9)));
        icon.setRotation(rotation);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout touch = new FrameLayout(a); // the icon itself takes no touches, so a hold reaches the button around it
        touch.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, oval(Colors.WHITE)));
        touch.addView(icon, new FrameLayout.LayoutParams(ui.dp(40), ui.dp(40), Gravity.CENTER));
        touch.setContentDescription(description);
        touch.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        return touch;
    }

    /**
     * A labelled slider for a whole-number setting, with − and + beside it for exact values (they repeat while
     * held); the preview follows at once.
     */
    void slider(LinearLayout card, String title, String key, int min, int max, int def) {
        slider(card, title, p.getInt(key, def), min, max, v -> {
            if (p.getInt(key, def) == v) return;
            p.edit().putInt(key, v).apply();
            refresh.run();
        });
    }

    /** The same control for a value stored somewhere other than SharedPreferences. */
    void slider(LinearLayout card, String title, int current, int min, int max, IntConsumer pick) {
        current = Math.max(min, Math.min(max, current));
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(ui.dp(20), ui.dp(12), ui.dp(12), ui.dp(4));
        LinearLayout head = new LinearLayout(a);
        head.setPadding(0, 0, ui.dp(8), 0);
        head.addView(text(title, 17, Colors.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(String.valueOf(current), 15, ui.accent);
        head.addView(value);
        r.addView(head);
        SeekBar bar = new SeekBar(a);
        bar.setMax(max - min);
        bar.setProgress(current - min);
        bar.setProgressTintList(ColorStateList.valueOf(ui.accent));
        bar.setThumbTintList(ColorStateList.valueOf(ui.accent));
        bar.setContentDescription(title);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                value.setText(String.valueOf(min + v));
                pick.accept(min + v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout line = new LinearLayout(a);
        line.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout less = glyphButton("−", 0, title + ": " + ui.tr("Decrease")), more = glyphButton("+", 0, title + ": " + ui.tr("Increase"));
        repeating(less, () -> bar.setProgress(bar.getProgress() - 1));
        repeating(more, () -> bar.setProgress(bar.getProgress() + 1));
        line.addView(less);
        line.addView(bar, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        line.addView(more);
        r.addView(line);
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    /** One table-size picker: touching N × M stores both columns and rows in a single change. */
    void gridPicker(LinearLayout card, String keyCols, String keyRows, int defCols, int defRows,
                    int minCols, int maxCols, int minRows, int maxRows) {
        int chosenCols = p.getInt(keyCols, defCols), chosenRows = p.getInt(keyRows, defRows);
        LinearLayout picker = new LinearLayout(a);
        picker.setOrientation(LinearLayout.VERTICAL);
        picker.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(12));
        for (int rows = minRows; rows <= maxRows; rows++) {
            LinearLayout line = new LinearLayout(a);
            for (int cols = minCols; cols <= maxCols; cols++) {
                String size = cols + " × " + rows;
                boolean on = cols == chosenCols && rows == chosenRows;
                TextView cell = text(size, 12, on ? Colors.WHITE : SUB);
                cell.setGravity(Gravity.CENTER);
                cell.setMinHeight(ui.dp(44));
                GradientDrawable bg = round(on ? Colors.alpha(ui.accent, 0x44) : 0x14FFFFFF, 10);
                if (on) bg.setStroke(ui.dp(2), ui.accent);
                cell.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
                cell.setContentDescription(size);
                selectable(cell, on);
                int c = cols, r = rows;
                cell.setOnClickListener(v -> {
                    save(keyCols, c);
                    put(keyRows, r);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(48), 1);
                lp.setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
                line.addView(cell, lp);
            }
            picker.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        card.addView(picker, new LinearLayout.LayoutParams(-1, -2));
    }

    String[] tr(String[] en) {
        String[] out = new String[en.length];
        for (int i = 0; i < en.length; i++) out[i] = ui.tr(en[i]);
        return out;
    }

    TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * dp);
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Small-caps section label in the accent colour. */
    void label(String en) {
        TextView t = text(ui.tr(en).toUpperCase(T.locale(a)), 13, ui.accent);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(ui.dp(8), ui.dp(16), ui.dp(8), ui.dp(8));
        if (Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
        box.addView(t);
    }

    LinearLayout card(String label) {
        label(label);
        return card();
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 24));
        c.setClipToOutline(true);
        c.setPadding(0, ui.dp(6), 0, ui.dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = ui.dp(12);
        box.addView(c, lp);
        return c;
    }

    TextView para(LinearLayout card, String s, float sp, int color) {
        TextView t = text(s, sp, color);
        t.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10));
        t.setLineSpacing(0, 1.15f);
        card.addView(t);
        return t;
    }

    /** A heading inside a card. */
    void sub(LinearLayout card, String s) {
        TextView t = text(s, 15, Colors.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(6));
        card.addView(t);
    }

    LinearLayout row(LinearLayout card, String glyph, String title, String summary, Runnable click) {
        return row(card, glyph, ui.accent, text(title, 17, Colors.WHITE), summary(summary), click);
    }

    /** The line under a row's title, or null when there is nothing to say. */
    TextView summary(String s) {
        if (s == null || s.isEmpty()) return null;
        TextView t = text(s, 14, SUB);
        t.setPadding(0, ui.dp(2), 0, 0);
        return t;
    }

    /** A row from its parts: a glyph in its colour (or none), the title and the summary (or null) under it. */
    LinearLayout row(LinearLayout card, String glyph, int color, TextView title, TextView summary, Runnable click) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(ui.dp(56));
        r.setPadding(ui.dp(20), ui.dp(12), ui.dp(16), ui.dp(12));
        if (glyph != null) {
            boolean icon = Glyph.has(glyph);
            TextView mark = text(icon ? "" : glyph, 18, color);
            mark.setGravity(Gravity.CENTER);
            // Line icons stand on their own (no circle behind them); text marks like ✓ keep their soft disc.
            mark.setBackground(icon ? new InsetDrawable(new Glyph(glyph, color), ui.dp(7))
                    : oval(Colors.alpha(color, 0x2E)));
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
            g.rightMargin = ui.dp(16);
            r.addView(mark, g);
        }
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(title);
        if (summary != null) col.addView(summary);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        r.setContentDescription(summary == null ? title.getText() : title.getText() + ". " + summary.getText());
        if (click != null) {
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, new ColorDrawable(Colors.WHITE)));
            r.setOnClickListener(v -> click.run());
        }
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    /** A row that leads to another screen: the same, with a › at its end. */
    void link(LinearLayout card, String glyph, String title, String summary, Runnable click) {
        row(card, glyph, title, summary, click).addView(text("›", 26, SUB));
    }

    /** A row with a switch on the right; the whole row toggles. */
    void toggle(LinearLayout card, String title, String summary, String key, boolean def, Runnable after) {
        Switch sw = new Switch(a);
        sw.setChecked(p.getBoolean(key, def));
        sw.setClickable(false);
        sw.setFocusable(false);
        sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ui.accent, 0xFFCFCBD8}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{Colors.alpha(ui.accent, 0x88), 0x55FFFFFF}));
        LinearLayout r = row(card, null, title, summary, () -> {
            sw.setChecked(!sw.isChecked());
            p.edit().putBoolean(key, sw.isChecked()).apply();
            if (after != null) after.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = ui.dp(12);
        r.addView(sw, lp);
        r.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(sw.isChecked());
            }
        });
    }

    /** Single choice as rows with a check mark; faces/subs optional (each row drawn in its own typeface). */
    void radios(LinearLayout card, String[] names, int sel, Typeface[] faces, String[] subs, IntConsumer pick) {
        for (int i = 0; i < names.length; i++) {
            int k = i;
            TextView name = text(names[i], 17, Colors.WHITE), sub = summary(subs == null ? null : subs[i]);
            if (faces != null) {
                name.setTypeface(faces[i]);
                if (sub != null) sub.setTypeface(faces[i]);
            }
            LinearLayout r = row(card, null, 0, name, sub, () -> pick.accept(k));
            TextView check = text("✓", 20, ui.accent);
            check.setVisibility(i == sel ? View.VISIBLE : View.INVISIBLE);
            r.addView(check);
            selectable(r, i == sel);
        }
    }

    /** Single choice as a sideways strip of tiles, each with a small picture of the choice. */
    void tiles(LinearLayout card, int n, int sel, String[] names, IntFunction<View> content, IntConsumer pick) {
        tiles(card, n, sel, names, names, content, pick);
    }

    /** As above, with a fuller accessible name when adjacent groups reuse the same visible labels. */
    void tiles(LinearLayout card, int n, int sel, String[] names, String[] descriptions,
               IntFunction<View> content, IntConsumer pick) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(a);
        strip.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(12));
        hs.addView(strip);
        View chosen = null;
        for (int i = 0; i < n; i++) {
            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(8));
            tile.setMinimumWidth(ui.dp(84));
            tile.setMinimumHeight(ui.dp(48));
            GradientDrawable g = round(i == sel ? Colors.alpha(ui.accent, 0x33) : 0x14FFFFFF, 18);
            if (i == sel) g.setStroke(ui.dp(2), ui.accent);
            tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
            View v = content == null ? null : content.apply(i);
            if (v != null) tile.addView(v);
            TextView name = text((i == sel ? "✓ " : "") + names[i], 13, i == sel ? Colors.WHITE : SUB);
            name.setGravity(Gravity.CENTER);
            name.setMaxWidth(ui.dp(120));
            name.setMaxLines(2);
            name.setPadding(0, v == null ? ui.dp(4) : ui.dp(6), 0, v == null ? ui.dp(4) : 0);
            tile.addView(name);
            tile.setContentDescription(descriptions[i]);
            selectable(tile, i == sel);
            int k = i;
            tile.setOnClickListener(x -> pick.accept(k));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(ui.dp(4), 0, ui.dp(4), 0);
            strip.addView(tile, lp);
            if (i == sel) chosen = tile;
        }
        card.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        View ch = chosen;
        if (ch != null) afterLayout(hs, () -> hs.scrollTo(Math.max(0, ch.getLeft() - ui.dp(40)), 0));
    }

    static void selectable(View v, boolean on) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(on);
            }
        });
    }

    /** The language, as rows (the settings and the wizard's first step). */
    void languages(LinearLayout card) { radios(card, T.LANG_NAMES, Math.max(0, indexOf(T.LANGS, p.getString("lang", ""))), null, null, i -> put("lang", T.LANGS[i])); }

    /** A permission row: ✓ when allowed, otherwise a clear "Allow" button. */
    void perm(LinearLayout card, boolean ok, String title, String why, Runnable fix) {
        LinearLayout r = row(card, ok ? "✓" : "!", ok ? GOOD : BAD, text(title, 17, Colors.WHITE), summary(ok ? ui.tr("Allowed") : why), ok ? null : fix);
        if (!ok) {
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
            pl.setMarginStart(ui.dp(12));
            r.addView(pill(ui.tr("Allow"), fix), pl);
        }
    }

    TextView pill(String s, Runnable click) {
        // Tonal button, like the rest of the cards: tinted fill and accent text, no loud solid block.
        // A small capsule: compact medium text, soft fill, no outline; still a 48dp touch target around it.
        TextView b = text(s, 13, ui.accent);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setLetterSpacing(0.04f);
        b.setIncludeFontPadding(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(ui.dp(48));
        GradientDrawable tonal = round(Colors.alpha(ui.accent, 0x33), 17);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Colors.alpha(ui.accent, 0x44)),
                new InsetDrawable(tonal, 0, ui.dp(7), 0, ui.dp(7)), null));
        b.setPadding(ui.dp(18), 0, ui.dp(18), 0); // after the background, which would reset it to the inset's padding
        b.setContentDescription(s);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    static void afterLayout(View v, Runnable r) {
        v.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
    }

    /** Saves a setting as its own type and shows the page again. */
    void put(String key, Object value) {
        save(key, value);
        render.run();
    }

    /** Saves a setting as its own type (null removes it), leaving the page as it is. */
    void save(String key, Object value) {
        SharedPreferences.Editor e = p.edit();
        Prefs.put(e, key, value);
        e.apply();
    }

    static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    String time(long ms) {
        return Prefs.time(a, ms);
    }

    /** Goes to the home screen and closes settings: at once, or after delay ms. */
    void home(int delay) {
        a.startActivity(new Intent(a, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        if (delay == 0) a.finish(); else box.postDelayed(a::finish, delay);
    }
}
