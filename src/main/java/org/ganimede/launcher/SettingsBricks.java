package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.FolderLook;
import org.ganimede.launcher.core.History;
import org.ganimede.launcher.core.Preferences;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Customize page: its pills select the whole screen, one home brick, the theme, the clock or the drawer.
 */
final class SettingsBricks {
    private static final Set<String> KEYS = Preferences.UNDO_KEYS;
    private static final int STEP = 4; // dp per arrow press

    private final SettingsKit kit;
    private final SettingsPerms perms;
    private final SettingsLook look;
    private final SettingsSamples samples;
    private final SettingsPreview preview;
    private final TextView undo; // in the top bar, shown while there is something to undo
    /** Undo for this page, while it is open: its settings as they were before each gesture. */
    private History<Map<String, Object>> history;
    private final SharedPreferences.OnSharedPreferenceChangeListener watch = (sp, k) -> {
        if (history == null || !KEYS.contains(k)) return;
        history.changed(now(), SystemClock.uptimeMillis());
        showUndo();
    };
    /** The brick whose controls show (a Brick id), or Brick.SCREEN. */
    private String picked = Brick.SCREEN;

    SettingsBricks(SettingsKit kit, SettingsPerms perms, SettingsLook look, SettingsSamples samples, SettingsPreview preview, TextView undo) {
        this.kit = kit;
        this.perms = perms;
        this.look = look;
        this.samples = samples;
        this.preview = preview;
        this.undo = undo;
        kit.prefs().registerOnSharedPreferenceChangeListener(watch);
    }

    void stop() {
        kit.prefs().unregisterOnSharedPreferenceChangeListener(watch);
    }

    /** The undo steps, for the screen Android builds in place of this one (rotation); null while the page is closed. */
    History<Map<String, Object>> history() { return history; }

    /** Takes over the undo steps of the screen this one replaces. */
    void adopt(History<Map<String, Object>> steps) { history = steps; }

    /** Undo covers what is done on this page, from the moment it opens. */
    void track(boolean open) {
        if (!open) history = null;
        else if (history == null) history = new History<>(now(), 30, 700);
        undo.setText(kit.ui.tr("Undo"));
        undo.setContentDescription(kit.ui.tr("Undo"));
        showUndo();
    }

    void undo() {
        if (history == null || !history.canUndo()) return;
        Map<String, Object> was = history.undo(SystemClock.uptimeMillis());
        SharedPreferences.Editor e = kit.prefs().edit();
        for (String k : KEYS) if (was.containsKey(k)) Prefs.put(e, k, was.get(k)); else e.remove(k);
        e.apply();
        kit.render();
    }

    private void showUndo() {
        undo.setVisibility(history != null && history.canUndo() ? View.VISIBLE : View.GONE);
    }

    private Map<String, Object> now() {
        Map<String, Object> m = new HashMap<>(kit.prefs().getAll());
        m.keySet().retainAll(KEYS);
        return m;
    }

    /** Shows this brick's controls from the next time the page is built. */
    void pick(String brick) { picked = brick; }

    String picked() { return picked; }

    void page() {
        Layout layout = new Layout(kit.activity(), kit.prefs());
        boolean classic = layout.classic();
        Brick[] all = preview.edit.bricks; // the preview's own, so each knows where it is drawn
        List<String[]> pills = pills(all, classic, classic && !layout.drawerOn());
        if (pills.stream().noneMatch(p -> p[0].equals(picked))) picked = Brick.SCREEN; // a pill of the other mode, or gone
        Brick b = Brick.find(all, picked);
        boolean themes = picked.equals("look"), drawer = picked.equals("drawer"), classicClock = classic && picked.equals("clock");
        boolean folders = picked.equals("folders");
        boolean special = themes || folders || drawer || classicClock;
        preview.showPage(drawer ? 1 : 0);
        preview.edit.mode = special ? BrickOverlay.OFF : BrickOverlay.EDIT;
        preview.edit.selected = special ? Brick.SCREEN : picked;
        chips(pills);
        if (themes) { look.themes(); return; }
        if (folders) { look.folders(); return; }
        if (classicClock) { clockAppearance(); return; }
        if (drawer) { drawer(layout); return; }
        if (b == null) { screen(classic); return; }
        switch (b.id) {
            case "clock": clock(b); break;
            case "line": line(b); break;
            case "widget": widget(b); break;
            case "apps": apps(b); break;
            case "alpha": alphabet(b); break;
            case "grid": grid(b); break;
            default: dock(b);
        }
    }

    /** What the strip offers: the screen, the bricks, then the themes, in Classic the clock, and the app pages when the drawer is off. */
    private static List<String[]> pills(Brick[] all, boolean classic, boolean drawerPage) {
        List<String[]> pills = new ArrayList<>();
        pills.add(new String[]{Brick.SCREEN, "Whole screen"});
        for (Brick b : all) pills.add(new String[]{b.id, b.title});
        pills.add(new String[]{"look", "Themes"});
        pills.add(new String[]{"folders", "Folders"});
        if (classic) pills.add(new String[]{"clock", "Clock"});
        if (drawerPage) pills.add(new String[]{"drawer", "Drawer"});
        return pills;
    }

    /** The pills by name, always in view under the preview: another way to pick one. */
    private void chips(List<String[]> pills) {
        HorizontalScrollView hs = new HorizontalScrollView(kit.activity());
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(kit.activity());
        strip.setPadding(kit.ui.dp(12), kit.ui.dp(8), kit.ui.dp(12), kit.ui.dp(8));
        hs.addView(strip);
        for (String[] pill : pills) {
            String id = pill[0], title = pill[1];
            String name = kit.ui.tr(title);
            boolean on = id.equals(picked);
            TextView chip = kit.text(name, 15, on ? Colors.WHITE : SettingsKit.SUB);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(kit.ui.dp(48));
            chip.setPadding(kit.ui.dp(16), 0, kit.ui.dp(16), 0);
            GradientDrawable g = kit.round(on ? Colors.alpha(kit.ui.accent, 0x44) : 0x1FFFFFFF, 24);
            if (on) g.setStroke(kit.ui.dp(2), kit.ui.accent);
            chip.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
            SettingsKit.selectable(chip, on);
            chip.setOnClickListener(v -> { picked = id; kit.render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(kit.ui.dp(4), 0, kit.ui.dp(4), 0);
            strip.addView(chip, lp);
            if (on) hs.post(() -> hs.scrollTo(Math.max(0, chip.getLeft() - kit.ui.dp(40)), 0)); // the picked one in view
        }
        kit.pinned.addView(hs, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---- one brick each

    private void clock(Brick b) {
        LinearLayout c = kit.card("Position and size");
        position(c, b);
        kit.sub(c, kit.ui.tr("Line it up"));
        kit.tiles(c, 3, -1, kit.tr(new String[]{"Left", "Centre", "Right"}), null, i -> align(b, i));
        kit.slider(c, kit.ui.tr("Clock size (%)"), b.ksize, b.smin, b.smax, b.sdef);
        c = kit.card("Inside this brick");
        look.clockChoices(c);
    }

    private void line(Brick b) {
        position(kit.card("Position and size"), b);
        LinearLayout c = kit.card("Inside this brick");
        outlook(c);
        kit.toggle(c, kit.ui.tr("Show the next event on the home screen"), null, "event_preview", true, null);
        perms.mediaPlayerToggle(c);
    }

    private void widget(Brick b) {
        LinearLayout c = kit.card("Position and size");
        if (!b.shown) kit.para(c, kit.ui.tr("No widgets yet. Long-press an empty part of the home screen and choose “Add widget”; then move and size them here."), 14, SettingsKit.SUB);
        position(c, b);
        kit.slider(c, kit.ui.tr("Widget height (0 = automatic)"), b.ksize, b.smin, b.smax, b.sdef);
        c = kit.card("Inside this brick");
        kit.toggle(c, kit.ui.tr("Widgets below the clock"), kit.ui.tr("Off: the widget area sits above the clock"), "widget_below", false, kit::render);
        kit.para(c, kit.ui.tr("Widgets, widget stacks and pop-up folders: long-press an empty part of the home screen."), 14, SettingsKit.SUB);
    }

    private void apps(Brick b) {
        LinearLayout c = kit.card("Position and size");
        position(c, b);
        kit.slider(c, kit.ui.tr("Icon size"), b.ksize, b.smin, b.smax, b.sdef);
        kit.para(c, kit.ui.tr("Apps move sideways up to the alphabet, never under it."), 14, SettingsKit.SUB);
        c = kit.card("Inside this brick");
        kit.slider(c, kit.ui.tr("Space between apps"), "fav_gap", 0, 32, 8);
        labels(c);
        kit.slider(c, kit.ui.tr("Apps column: most of the screen it takes (%)"), "list_apps_h", 20, 95, 58);
    }

    private void alphabet(Brick b) {
        LinearLayout c = kit.card();
        kit.toggle(c, kit.ui.tr("Show the alphabet"), kit.ui.tr("Touch the letters to jump through your apps"), "alpha_home", true, kit::render);
        c = kit.card("Position and size");
        position(c, b);
        kit.slider(c, kit.ui.tr("Letter size"), b.ksize, b.smin, b.smax, b.sdef);
        c = kit.card("Inside this brick");
        kit.sub(c, kit.ui.tr("Letter colour"));
        int[] accents = SettingsLook.accents(), tints = new int[accents.length + 1];
        String[] accentNames = SettingsLook.accentNames(), names = new String[tints.length];
        names[0] = kit.ui.tr("Same as the text");
        for (int i = 1; i < tints.length; i++) { tints[i] = accents[i - 1]; names[i] = kit.ui.tr(accentNames[i - 1]); }
        kit.tiles(c, tints.length, SettingsKit.indexOf(tints, kit.prefs().getInt("alpha_tint", 0)), names,
                i -> i == 0 ? samples.aa(!kit.ui.light(), kit.ui.face) : samples.swatch(tints[i]), i -> kit.put("alpha_tint", tints[i]));
        kit.para(c, kit.ui.tr("The alphabet answers touches inside its column, even when hidden. The very edge stays free for Back."), 14, SettingsKit.SUB);
    }

    private void grid(Brick b) {
        LinearLayout c = kit.card("Position and size");
        position(c, b);
        kit.slider(c, kit.ui.tr("Left edge"), "classic_pad_l", 4, 80, 8);
        kit.slider(c, kit.ui.tr("Right edge"), "classic_pad_r", 4, 80, 8);
        kit.slider(c, kit.ui.tr("Top edge"), "classic_pad_t", 4, 200, 16);
        kit.slider(c, kit.ui.tr("Bottom edge"), "classic_pad_b", 4, 200, 16);
        c = kit.card("Inside this brick");
        gridPicker(c);
        kit.slider(c, kit.ui.tr("Home screen pages"), "grid_pages", 1, 12, 1);
        kit.slider(c, kit.ui.tr("Icon size"), "grid_icon", 32, 80, 48);
        labels(c);
        kit.para(c, kit.ui.tr("Grid sizes start from a size that suits your screen. Widgets and the clock occupy cells too; long-press them to move, resize or remove them."), 14, SettingsKit.SUB);
    }

    private void dock(Brick b) {
        position(kit.card("Position and size"), b);
        LinearLayout c = kit.card("Inside this brick");
        kit.slider(c, kit.ui.tr("Dock icons"), "dock_count", 0, 7, Layout.gridDefaults(kit.activity())[2]);
        kit.slider(c, kit.ui.tr("Dock background (%, 0 = transparent)"), "dock_alpha", 0, 100, Ui.dockAlpha(kit.prefs()));
    }

    /** No brick picked: the mode, and what belongs to the whole screen. */
    private void screen(boolean classic) {
        LinearLayout c = kit.card();
        kit.para(c, kit.ui.tr(classic ? "Your home screen is made of bricks. Icons, widgets and the clock are arranged on the home screen itself; here you set the grid they sit on and the dock: touch one in the preview."
                : "Your home screen is made of bricks. Touch one in the preview to pick it, drag it to move it, pull its round knob to size it."), 14, SettingsKit.SUB);
        if (classic) kit.link(c, "grid", kit.ui.tr("Arrange icons, widgets and the clock"),
                kit.ui.tr("Right on the home screen: long-press one to move it, resize it or remove it"), this::editOnHome);
        c = kit.card("Mode");
        String[] modes = {"list", "classic"};
        kit.tiles(c, 2, classic ? 1 : 0, kit.tr(new String[]{"List", "Classic"}), null, i -> kit.put("mode", modes[i]));
        kit.para(c, kit.ui.tr(classic ? "A grid of icons, a dock and an app drawer" : "Favourites and an A–Z list, like a minimalist launcher"), 14, SettingsKit.SUB);
        c = kit.card("Whole screen");
        if (classic) outlook(c);
        else kit.slider(c, kit.ui.tr("Bottom edge: lift everything"), "home_bottom", 4, 420, 64);
        kit.link(c, "button", kit.ui.tr("Ganimede button"), kit.ui.tr("A round button in the bottom corner with its own pop-up"), () -> kit.open(Page.BUTTON));
        kit.toggle(c, kit.ui.tr("Left-hand use (mirrored)"), kit.ui.tr("List: alphabet on the left, everything aligned to the right"), "left_handed", false, kit::render);
        kit.toggle(c, kit.ui.tr("Hide status bar"), null, "hide_status_bar", false, kit::render);
        kit.toggle(c, kit.ui.tr("Allow home screen rotation"), null, "rotate", false, null);
        if (classic) {
            c = kit.card("The app drawer");
            kit.toggle(c, kit.ui.tr("App drawer"), kit.ui.tr("Off: every app sits on the home pages, from page two in alphabetical order, free to move. Pages: long-press an empty spot."), "drawer", true, kit::render);
            if (kit.prefs().getBoolean("drawer", true)) {
                kit.slider(c, kit.ui.tr("App drawer: columns"), "drawer_cols", 3, 7, Layout.gridDefaults(kit.activity())[0]);
                kit.toggle(c, kit.ui.tr("App names in the app drawer"), null, "drawer_labels", true, kit::render);
            }
        }
        c = kit.card("Wallpaper");
        int[] dims = {0, 35, 60, 85};
        String[] names = kit.tr(new String[]{"Off", "Light", "Medium", "Strong"});
        kit.sub(c, kit.ui.tr("Darken the wallpaper on the home screen"));
        kit.tiles(c, dims.length, SettingsKit.indexOf(dims, kit.prefs().getInt("dim_home", 0)), names,
                i -> samples.dimTile(dims[i]), i -> kit.put("dim_home", dims[i]));
        kit.sub(c, kit.ui.tr("Dim the wallpaper behind the app list"));
        kit.tiles(c, dims.length, SettingsKit.indexOf(dims, kit.prefs().getInt("dim", 60)), names,
                i -> samples.dimTile(dims[i]), i -> kit.put("dim", dims[i]));
    }

    /** Classic mode's bricks are arranged on the home screen itself: go there, with a reminder of how. */
    private void editOnHome() {
        kit.ui.toastLong(kit.ui.tr("Long-press an icon, a widget or the clock, then drag it. Its menu resizes or removes it."));
        kit.home(0);
    }

    // ---- shared controls

    private void outlook(LinearLayout c) {
        kit.toggle(c, kit.ui.tr("Show date"), null, "show_date", true, kit::render);
        kit.toggle(c, kit.ui.tr("Show weather"), null, "show_weather", true, kit::render);
        kit.toggle(c, kit.ui.tr("Show battery percentage"), null, "show_battery", false, kit::render);
    }

    private void labels(LinearLayout c) {
        kit.toggle(c, kit.ui.tr("Hide app names of favourites"), null, "hide_labels", false, kit::render);
        kit.slider(c, kit.ui.tr("App name size (%)"), "label_scale", 60, 150, 100);
    }

    private void clockAppearance() {
        look.clockChoices(kit.card("Clock"));
        outlook(kit.card("What it shows"));
    }

    /** Columns × rows of every home page, the app pages too: touch the cell of the size you want. */
    private void gridPicker(LinearLayout c) {
        int[] g = Layout.gridDefaults(kit.activity());
        kit.sub(c, kit.ui.tr("Home grid size"));
        kit.gridPicker(c, "grid_cols", "grid_rows", g[0], g[1], 3, 7, 3, 8);
    }

    /** The app pages (drawer off): the preview shows page two; their look follows the home's unless told otherwise. */
    private void drawer(Layout layout) {
        kit.toggle(kit.card(), kit.ui.tr("Follow home"), kit.ui.tr("Use the home's icons, app names and font"),
                "drawer_follow", true, kit::render);
        gridPicker(kit.card("Grid"));
        if (layout.drawerFollowsHome()) return;
        LinearLayout c = kit.card("Icons");
        look.iconChoices(c, layout::drawerKey);
        kit.slider(c, kit.ui.tr("Icon size"), layout.drawerKey("grid_icon"), 32, 80, kit.prefs().getInt("grid_icon", 48));
        kit.toggle(c, kit.ui.tr("App names in the app drawer"), null, "drawer_labels",
                !kit.prefs().getBoolean("hide_labels", false), kit::render);
        kit.slider(c, kit.ui.tr("App name size (%)"), layout.drawerKey("label_scale"), 60, 150,
                kit.prefs().getInt("label_scale", 100));
        look.fontChoices(kit.card("Font"), layout.drawerKey("font"));
    }

    /**
     * Where a brick sits, to the dp: four arrows that move it 4 dp a press (and keep going while held), where it is
     * now, and a reset. The preview's drag does the same thing faster; the arrows finish the job exactly.
     */
    private void position(LinearLayout card, Brick b) {
        String name = kit.ui.tr(b.title);
        int right = b.right(kit.prefs());
        LinearLayout r = new LinearLayout(kit.activity());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(kit.ui.dp(20), kit.ui.dp(10), kit.ui.dp(12), kit.ui.dp(10));
        LinearLayout col = new LinearLayout(kit.activity());
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(kit.text(kit.ui.tr("Position"), 17, Colors.WHITE));
        TextView value = kit.text("", 14, SettingsKit.SUB);
        col.addView(value);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        Runnable show = () -> value.setText(String.format(java.util.Locale.ROOT, "x %+d · y %+d dp", (b.x(kit.prefs()) - b.xdef) * right, b.y(kit.prefs())));
        show.run();
        // {glyph, rotation, dx, dy, description}
        Object[][] keys = {{"up", -90f, -1, 0, "Move left"}, {"up", 0f, 0, -1, "Move up"}, {"down", 0f, 0, 1, "Move down"},
                {"up", 90f, 1, 0, "Move right"}, {"reset", 0f, 0, 0, "Reset position"}};
        for (Object[] k : keys) {
            int dx = (int) k[2], dy = (int) k[3];
            Runnable step = () -> {
                SharedPreferences.Editor e = kit.prefs().edit();
                if (dx == 0 && dy == 0) e.remove(b.kx).remove(b.ky);
                if (dx != 0) e.putInt(b.kx, b.clampX(b.x(kit.prefs()) + dx * right * STEP));
                if (dy != 0) e.putInt(b.ky, b.clampY(b.y(kit.prefs()) + dy * STEP));
                e.putString("last_moved", b.ky); // this brick goes where asked and pushes its neighbours
                e.apply();
                show.run();
                preview.afterFit(show); // after the preview fits it, show where it really is
                preview.refresh();
            };
            View button = kit.glyphButton((String) k[0], (float) k[1], name + ": " + kit.ui.tr((String) k[4]));
            if (dx == 0 && dy == 0) button.setOnClickListener(v -> step.run()); else kit.repeating(button, step);
            r.addView(button);
        }
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Lines a brick up with the left limit of its column, the middle of the screen or the right limit. */
    private void align(Brick b, int where) {
        BrickOverlay edit = preview.edit;
        if (!b.shown) return;
        float dp = kit.activity().getResources().getDisplayMetrics().density;
        // Whole dp, rounded inward: a brick pushed a hair past its limit would wrap its text.
        int width = (int) Math.ceil(b.at.width() / dp), lo = (int) Math.ceil(edit.area.left / dp), hi = (int) Math.floor(edit.area.right / dp) - width;
        int left = where == 0 ? lo : where == 2 ? hi : Math.max(lo, Math.min(hi, Math.round(edit.getWidth() / dp / 2 - width / 2f)));
        kit.put(b.kx, b.clampX(b.x(kit.prefs()) + b.right(kit.prefs()) * (left - Math.round(b.at.left / dp))));
    }
}
