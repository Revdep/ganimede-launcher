package org.ganimede.launcher;

import static org.ganimede.launcher.Sheets.item;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Safe;

import android.Manifest;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The long-press menus (an app, a pinned shortcut, the empty home screen) and the dialogs they open. */
final class Menus {
    private final Ui ui;
    private final Sheets sheets;
    private final Layout layout;
    private final Apps apps;
    private final Launcher launcher;
    private final LauncherApps launcherApps;
    private final Folders folders;
    private final IconPicker icons;
    private final ClassicHome classic;
    private final Widgets widgets;
    private final Notices notices;
    private final Runnable refresh;

    Menus(Ui ui, Sheets sheets, Layout layout, Apps apps, Launcher launcher, LauncherApps launcherApps, Folders folders,
          IconPicker icons, ClassicHome classic, Widgets widgets, Notices notices, Runnable refresh) {
        this.ui = ui;
        this.sheets = sheets;
        this.layout = layout;
        this.apps = apps;
        this.launcher = launcher;
        this.launcherApps = launcherApps;
        this.folders = folders;
        this.icons = icons;
        this.classic = classic;
        this.widgets = widgets;
        this.notices = notices;
        this.refresh = refresh;
    }

    private void saveFavourites(List<String> f) {
        if (!layout.saveFavourites(f).isEmpty()) ui.toast(ui.tr("The home screen is full"));
        refresh.run();
    }

    private void toggleSet(String key, String v) {
        layout.toggleSet(key, v);
        refresh.run();
    }

    void forEntry(String entry) {
        forEntry(entry, null);
    }

    /** An entry's long-press menu; inFolder: the folder whose pop-up it was pressed in, adding "Remove from this folder". */
    void forEntry(String entry, String inFolder) {
        if (entry.startsWith("f:")) folders.menu(entry.substring(2));
        else if (entry.startsWith("s:")) shortcut(entry, inFolder);
        else {
            Item it = apps.find(entry);
            if (it != null) app(it, inFolder);
        }
    }

    /** Long-press on a pinned shortcut or web app: put it in a folder, or take it away from where it is. */
    private void shortcut(String entry, String inFolder) {
        List<Sheets.Row> rows = new ArrayList<>();
        rows.add(item("folder", ui.tr("Add to pop-up folder…"), () -> folders.choose(entry)));
        rows.add(item("image", ui.tr("Icon…"), () -> icons.choose(entry)));
        if (inFolder != null) rows.add(removeFromFolder(entry, inFolder));
        else rows.add(item("close", ui.tr("Remove from favourites"), () -> {
            List<String> f = layout.favourites();
            f.remove(entry);
            if (layout.classic()) layout.removeFromHome(entry);
            saveFavourites(f);
        }));
        sheets.show(Entries.shortcutName(entry), rows);
    }

    private Sheets.Row removeFromFolder(String entry, String folder) {
        return item("close", ui.tr("Remove from this folder"), () -> folders.remove(folder, entry));
    }

    private void app(Item it, String inFolder) {
        List<String> fav = layout.favourites();
        boolean isFav = fav.contains(it.key), hidden = layout.inSet("hidden", it.key);
        List<ShortcutInfo> scs = apps.shortcuts(it);
        List<Sheets.Row> rows = new ArrayList<>();
        if (inFolder != null) rows.add(removeFromFolder(it.key, inFolder)); // where it was pressed: first
        // Always the same seven first, in the same order.
        rows.add(item("star", isFav ? ui.tr("Remove from favourites") : ui.tr("Add to favourites"), () -> {
            if (isFav) fav.remove(it.key); else fav.add(it.key);
            saveFavourites(fav);
        }));
        if (layout.classic()) {
            String[] slots = layout.dockSlots();
            int at = Arrays.asList(slots).indexOf(it.key), free = Arrays.asList(slots).indexOf("");
            rows.add(item("layers", at >= 0 ? ui.tr("Remove from dock") : ui.tr("Add to dock"), () -> {
                if (at >= 0) slots[at] = "";
                else if (free < 0) { ui.toast(ui.tr("The dock is full: remove an app first")); return; }
                else slots[free] = it.key;
                layout.saveDock(slots);
                refresh.run();
            }));
        }
        rows.add(item("folder", ui.tr("Add to pop-up folder…"), () -> folders.choose(it.key)));
        rows.add(item("edit", ui.tr("Rename"), () -> rename(it)));
        rows.add(item("image", ui.tr("App icon…"), () -> icons.choose(it.key)));
        rows.add(item("eyeoff", hidden ? ui.tr("Unhide") : ui.tr("Hide"), () -> {
            Set<String> h = new HashSet<>(apps.hidden());
            if (!h.remove(it.key)) { h.add(it.key); fav.remove(it.key); }
            ui.prefs.edit().putStringSet("hidden", h).apply();
            layout.saveFavourites(fav);
            if (!hidden && layout.classic()) layout.removeFromHome(it.key);
            refresh.run();
            ui.toast(ui.f(hidden ? "%s is back in the list." : "%s hidden. Search still finds it.", apps.label(it)));
        }));
        rows.add(item("info", ui.tr("App info"), () -> {
            try {
                launcherApps.startAppDetailsActivity(it.info.getComponentName(), it.info.getUser(), null, null);
            } catch (RuntimeException gone) { // uninstalled meanwhile, or its profile paused
                Ui.cantOpen(ui.a);
            }
        }));
        rows.add(item("gear", ui.tr("Ganimede settings"), ui::settings));
        rows.add(item("trash", ui.tr("Uninstall"), () -> launcher.uninstall(it)));
        rows.add(item(null, ui.tr("More"), null));
        if (isFav) {
            int at = fav.indexOf(it.key);
            if (at > 0) rows.add(item("up", ui.tr("Move up"), () -> { fav.set(at, fav.get(at - 1)); fav.set(at - 1, it.key); saveFavourites(fav); }));
            if (at < fav.size() - 1) rows.add(item("down", ui.tr("Move down"), () -> { fav.set(at, fav.get(at + 1)); fav.set(at + 1, it.key); saveFavourites(fav); }));
        }
        rows.add(item("music", (layout.inSet("music_apps", it.key) ? "✓ " : "") + ui.tr("Music app (show with headphones)"), () -> toggleSet("music_apps", it.key)));
        rows.add(item("pause", (layout.inSet("breaker_apps", it.key) ? "✓ " : "") + ui.tr("Break reminder"), () -> {
            toggleSet("breaker_apps", it.key);
            if (layout.inSet("breaker_apps", it.key) && Build.VERSION.SDK_INT >= 33)
                ui.a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, MainActivity.BREAK_PERMISSION);
        }));
        rows.add(item("bell", (layout.inSet("summary_apps", it.pkg) ? "✓ " : "") + ui.tr("Notification summary"), () -> {
            toggleSet("summary_apps", it.pkg);
            if (!layout.inSet("summary_apps", it.pkg)) return;
            boolean was = ui.prefs.getBoolean("summary", false);
            ui.prefs.edit().putBoolean("summary", true).apply();
            if (!Ui.notifAllowed(ui.a)) Ui.explainNotif(ui.a, () -> {
                toggleSet("summary_apps", it.pkg);
                ui.prefs.edit().putBoolean("summary", was).apply();
            });
        }));
        if (!scs.isEmpty()) {
            rows.add(item(null, ui.tr("Shortcuts"), null));
            for (int i = 0; i < Math.min(5, scs.size()); i++) {
                ShortcutInfo s = scs.get(i);
                String name = shortcutName(s);
                rows.add(item("shortcut", name, () -> {
                    try {
                        launcherApps.startShortcut(s, null, null);
                    } catch (Exception e) {
                        ui.toast(ui.tr("This shortcut can't be opened"));
                    }
                }));
            }
            rows.add(item("pin", ui.tr("Pin a shortcut to favourites…"), () -> {
                String[] names = new String[scs.size()];
                for (int i = 0; i < names.length; i++) names[i] = shortcutName(scs.get(i));
                ui.ask().setTitle(ui.tr("Pin shortcut"))
                        .setItems(names, (d, w) -> {
                            String entry = Safe.shortcutEntry(it.pkg, scs.get(w).getId(), names[w]);
                            if (entry == null) { ui.toast(ui.tr("This shortcut can't be opened")); return; }
                            fav.add(entry);
                            saveFavourites(fav);
                        }).show();
            }));
        }
        sheets.show(apps.label(it), rows);
    }

    private static String shortcutName(ShortcutInfo s) {
        return String.valueOf(s.getShortLabel() == null ? s.getId() : s.getShortLabel());
    }

    private void rename(Item it) {
        EditText input = new EditText(ui.a);
        input.setText(apps.label(it));
        input.setSelectAllOnFocus(true);
        ui.ask()
                .setTitle(ui.tr("Rename"))
                .setView(input)
                .setPositiveButton(ui.tr("Save"), (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) ui.prefs.edit().remove("rn:" + it.key).apply();
                    else ui.prefs.edit().putString("rn:" + it.key, n).apply();
                    refresh.run();
                })
                .setNeutralButton(ui.tr("Original name"), (d, w) -> {
                    ui.prefs.edit().remove("rn:" + it.key).apply();
                    refresh.run();
                })
                .setNegativeButton(ui.tr("Cancel"), null)
                .show();
    }

    /** Long-press on an empty part of the home screen; cell: the classic grid's empty cell pressed ("g:N"), or null. */
    void home(String cell) {
        boolean cl = layout.classic();
        List<Sheets.Row> rows = new ArrayList<>();
        rows.add(item("check", cl ? ui.tr("Choose home screen apps") : ui.tr("Choose favourites"), this::chooseFavourites));
        rows.add(item("folder", ui.tr("Create pop-up folder"), () -> folders.create(null, cell)));
        if (cl) {
            rows.add(item("plus", ui.tr("Create page"), classic::createPage));
            if (layout.pages() > 1 && classic.currentPage() > 0)
                rows.add(item("close", ui.tr("Delete page"), () -> classic.deletePage(classic.currentPage(), null)));
            rows.add(item("grid", ui.tr("Arrange pages"), classic::arrangePages));
            if (!classic.hasBlock("clock")) rows.add(item("clock", ui.tr("Add clock"), classic::addClock));
        }
        rows.add(item("widget", ui.tr("Add widget"), () -> widgets.pick(cl ? "classic:" + classic.currentPage() : "stack")));
        if (!cl) rows.add(item("close", ui.tr("Remove a widget"), widgets::removeDialog));
        rows.add(item("bell", ui.tr("Notifications"), () -> notices.show(null)));
        rows.add(item("image", ui.tr("Wallpaper"), () -> ui.open(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), ui.tr("Wallpaper")))));
        rows.add(item("gear", ui.tr("Ganimede settings"), ui::settings));
        sheets.show("Ganimede", rows);
    }

    void block(String id) {
        List<Sheets.Row> rows = new ArrayList<>();
        rows.add(item("grid", ui.tr("Resize"), () -> classic.resize(id)));
        rows.add(item("close", ui.tr("Remove"), () -> classic.removeBlock(id)));
        sheets.show(id.equals("clock") ? ui.tr("Clock") : ui.tr("Widget"), rows);
    }

    /**
     * Pick favourites on the fly: every app with a check box; ticked ones join the favourites (or the classic home
     * grid) in list order, while folders, shortcuts and the order of those already there are kept.
     */
    void chooseFavourites() {
        List<Item> all = apps.visible();
        List<String> fav = layout.favourites();
        boolean cl = layout.classic();
        String[] names = new String[all.size()];
        boolean[] on = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) {
            String k = all.get(i).key;
            names[i] = apps.label(all.get(i));
            on[i] = fav.contains(k);
        }
        ui.ask()
                .setTitle(cl ? ui.tr("Choose home screen apps") : ui.tr("Choose favourites"))
                .setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton(ui.tr("Save"), (d, w) -> {
                    List<String> keep = new ArrayList<>();
                    for (String k : fav) { // keep what's there, in its order, unless it was unticked
                        Item it = apps.find(k);
                        int at = it == null ? -1 : all.indexOf(it);
                        if (at < 0 || on[at]) keep.add(k);
                    }
                    for (int i = 0; i < names.length; i++) if (on[i] && !keep.contains(all.get(i).key)) keep.add(all.get(i).key);
                    saveFavourites(keep);
                })
                .setNegativeButton(ui.tr("Cancel"), null).show();
    }
}
