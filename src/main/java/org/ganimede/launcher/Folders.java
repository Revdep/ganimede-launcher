package org.ganimede.launcher;

import org.ganimede.launcher.core.IconSet;
import static org.ganimede.launcher.Sheets.item;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.FolderLook;
import org.ganimede.launcher.core.FolderOrder;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Pop-up folders: creating one, putting things in, its menu, and the pop-up itself (its widgets, then its apps). */
final class Folders {
    private static final String BG = "bg", ALPHA = "alpha", COLUMNS = "cols";

    private final Ui ui;
    private final Sheets sheets;
    private final Layout layout;
    private final Apps apps;
    private final Entries entries;
    private final Launcher launcher;
    private final Widgets widgets;
    private final IconPicker icons;
    private final ClassicHome classic;
    private final Runnable refresh;
    /** The long-press menu of an entry inside a folder: (entry, folder id). */
    private final BiConsumer<String, String> entryMenu;
    private Motion popup;          // the pop-up last opened
    private ScrollView popupScroll; // its scroll view, whose position survives a rebuild
    private String popupId;        // its folder
    private Set<String> popupLook; // what it shows of each entry (see looks)
    private FolderDrag popupDrag;  // its icons' touch handling: no rebuild under a finger

    Folders(Ui ui, Sheets sheets, Layout layout, Apps apps, Entries entries, Launcher launcher, Widgets widgets,
            IconPicker icons, ClassicHome classic, Runnable refresh, BiConsumer<String, String> entryMenu) {
        this.ui = ui;
        this.sheets = sheets;
        this.layout = layout;
        this.apps = apps;
        this.entries = entries;
        this.launcher = launcher;
        this.widgets = widgets;
        this.icons = icons;
        this.classic = classic;
        this.refresh = refresh;
        this.entryMenu = entryMenu;
    }

    /** "Remove from this folder" in an entry's menu: out of the folder; the open pop-up follows (reshowIfChanged). */
    void remove(String id, String entry) {
        if (layout.editFolderApps(id, a -> a.remove(entry))) refresh.run();
    }

    /**
     * What a pop-up draws (FolderOrder.looks). An app's icon is the one its Item was made with (Item.picked and
     * Item.installed): a newly picked icon or an update of that app counts once Apps.load has made the new Item, and
     * other apps' updates change nothing here. A shortcut draws its picked icon straight from the setting.
     */
    private Set<String> looks(String id) {
        return FolderOrder.looks(Layout.strings(layout.folder(id).optJSONArray("apps")), entries::name, k -> {
            if (k.startsWith("s:")) return ui.prefs.getString(IconSet.pickedKey(k), "");
            Item it = apps.find(k);
            return it == null ? null : FolderOrder.drawnIcon(it.picked, it.installed);
        });
    }

    /**
     * After any change (MainActivity.refreshAll) and whenever the open pop-up comes back in front (a menu, dialog or
     * other app on top closed): if its entries now look different (an entry taken out or uninstalled, renamed, a new icon) it
     * is built again. A reorder alone changes nothing here, the pop-up already shows it; an open menu or dialog is
     * waited for, so the new pop-up never covers it, and so is a finger on an icon (FolderDrag calls back once it
     * lifts).
     * The entry TalkBack was on keeps its focus.
     */
    void reshowIfChanged() {
        if (popup == null || !popup.isShowing() || popup.closing()) return; // closing: tapped away, or an app opening
        if (!popup.getWindow().getDecorView().hasWindowFocus()) return;
        if (popupDrag.busy() || looks(popupId).equals(popupLook)) return;
        String focused = popupDrag.focused();
        int scrolled = popupScroll.getScrollY();
        popup.dismiss();
        show(popupId);
        ScrollView again = popupScroll;
        again.post(() -> again.scrollTo(0, scrolled));
        if (focused != null) popupDrag.focus(focused);
    }

    private void save(String id, JSONObject f) {
        layout.saveFolder(id, f);
        refresh.run();
    }

    private String defaultName(String id) {
        return id.equals("button") ? ui.tr("Ganimede button") : ui.tr("Folder");
    }

    /** The title; the button's pop-up drops the default name older builds saved for it ("Quick button"). */
    private String name(String id, JSONObject f) {
        String n = f.optString("name", "");
        boolean legacy = id.equals("button") && (n.equals("Quick button") || n.equals("Pulsante rapido"));
        return n.isEmpty() || legacy ? defaultName(id) : n;
    }

    /**
     * New folder: a name, then "add apps now?", and if yes the list of every app with check boxes. The folder lands on
     * the page shown (classic: in `cell`, "g:N", else the first free one) and opens to show what's inside.
     * firstItem: an app or shortcut to put in.
     */
    void create(String firstItem, String cell) {
        if (target(cell) < 0) return; // full: said before a name is typed
        EditText input = new EditText(ui.a);
        input.setHint(ui.tr("Folder name"));
        input.setSingleLine(true);
        ui.ask()
                .setTitle(ui.tr("Create pop-up folder"))
                .setView(input)
                .setPositiveButton(ui.tr("Create"), (d, w) -> {
                    int at = target(cell); // again: an app installed meanwhile may have taken the cell
                    if (at < 0) return;
                    String typed = input.getText().toString().trim(), name = typed.isEmpty() ? ui.tr("Folder") : typed;
                    String id = layout.newFolder(name, firstItem == null ? List.of() : List.of(firstItem));
                    if (id == null) return;
                    if (layout.classic()) { // a grid cell, as a folder made by a drop
                        Map<Integer, String> g = layout.grid();
                        g.put(at, "f:" + id);
                        layout.saveGrid(g);
                    } else {
                        List<String> fav = layout.favourites();
                        fav.add("f:" + id);
                        layout.saveFavourites(fav);
                    }
                    refresh.run();
                    ui.ask()
                            .setTitle(name)
                            .setMessage(ui.tr("Add apps to this folder now?"))
                            .setPositiveButton(ui.tr("Yes, choose apps"), (x, y) -> chooseApps(id))
                            .setNegativeButton(ui.tr("Later"), (x, y) -> show(id))
                            .show();
                })
                .setNegativeButton(ui.tr("Cancel"), null)
                .show();
        input.requestFocus();
    }

    /** The classic cell a new folder takes (see ClassicHome.freeCell), 0 in the list mode; -1 and a toast when full. */
    private int target(String cell) {
        int at = layout.classic() ? classic.freeCell(cell) : 0;
        if (at < 0) ui.toast(ui.tr("The home screen is full"));
        return at;
    }

    /** Puts an app (component key) or a shortcut ("s:…") into an existing folder or a new one. */
    void choose(String entry) {
        List<Sheets.Row> rows = new ArrayList<>();
        rows.add(item("plus", ui.tr("+ New folder").replace("+ ", ""), () -> create(entry, null)));
        for (String id : layout.folderIds()) {
            rows.add(item("folder", layout.folder(id).optString("name", ui.tr("Folder")), () -> {
                if (!layout.editFolderApps(id, a -> { if (!a.contains(entry)) a.add(entry); })) return;
                refresh.run();
                ui.toast(ui.f("Added to %s", layout.folder(id).optString("name")));
            }));
        }
        sheets.show(ui.tr("Add to pop-up folder…"), rows);
    }

    void menu(String id) {
        List<Sheets.Row> rows = new ArrayList<>();
        rows.add(item("check", ui.tr("Choose apps"), () -> chooseApps(id)));
        rows.add(item("widget", ui.tr("Add widget"), () -> widgets.pick(id)));
        rows.add(item("image", ui.tr("Icon…"), () -> icons.choose("f:" + id)));
        rows.add(item("gear", ui.tr("Customize folder"), () -> customize(id)));
        rows.add(item("edit", ui.tr("Rename"), () -> {
            EditText input = new EditText(ui.a);
            input.setText(layout.folder(id).optString("name"));
            input.setSingleLine(true);
            ui.ask().setTitle(ui.tr("Rename")).setView(input)
                    .setPositiveButton(ui.tr("Save"), (d, w) -> {
                        JSONObject f = layout.folder(id);
                        try { f.put("name", input.getText().toString().trim()); } catch (Exception ignored) { return; }
                        save(id, f);
                    }).setNegativeButton(ui.tr("Cancel"), null).show();
        }));
        if (!id.equals("button")) rows.add(item("trash", ui.tr("Delete folder"), () -> {
            widgets.deleteAll(Layout.strings(layout.folder(id).optJSONArray("widgets")));
            layout.deleteFolder(id);
            refresh.run();
        }));
        sheets.show(name(id, layout.folder(id)), rows);
    }

    /** Folder-only overrides use the Settings controls but stay in this folder's JSON. */
    private void customize(String id) {
        Runnable[] draw = new Runnable[1];
        SettingsKit kit = new SettingsKit(ui.a, ui.prefs, () -> draw[0].run(), page -> {}, () -> {});
        kit.box.setOrientation(LinearLayout.VERTICAL);
        draw[0] = () -> { // a picked colour redraws the card so its tick moves; the sliders keep their own value
            kit.box.removeAllViews();
            JSONObject f = layout.folder(id);
            int[] d = FolderControls.defaults(ui.prefs, ui.light());
            FolderControls.add(kit, kit.card(),
                    value(f, BG, d[0]), value(f, ALPHA, d[1]), value(f, COLUMNS, d[2]),
                    v -> { saveLook(id, BG, v); draw[0].run(); }, v -> saveLook(id, ALPHA, v), v -> saveLook(id, COLUMNS, v));
        };
        draw[0].run();
        ScrollView scroll = new ScrollView(ui.a);
        scroll.addView(kit.box);
        ui.ask().setTitle(ui.tr("Customize folder")).setView(scroll)
                .setNeutralButton(ui.tr("Use the defaults"), (d, w) -> {
                    JSONObject folder = layout.folder(id);
                    folder.remove(BG);
                    folder.remove(ALPHA);
                    folder.remove(COLUMNS);
                    layout.saveFolder(id, folder);
                })
                .setNegativeButton(ui.tr("Close"), null).show();
    }

    private void saveLook(String id, String key, int value) {
        JSONObject f = layout.folder(id);
        try { f.put(key, value); } catch (JSONException ignored) { }
        layout.saveFolder(id, f);
    }

    private static Integer number(JSONObject f, String key) {
        Object value = f.opt(key);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static int value(JSONObject f, String key, int fallback) {
        Integer value = number(f, key);
        return value == null ? fallback : value;
    }

    /** The folder's pinned shortcuts, then every app, with check boxes: unchecking takes an entry out of the folder. */
    private void chooseApps(String id) {
        JSONObject f = layout.folder(id);
        List<String> inside = Layout.strings(f.optJSONArray("apps")), listed = new ArrayList<>(), names = new ArrayList<>();
        for (String k : inside) if (k.startsWith("s:")) { listed.add(k); names.add(Entries.shortcutName(k)); }
        for (Item it : apps.visible()) { listed.add(it.key); names.add(apps.label(it)); }
        boolean[] on = new boolean[listed.size()];
        for (int i = 0; i < on.length; i++) on[i] = inside.contains(listed.get(i));
        ui.ask().setTitle(ui.tr("Apps in this pop-up"))
                .setMultiChoiceItems(names.toArray(new String[0]), on, (d, w, c) -> on[w] = c)
                .setPositiveButton(ui.tr("Save"), (d, w) -> {
                    List<String> checked = new ArrayList<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) checked.add(listed.get(i));
                    try {
                        if (!f.has("name")) f.put("name", defaultName(id));
                        f.put("apps", new JSONArray(FolderOrder.chosen(inside, listed, checked)));
                    } catch (Exception ignored) { return; }
                    save(id, f);
                    show(id); // show the result right away
                }).setNegativeButton(ui.tr("Cancel"), null).show();
    }

    private static final int CELL_DP = 72, ICON_DP = 40, LABEL_SP = 11, GAP_DP = 3, PAD_DP = 14, MIN_WIDGET_DP = 240;

    /** A pop-up: the folder's widgets on top, then its apps. */
    void show(String id) {
        if (popup != null && popup.isShowing()) popup.dismiss(); // "New folder" from inside one must not open a second over it
        JSONObject f = layout.folder(id);
        int[] d = FolderControls.defaults(ui.prefs, ui.light());
        FolderLook look = FolderLook.resolve(number(f, BG), number(f, ALPHA), number(f, COLUMNS), d[0], d[1], d[2], ui.light());
        LinearLayout box = new LinearLayout(ui.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(ui.dp(PAD_DP), ui.dp(12), ui.dp(PAD_DP), ui.dp(6));
        GradientDrawable card = new GradientDrawable();
        card.setColor(look.color());
        card.setCornerRadius(ui.dp(22));
        box.setBackground(card);
        int homeText = ui.fg;
        ui.fg = look.text(); // the title and entries built below read the card's text colour
        TextView title = ui.text(16, true);
        title.setText(name(id, f));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPaddingRelative(0, 0, 0, ui.dp(6));
        box.addView(title);
        int content = 0;
        boolean hasWidgets = false;
        for (String w : Layout.strings(f.optJSONArray("widgets"))) {
            View v = widgets.view(w);
            if (v != null) {
                box.addView(v);
                hasWidgets = true;
                content++;
            }
        }
        Dialog[] dlg = new Dialog[1];
        List<View> appViews = new ArrayList<>();
        FolderDrag drag = new FolderDrag(ui, entry -> entryMenu.accept(entry, id), sheets::dismissLast,
                this::reshowIfChanged, shown -> {
            layout.editFolderApps(id, a -> { List<String> sorted = FolderOrder.reorderShown(a, shown); a.clear(); a.addAll(sorted); });
            refresh.run();
        });
        for (String k : Layout.strings(f.optJSONArray("apps"))) {
            View v = folderEntry(id, k, look.columns() > 1, dlg, drag);
            if (v != null) appViews.add(v);
        }
        if (look.columns() == 1) {
            for (View v : appViews) box.addView(v);
        } else if (!appViews.isEmpty()) {
            LinearLayout grid = new LinearLayout(ui.a);
            grid.setOrientation(LinearLayout.VERTICAL);
            for (int from = 0; from < appViews.size(); from += look.columns()) {
                LinearLayout row = new LinearLayout(ui.a);
                row.setBaselineAligned(false);
                for (int col = 0; col < look.columns(); col++) {
                    View v = from + col < appViews.size() ? appViews.get(from + col) : new View(ui.a);
                    row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
                }
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            box.addView(grid);
        }
        content += appViews.size();
        if (content == 0) {
            TextView e = ui.text(15, false);
            e.setText(ui.tr("This pop-up is empty. Add apps or a widget below."));
            e.setAlpha(0.8f);
            box.addView(e);
        }
        TextView edit = ui.text(13, true);
        edit.setText(ui.tr("✎ Edit pop-up"));
        edit.setTextColor(ui.accent);
        edit.setMinHeight(ui.dp(48)); // a touch target
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setOnClickListener(v -> { dlg[0].dismiss(); menu(id); });
        box.addView(edit);
        ui.fg = homeText;
        ScrollView sv = new ScrollView(ui.a);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        if (look.columns() > 1) { // a grid needs a definite width; one column keeps Motion.pop's own margins
            int screenMax = Math.min(ui.a.getResources().getDisplayMetrics().widthPixels - ui.dp(48), ui.dp(420));
            float fontScale = Math.max(1, ui.a.getResources().getConfiguration().fontScale); // bigger text needs wider cells
            int width = FolderLook.popupWidth(look.columns(), Math.round(ui.dp(CELL_DP) * fontScale), ui.dp(PAD_DP),
                    hasWidgets ? ui.dp(MIN_WIDGET_DP) : 0, screenMax); // a widget needs room to read
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, -2, Gravity.CENTER);
            int m = ui.dp(24); // Motion.pop's margin, so a tall card stays clear of the bars
            lp.setMargins(m, m, m, m);
            sv.setLayoutParams(lp);
        }
        Motion shown = Motion.pop(ui, sv, null); // out of the folder's icon, back into it
        dlg[0] = popup = shown;
        shown.setOnDismissListener(x -> { // an older pop-up ending after its rebuild must not clear the new one
            box.removeAllViews(); // Widgets keeps its host views: a closed card must not hold them
            if (popup == shown) { popup = null; popupId = null; popupLook = null; popupDrag = null; }
        });
        dlg[0].show();
        popupId = id;
        popupLook = looks(id);
        popupDrag = drag;
        popupScroll = sv;
        dlg[0].getWindow().getDecorView().getViewTreeObserver()
                .addOnWindowFocusChangeListener(has -> { if (has) reshowIfChanged(); });
    }

    private View folderEntry(String id, String key, boolean cell, Dialog[] dlg, FolderDrag drag) {
        if (key.startsWith("s:")) {
            View v = cell ? entries.cell(key, ICON_DP, LABEL_SP, GAP_DP) : entries.shortcutRow(key, false);
            drag.add(v, key);
            return v;
        }
        Item app = apps.find(key);
        if (app == null) return null;
        View v = cell ? entries.cell(key, ICON_DP, LABEL_SP, GAP_DP) : entries.appRow(app, false);
        v.setOnClickListener(x -> { dlg[0].dismiss(); launcher.launch(app, x); });
        drag.add(v, key);
        return v;
    }
}
