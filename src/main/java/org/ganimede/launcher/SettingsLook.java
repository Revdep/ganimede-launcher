package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.FolderLook;
import org.ganimede.launcher.core.IconLook;
import org.ganimede.launcher.core.IconSet;
import org.ganimede.launcher.core.Preferences;
import org.ganimede.launcher.core.Safe;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Process;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** The look: themes, clock, font, colours, icons and the Ganimede button, with the choosers the wizard and bricks reuse. */
final class SettingsLook {
    private static final String[] FONTS = {"sans-serif", "sans-serif-light", "sans-serif-thin", "sans-serif-medium",
            "sans-serif-condensed", "serif", "monospace", "casual", "cursive", "sans-serif-smallcaps"};
    private static final String[] FONT_NAMES = {"Default", "Light", "Thin", "Medium", "Condensed", "Serif", "Monospace",
            "Casual", "Handwriting", "Small caps"};
    private static final int[] ACCENTS = {Colors.ACCENT, 0xFF8AB4F8, 0xFF9FD3C7, 0xFFA8D672, 0xFFF6C177, 0xFFF28B82, 0xFFE01010, 0xFFF0F0F0};
    private static final String[] ACCENT_NAMES = {"Lavender", "Sky", "Mint", "Leaf", "Amber", "Coral", "Red", "White"};
    private static final int[] BUTTON_ICONS = {0, R.drawable.ganimede_gray, R.drawable.ganimede_colour, 0}; // 0: star, own image
    /** Theme presets: name, description, then setting/value pairs. */
    private static final Object[][] PRESETS = {
            {"Lavender wave", "Overlapping clock, round tinted icons", "clock_style", "overlap", "font", "sans-serif",
                    "text_color", "light", "accent", Colors.ACCENT,
                    "icon_glyph", "line", "icon_container", "circle", "icon_fill", "solid", "icon_color", "accent",
                    "hide_labels", false, "alpha_home", true, "fab", false},
            {"Night rain", "Weekday clock, white icons, no labels", "clock_style", "dayofweek", "font", "sans-serif-light",
                    "text_color", "light", "accent", 0xFFF0F0F0,
                    "icon_glyph", "line", "icon_container", "none", "icon_fill", "solid", "icon_color", "text",
                    "hide_labels", true, "alpha_home", false, "fab", false},
            {"Sakura", "Simple clock, red dots, Ganimede button", "clock_style", "basic", "font", "casual",
                    "text_color", "dark", "accent", 0xFFE01010,
                    "icon_glyph", "dot", "icon_container", "none", "icon_fill", "solid", "icon_color", "accent",
                    "hide_labels", false, "alpha_home", false, "fab", true, "show_battery", true},
            {"Misty forest", "Tall vertical clock, square icons", "clock_style", "vertical", "font", "sans-serif-condensed",
                    "text_color", "light", "accent", 0xFF9FD3C7,
                    "icon_glyph", "original", "icon_container", "square", "icon_fill", "solid", "icon_color", "accent",
                    "hide_labels", false, "alpha_home", false, "fab", false}};

    private final SettingsKit kit;
    private final SettingsPerms perms;
    private final SettingsSamples samples;
    private final Runnable pickButtonIcon;

    SettingsLook(SettingsKit kit, SettingsPerms perms, SettingsSamples samples, Runnable pickButtonIcon) {
        this.kit = kit;
        this.perms = perms;
        this.samples = samples;
        this.pickButtonIcon = pickButtonIcon;
    }

    /** Copies of the choices, which no other page may change. */
    static String[] fonts() { return FONTS.clone(); }
    static String[] fontNames() { return FONT_NAMES.clone(); }
    static int[] accents() { return ACCENTS.clone(); }
    static String[] accentNames() { return ACCENT_NAMES.clone(); }

    /** Folder controls: the Customize page's Folders pill. */
    void folders() {
        LinearLayout c = kit.card("Folders");
        int[] d = FolderControls.defaults(kit.prefs(), kit.ui.light());
        FolderControls.add(kit, c, d[0], d[1], d[2],
                v -> kit.put(FolderLook.BACKGROUND_KEY, v), v -> kit.save(FolderLook.ALPHA_KEY, v),
                v -> kit.save(FolderLook.COLUMNS_KEY, v));
    }

    /** Theme controls reused by the Customize page's Themes pill. */
    void themes() {
        kit.label("Themes");
        LinearLayout grid = null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (i % 2 == 0) {
                grid = new LinearLayout(kit.activity());
                kit.box.addView(grid, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(i % 2 == 0 ? 0 : kit.ui.dp(6), 0, i % 2 == 0 ? kit.ui.dp(6) : 0, kit.ui.dp(12));
            Object[] pr = PRESETS[i];
            grid.addView(samples.presetCard(pr, () -> preset(pr, 2)), lp);
        }
        LinearLayout c = kit.card();
        kit.row(c, "layers", kit.ui.tr("Copy my theme"), kit.ui.tr("Share it in a message"), this::copyTheme);
        kit.row(c, "edit", kit.ui.tr("Paste a copied theme"), null, this::pasteTheme);

        fontChoices(kit.card("Font"));

        c = kit.card("Colours");
        kit.sub(c, kit.ui.tr("Text colour"));
        kit.tiles(c, 2, kit.ui.light() ? 0 : 1, new String[]{kit.ui.tr("Light, for dark wallpapers"), kit.ui.tr("Dark, for light wallpapers")},
                i -> samples.aa(i == 1, kit.ui.face), i -> kit.put("text_color", i == 0 ? "light" : "dark"));
        kit.sub(c, kit.ui.tr("Theme colour"));
        kit.tiles(c, ACCENTS.length, SettingsKit.indexOf(ACCENTS, kit.ui.accent), kit.tr(ACCENT_NAMES), i -> samples.swatch(ACCENTS[i]), i -> kit.put("accent", ACCENTS[i]));

        c = kit.card("Icons");
        iconChoices(c);
        boolean classic = new Layout(kit.activity(), kit.prefs()).classic();
        // One slider for the icons of the chosen mode: the preview follows the finger.
        kit.slider(c, kit.ui.tr("Icon size"), classic ? "grid_icon" : "icon_size", 32, 80, classic ? 48 : 54);
        kit.toggle(c, kit.ui.tr("Hide app names of favourites"), null, "hide_labels", false, kit::render);
        kit.slider(c, kit.ui.tr("App name size (%)"), "label_scale", 60, 150, 100);

        iconPacks(kit.card("Icon pack"));

        c = kit.card("Wallpaper");
        kit.row(c, "image", kit.ui.tr("Change wallpaper"), kit.ui.tr("Pick one that suits your theme"),
                () -> Ui.start(kit.activity(), Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), kit.ui.tr("Wallpaper"))));
    }

    /** List or classic, as two tappable miniatures of the real home screen. */
    void modeChooser(LinearLayout c) {
        LinearLayout pair = new LinearLayout(kit.activity());
        pair.setPadding(kit.ui.dp(12), kit.ui.dp(8), kit.ui.dp(12), kit.ui.dp(12));
        String mode = kit.prefs().getString("mode", "list");
        String[][] modes = {{"list", "List", "Favourites and an A–Z list, like a minimalist launcher"},
                {"classic", "Classic", "A grid of icons, a dock and an app drawer"}};
        for (String[] m : modes) {
            LinearLayout cardBox = new LinearLayout(kit.activity());
            cardBox.setOrientation(LinearLayout.VERTICAL);
            cardBox.setGravity(Gravity.CENTER_HORIZONTAL);
            cardBox.setPadding(kit.ui.dp(8), kit.ui.dp(10), kit.ui.dp(8), kit.ui.dp(10));
            boolean on = m[0].equals(mode);
            GradientDrawable g = kit.round(on ? Colors.alpha(kit.ui.accent, 0x33) : 0x14FFFFFF, 20);
            if (on) g.setStroke(kit.ui.dp(2), kit.ui.accent);
            cardBox.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
            cardBox.addView(new SettingsPreview(kit.activity(), kit.ui.dp(230), m[0]), new LinearLayout.LayoutParams(-2, -2));
            TextView name = kit.text((on ? "✓ " : "") + kit.ui.tr(m[1]), 16, Colors.WHITE);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setPadding(0, kit.ui.dp(8), 0, kit.ui.dp(2));
            cardBox.addView(name);
            TextView d = kit.text(kit.ui.tr(m[2]), 13, SettingsKit.SUB);
            d.setGravity(Gravity.CENTER);
            cardBox.addView(d);
            cardBox.setContentDescription(kit.ui.tr(m[1]));
            SettingsKit.selectable(cardBox, on);
            cardBox.setOnClickListener(v -> kit.put("mode", m[0]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); // both cards as tall as the taller
            lp.setMargins(kit.ui.dp(4), 0, kit.ui.dp(4), 0);
            pair.addView(cardBox, lp);
        }
        c.addView(pair, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---- pieces shared by the settings pages and the setup wizard

    void clockChoices(LinearLayout c) {
        kit.tiles(c, ClockView.STYLES.length, Math.max(0, SettingsKit.indexOf(ClockView.STYLES, kit.prefs().getString("clock_style", "overlap"))), kit.tr(ClockView.NAMES),
                i -> samples.clockTile(ClockView.STYLES[i], kit.ui.face, kit.ui.fg, !kit.ui.light()), i -> kit.put("clock_style", ClockView.STYLES[i]));
        kit.toggle(c, kit.ui.tr("24-hour clock"), null, "clock24", DateFormat.is24HourFormat(kit.activity()), kit::render);
    }

    void fontChoices(LinearLayout c) {
        fontChoices(c, "font");
    }

    void fontChoices(LinearLayout c, String key) {
        Typeface[] faces = new Typeface[FONTS.length];
        String[] samples = new String[FONTS.length];
        for (int i = 0; i < FONTS.length; i++) {
            faces[i] = Ui.font(FONTS[i]);
            samples[i] = "12:30 · Aa Bb Cc 123";
        }
        String selected = kit.prefs().getString(key, kit.prefs().getString("font", "sans-serif"));
        kit.radios(c, kit.tr(FONT_NAMES), Math.max(0, SettingsKit.indexOf(FONTS, selected)), faces, samples,
                i -> kit.put(key, FONTS[i]));
    }

    void iconChoices(LinearLayout c) {
        iconChoices(c, UnaryOperator.identity());
    }

    /** The icon model's four rows; {@code key} maps a home key to the one saved (the drawer's own on the app pages). */
    void iconChoices(LinearLayout c, UnaryOperator<String> key) {
        SharedPreferences p = kit.prefs();
        IconLook l = IconLook.read(k -> p.getString(key.apply(k), p.getString(k, null)));
        kit.sub(c, kit.ui.tr("Icon style"));
        iconRow(c, "Icon style", IconLook.GLYPHS, Icons.GLYPH_NAMES, l.glyph(), "icon_glyph", key,
                v -> IconLook.of(v, l.container(), l.fill(), l.color()));
        kit.para(c, kit.ui.tr("Line, Bold, Sharp, Duotone and Hollow change the stroke only for Arcticons (built-in) icons; the others take the same colour and plate."),
                13, SettingsKit.SUB);
        if (!l.glyph().equals("dot")) { // Dots replace the icon: no plate
            kit.sub(c, kit.ui.tr("Plate"));
            iconRow(c, "Plate", IconLook.CONTAINERS, Icons.CONTAINER_NAMES, l.container(), "icon_container", key,
                    v -> IconLook.of(l.glyph(), v, l.fill(), l.color()));
        }
        if (!l.tinted(IconSet.PACK.equals(p.getString("icon_pack", "")))) return; // the apps' own colours: no fill, no colour
        if (l.plated()) {
            kit.sub(c, kit.ui.tr("Fill"));
            iconRow(c, "Fill", IconLook.FILLS, Icons.FILL_NAMES, l.fill(), "icon_fill", key,
                    v -> IconLook.of(l.glyph(), l.container(), v, l.color()));
        }
        kit.sub(c, kit.ui.tr("Colour"));
        iconRow(c, "Colour", IconLook.COLORS, Icons.COLOR_NAMES, l.color(), "icon_color", key,
                v -> IconLook.of(l.glyph(), l.container(), l.fill(), v));
    }

    /** One row of tiles, each showing the sample icon with that value; TalkBack hears "group: choice" (rows share names). */
    private void iconRow(LinearLayout c, String group, String[] values, String[] names, String selected, String homeKey,
                         UnaryOperator<String> key, java.util.function.Function<String, IconLook> with) {
        kit.tiles(c, values.length, Math.max(0, SettingsKit.indexOf(values, selected)), kit.tr(names), described(group, names),
                i -> samples.sampleIcon(with.apply(values[i]), 44, kit.ui.fg, kit.ui.accent),
                i -> kit.put(key.apply(homeKey), values[i]));
    }

    void iconPacks(LinearLayout c) {
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        ids.add("");
        names.add(kit.ui.tr("None (apps' own icons)"));
        ids.add(IconSet.PACK);
        names.add(kit.ui.tr("Arcticons (built-in)"));
        for (Map.Entry<String, String> e : Icons.packs(kit.activity()).entrySet()) { ids.add(e.getKey()); names.add(e.getValue()); }
        kit.radios(c, names.toArray(new String[0]), Math.max(0, ids.indexOf(kit.prefs().getString("icon_pack", ""))), null, null,
                i -> kit.put("icon_pack", ids.get(i)));
    }

    /** The Ganimede button: what tap, double tap, triple tap and long press do. */
    void button() {
        LinearLayout c = kit.card("Ganimede button");
        kit.toggle(c, kit.ui.tr("Show the Ganimede button"), kit.ui.tr("A round button in the bottom corner"), "fab", false, kit::render);
        kit.sub(c, kit.ui.tr("Button icon"));
        buttonIconChoices(c);
        buttonGestures(kit.card("Gestures"));
    }

    /** Two short rows keep every choice on screen and reachable without a horizontal swipe. */
    private void buttonIconChoices(LinearLayout card) {
        String chosen = kit.prefs().getString("fab_icon", "default");
        if (chosen.equals("custom") && ButtonIcon.loaded() && ButtonIcon.cached() == null) chosen = "default"; // restored without its image
        int selected = Math.max(0, SettingsKit.indexOf(ButtonIcon.VALUES, chosen));
        for (int start = 0; start < ButtonIcon.VALUES.length; start += 2) {
            int first = start;
            String[] names = java.util.Arrays.copyOfRange(ButtonIcon.NAMES, first, first + 2);
            kit.tiles(card, 2, selected >= first && selected < first + 2 ? selected - first : -1,
                    kit.tr(names), described("Button icon", names), i -> buttonThumbnail(first + i), i -> setButtonIcon(first + i));
        }
    }

    /** "Button icon: Gray": the group joins the choice so TalkBack can tell the two rows' equal labels apart. */
    private String[] described(String group, String[] names) {
        String[] out = new String[names.length];
        for (int i = 0; i < names.length; i++) out[i] = kit.ui.tr(group) + ": " + kit.ui.tr(names[i]);
        return out;
    }

    private View buttonThumbnail(int index) {
        if (index == 0) return samples.swatch(kit.ui.accent);
        if (BUTTON_ICONS[index] != 0) return samples.picture(kit.activity().getDrawable(BUTTON_ICONS[index]));
        Bitmap own = ButtonIcon.cached(); // only once the home screen or an import has loaded it
        return own == null ? null : samples.picture(new BitmapDrawable(kit.activity().getResources(), own));
    }

    private void setButtonIcon(int selected) {
        if (ButtonIcon.VALUES[selected].equals("custom")) pickButtonIcon.run();
        else kit.put("fab_icon", ButtonIcon.VALUES[selected]);
    }

    void buttonGestures(LinearLayout c) {
        for (int i = 0; i < Actions.GESTURES.length; i++) {
            String g = Actions.GESTURES[i], action = kit.prefs().getString(g, Actions.DEFAULTS[i]);
            String glyph = "button";
            for (String[] b : Actions.BUILT_IN) if (b[0].equals(action)) glyph = b[2];
            if (action.startsWith("app:")) glyph = "apps";
            if (action.startsWith("sc:") || action.startsWith("link:")) glyph = "shortcut";
            kit.link(c, glyph, kit.ui.tr(Actions.GESTURE_NAMES[i]), Actions.name(kit.activity(), action), () -> chooseAction(g));
        }
        kit.para(c, kit.ui.tr("Double and triple tap wait a moment for the next tap; leave them on “Nothing” and a single tap answers instantly."), 14, SettingsKit.SUB);
        kit.para(c, kit.ui.tr("Screenshot, lock, recent apps, power menu and quick settings use Ganimede's accessibility switch (Settings → Gestures → Quick Lock)."), 14, SettingsKit.SUB);
    }

    private void chooseAction(String gesture) {
        List<Sheets.Row> rows = new ArrayList<>();
        for (String[] b : Actions.BUILT_IN) {
            String id = b[0];
            rows.add(Sheets.item(b[2], kit.ui.tr(b[1]), () -> kit.put(gesture, id)));
        }
        rows.add(Sheets.item("apps", kit.ui.tr("Open an app…"), () -> pickApp(app -> kit.put(gesture, "app:" + app.key))));
        rows.add(Sheets.item("shortcut", kit.ui.tr("An app's shortcut…"), () -> pickApp(app -> pickShortcut(gesture, app))));
        rows.add(Sheets.item("shortcut", kit.ui.tr("One of my shortcuts…"), () -> pickMine(gesture)));
        new Sheets(kit.ui).show(kit.ui.tr(Actions.GESTURE_NAMES[java.util.Arrays.asList(Actions.GESTURES).indexOf(gesture)]), rows);
    }

    private void pickApp(java.util.function.Consumer<Apps.Item> then) {
        List<Apps.Item> l = Apps.named(kit.activity());
        String[] names = new String[l.size()];
        for (int i = 0; i < names.length; i++) names[i] = l.get(i).label;
        Ui.dialog(kit.activity()).setTitle(kit.ui.tr("Open an app…"))
                .setItems(names, (d, w) -> then.accept(l.get(w))).show();
    }

    private void pickShortcut(String gesture, Apps.Item app) {
        List<android.content.pm.ShortcutInfo> l = new ArrayList<>();
        try {
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setPackage(app.pkg)
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<android.content.pm.ShortcutInfo> got = kit.activity().getSystemService(LauncherApps.class).getShortcuts(q, Process.myUserHandle());
            if (got != null) l.addAll(got);
        } catch (Exception ignored) {
            // Only the default home app can read shortcuts.
        }
        if (l.isEmpty()) {
            kit.ui.toastLong(kit.ui.tr(perms.isHome() ? "This app has no shortcuts" : "Make Ganimede your home app first to use app shortcuts"));
            return;
        }
        String[] names = new String[l.size()];
        for (int i = 0; i < names.length; i++) names[i] = String.valueOf(l.get(i).getShortLabel());
        Ui.dialog(kit.activity()).setTitle(app.label).setItems(names, (d, w) -> {
            String entry = Safe.shortcutEntry(l.get(w).getPackage(), l.get(w).getId(), app.label + " · " + names[w]);
            if (entry == null) kit.ui.toast(kit.ui.tr("This shortcut can't be opened"));
            else kit.put(gesture, "sc:" + entry.substring(2)); // gestures store the same fields under "sc:"
        }).show();
    }

    private void pickMine(String gesture) {
        JSONArray mine = Sunflower.links(kit.activity());
        if (mine.length() == 0) {
            kit.ui.toastLong(kit.ui.tr("No shortcuts yet: add some in Search & shortcuts → My shortcuts"));
            return;
        }
        String[] names = new String[mine.length()];
        for (int i = 0; i < names.length; i++) names[i] = mine.optJSONObject(i).optString("name");
        Ui.dialog(kit.activity()).setTitle(kit.ui.tr("One of my shortcuts…"))
                .setItems(names, (d, w) -> kit.put(gesture, "link:" + names[w].replace("|", " ") + "|" + mine.optJSONObject(w).optString("uri")))
                .show();
    }

    private void preset(Object[] kv, int from) {
        put(kv, from);
        kit.ui.toast(kit.ui.tr("Theme applied. Pick a matching wallpaper to finish the look."));
        kit.render();
    }

    /** Saves setting/value pairs, each as its own type. */
    private void put(Object[] kv, int from) {
        SharedPreferences.Editor e = kit.prefs().edit();
        for (int i = from; i < kv.length; i += 2) Prefs.put(e, (String) kv[i], kv[i + 1]);
        e.apply();
    }

    private void copyTheme() {
        try {
            JSONObject o = new JSONObject().put("ganimede_theme", 1);
            Map<String, ?> all = kit.prefs().getAll();
            for (String k : Preferences.THEME_KEYS) if (all.containsKey(k)) o.put(k, all.get(k));
            kit.activity().getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Ganimede theme", o.toString()));
            kit.ui.toast(kit.ui.tr("Theme copied: paste it in a message to share it"));
        } catch (Exception e) {
            kit.ui.toast(kit.ui.tr("Could not copy the theme"));
        }
    }

    private void pasteTheme() {
        try {
            ClipData c = kit.activity().getSystemService(ClipboardManager.class).getPrimaryClip();
            JSONObject o = new JSONObject(String.valueOf(c.getItemAt(0).coerceToText(kit.activity())));
            if (o.optInt("ganimede_theme") != 1) throw new Exception();
            Map<String, Object> theme = new java.util.HashMap<>();
            List<String> keys = new ArrayList<>(List.of(Preferences.THEME_KEYS));
            keys.addAll(List.of("icon_shape", "themed_shape")); // a theme copied before the icon model: translated below
            for (String k : keys) {
                if (!o.has(k)) continue;
                Object v = o.get(k);
                // A theme comes from other people: only values of the right type and size are taken.
                if ((v instanceof Boolean || v instanceof Integer || v instanceof String) && SettingsBackup.fits(k, v)) theme.put(k, v);
            }
            theme.putAll(IconLook.migrate(theme));
            List<Object> kv = new ArrayList<>();
            for (Map.Entry<String, Object> e : theme.entrySet()) { kv.add(e.getKey()); kv.add(e.getValue()); }
            preset(kv.toArray(), 0);
        } catch (Exception | StackOverflowError e) { // deeply nested JSON overflows the parser's stack
            kit.ui.toast(kit.ui.tr("The clipboard doesn't hold a Ganimede theme"));
        }
    }
}
