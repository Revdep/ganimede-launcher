package org.ganimede.launcher;

import org.ganimede.launcher.core.Grid;
import org.ganimede.launcher.core.Pins;
import org.ganimede.launcher.core.Blocks;
import org.ganimede.launcher.core.DrawerSettings;
import org.ganimede.launcher.core.IconLook;
import org.ganimede.launcher.core.Suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * What the home screens hold, as saved in settings: the favourites (List mode), the grid pages and the dock (Classic
 * mode), and the pop-up folders. Entries are apps ("component", "component#user"), folders ("f:id") or pinned
 * shortcuts ("s:pkg|id|name"). Saving here doesn't redraw anything.
 */
final class Layout {
    private final Context c;
    private final SharedPreferences prefs;

    Layout(Context c, SharedPreferences prefs) {
        this.c = c;
        this.prefs = prefs;
    }

    boolean classic() {
        return "classic".equals(prefs.getString("mode", "list"));
    }

    boolean drawerOn() {
        return prefs.getBoolean("drawer", true);
    }

    /** The app pages (drawer off: every page after the first holds apps) may look different from the home's first page. */
    boolean appPage(int page) { return page > 0 && !drawerOn(); }

    boolean drawerFollowsHome() { return prefs.getBoolean("drawer_follow", true); }

    String drawerKey(String homeKey) { return DrawerSettings.key(drawerFollowsHome(), homeKey); }

    int drawerInt(String homeKey, int def) {
        return prefs.getInt(drawerKey(homeKey), prefs.getInt(homeKey, def));
    }

    /** The icon settings of the app pages: the drawer's own, each falling back to the home's. */
    IconLook drawerIconLook() { return IconLook.read(k -> drawerString(k, null)); }

    String drawerString(String homeKey, String def) {
        return prefs.getString(drawerKey(homeKey), prefs.getString(homeKey, def));
    }

    boolean drawerLabels() {
        boolean home = !prefs.getBoolean("hide_labels", false);
        return DrawerSettings.labels(drawerFollowsHome(), home, prefs.getBoolean("drawer_labels", home));
    }

    /**
     * Grid size by screen size, the same in both orientations (5x5 grid and 5 dock icons from 406x694dp screens, 4x4 and
     * 4 below, 3x3 on tiny ones), shared by the home screen, the settings and their preview.
     */
    static int[] gridDefaults(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Grid.defaults(m.widthPixels / m.density, m.heightPixels / m.density);
    }

    /** How much of the screen the favourites column may take before it scrolls, in percent. */
    static int appsShare(SharedPreferences p) {
        return p.getInt("list_apps_h", 58);
    }

    static int appsHeight(SharedPreferences p, int screenHeight) {
        return screenHeight * appsShare(p) / 100;
    }

    /** Grid size unless chosen in settings: columns, rows, dock slots (gridDefaults). */
    int[] defaults() {
        return gridDefaults(c);
    }

    int cols() { return prefs.getInt("grid_cols", defaults()[0]); }
    int rows() { return prefs.getInt("grid_rows", defaults()[1]); }
    int dockCount() { return prefs.getInt("dock_count", defaults()[2]); }
    int drawerCols() { return prefs.getInt("drawer_cols", defaults()[0]); }
    int pages() { return Math.max(1, prefs.getInt("grid_pages", 1)); }
    /** Cells on one page. */
    int per() { return cols() * rows(); }

    void setPages(int n) {
        prefs.edit().putInt("grid_pages", n).apply();
    }

    List<String> favourites() {
        return lines("fav");
    }

    /** Whether a favourite or grid entry still exists: a folder that is kept, a shortcut, or an app {@code app} knows. */
    boolean entryExists(String entry, java.util.function.Predicate<String> app) {
        if (entry.startsWith("f:")) return hasFolder(entry.substring(2));
        return entry.startsWith("s:") || app.test(entry);
    }

    List<String> saveFavourites(List<String> f) {
        List<String> before = favourites(), pinnedBefore = pinnable();
        Set<String> unsuggested = Suggestions.unsuggested(
                prefs.getStringSet("unsuggested", Set.of()), before, f);
        if (!classic()) {
            prefs.edit().putString("fav", String.join("\n", f)).putStringSet("unsuggested", unsuggested).apply();
            pinShortcuts(pinnedBefore);
            return List.of();
        }
        Set<Integer> reserved = blockCells(); // migration may first make room for the clock
        Map<Integer, String> g = grid();
        List<String> dock = dock();
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(f);
        dock.removeIf(removed::contains);
        List<String> elsewhere = new ArrayList<>(dock);
        for (String id : folderIds()) elsewhere.addAll(folderApps(id));
        List<String> missing = Grid.syncFavourites(g, reserved, elsewhere, before, f, per());
        prefs.edit().putString("fav", String.join("\n", f)).putStringSet("unsuggested", unsuggested)
                .putString("grid", gridJson(g).toString())
                .putString("dock", String.join("\n", dock)).apply();
        pinShortcuts(pinnedBefore);
        return missing;
    }

    /** Every shortcut entry we keep: favourites, an existing classic grid, dock, folders and the gesture/button actions. */
    private List<String> pinnable() {
        List<String> all = new ArrayList<>(favourites());
        if (classic() && prefs.contains("grid")) all.addAll(grid().values()); // never seeds a grid from here
        all.addAll(dock());
        for (String id : folderIds()) all.addAll(folderApps(id));
        List<String> actions = new ArrayList<>();
        for (Object v : prefs.getAll().values()) if (v instanceof String) actions.add((String) v);
        all.addAll(Pins.fromActions(actions));
        return all;
    }

    /** Tells Android which shortcuts we keep (pinned ones outlive the app retiring them), for the packages that changed. */
    private void pinShortcuts(List<String> before) {
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (!la.hasShortcutHostPermission()) return; // not the home app
        List<String> all = pinnable();
        for (String pkg : Pins.changed(before, all)) {
            if (pkg.equals(c.getPackageName())) continue;
            try {
                la.pinShortcuts(pkg, Pins.ids(all, pkg), Process.myUserHandle());
            } catch (IllegalStateException | SecurityException e) {
                // Lost the home role meanwhile: the entry still opens while the app offers the shortcut.
            }
        }
    }

    /** Adds favourites missing from an older classic grid without changing any existing placement. */
    void ensureFavourites() { if (classic()) saveFavourites(favourites()); }

    private List<String> lines(String key) {
        String s = prefs.getString(key, "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split("\n")));
    }

    // ---- the grid: cell index → entry (core Grid); JSON only here, at the edge

    /** The grid's cells. First use: the favourites, in order. */
    Map<Integer, String> grid() {
        if (!prefs.contains("grid")) {
            Map<Integer, String> g = Grid.seed(favourites(), per() * pages());
            saveGrid(g);
            return g;
        }
        Map<Integer, String> g = new TreeMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString("grid", "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                try { g.put(Integer.parseInt(k), o.optString(k)); } catch (NumberFormatException notACell) { /* skip */ }
            }
        } catch (JSONException e) {
            // unreadable: an empty grid
        }
        return g;
    }

    void saveGrid(Map<Integer, String> g) {
        prefs.edit().putString("grid", gridJson(g).toString()).apply();
    }

    // ---- blocks: widgets and the classic clock, separate from the old icon grid

    List<Blocks.Item> blocks() {
        if (!prefs.contains("blocks")) {
            if (!classic()) return new ArrayList<>();
            String old = prefs.getString("stack", "");
            List<String> widgets = old.isEmpty() ? List.of() : Arrays.asList(old.split(","));
            Blocks.Result migrated = Blocks.migrate(grid(), widgets, true,
                    pages(), cols(), rows());
            savePlacement(migrated.blocks, migrated.cells, migrated.pages);
            return migrated.blocks;
        }
        List<Blocks.Item> out = new ArrayList<>(), shrunk = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("blocks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id");
                Blocks.Item saved = new Blocks.Item(id, o.optInt("page", -1), o.optInt("col", -1),
                        o.optInt("row", -1), o.optInt("colSpan", 0), o.optInt("rowSpan", 0));
                Blocks.Item b = Blocks.clamp(saved, cols(), rows()); // the grid may have fewer columns or rows now
                if (id.isEmpty() || b == null || b.page >= pages() || Blocks.contains(out, id)) continue;
                if (b.equals(saved)) out.add(b); else shrunk.add(b);
            }
        } catch (JSONException ignored) {
            // Unreadable blocks are an empty list; the icon grid remains intact.
        }
        if (shrunk.isEmpty()) return out;
        // Put each shrunk block back through the normal placement, so icons it now covers make room.
        Map<Integer, String> cells = grid();
        int pages = pages();
        for (Blocks.Item b : shrunk) {
            Blocks.Result r = Blocks.put(b, pages, cols(), rows(), out, cells);
            if (!r.changed) continue; // no room: hidden for now, never deleted
            out = r.blocks;
            cells = r.cells;
            pages = r.pages;
        }
        // This is only the drawing for the smaller grid. The saved rectangles and icon cells return when it grows.
        return out;
    }

    void saveBlocks(List<Blocks.Item> blocks) {
        prefs.edit().putString("blocks", blocksJson(blocks).toString()).apply();
    }

    void savePlacement(List<Blocks.Item> blocks, Map<Integer, String> cells, int pageCount) {
        prefs.edit().putString("blocks", blocksJson(blocks).toString())
                .putString("grid", gridJson(cells).toString()).putInt("grid_pages", pageCount).apply();
    }

    /** The grid cells (page * per + row * cols + col) that widgets and the clock cover. */
    Set<Integer> blockCells() {
        return Blocks.cells(blocks(), cols(), rows());
    }

    Blocks.Result placeBlock(String id, int colSpan, int rowSpan, int page) {
        return Blocks.add(id, colSpan, rowSpan, page, pages(), cols(), rows(), blocks(), grid());
    }

    List<String> widgetStack() {
        String s = prefs.getString("stack", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    boolean addStackWidget(String id) {
        List<String> stack = widgetStack();
        if (stack.size() >= 4) return false;
        stack.add(id);
        prefs.edit().putString("stack", String.join(",", stack)).apply();
        return true;
    }

    private static JSONObject gridJson(Map<Integer, String> cells) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<Integer, String> e : cells.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
        } catch (JSONException ignored) { }
        return o;
    }

    private static JSONArray blocksJson(List<Blocks.Item> blocks) {
        JSONArray a = new JSONArray();
        for (Blocks.Item b : blocks) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", b.id).put("page", b.page).put("col", b.col).put("row", b.row)
                        .put("colSpan", b.colSpan).put("rowSpan", b.rowSpan);
                a.put(o);
            } catch (JSONException ignored) { }
        }
        return a;
    }

    String[] dockSlots() {
        String[] slots = new String[dockCount()];
        Arrays.fill(slots, "");
        List<String> d = lines("dock");
        for (int i = 0; i < Math.min(slots.length, d.size()); i++) slots[i] = d.get(i);
        return slots;
    }

    List<String> dock() {
        return lines("dock");
    }

    void saveDock(String[] slots) {
        prefs.edit().putString("dock", String.join("\n", slots)).apply();
    }

    // ---- pop-up folders: "folder:<id>" → {"name", "where", "apps": [...], "widgets": [...]}

    JSONObject folder(String id) {
        try {
            return new JSONObject(prefs.getString("folder:" + id, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    boolean hasFolder(String id) {
        return prefs.contains("folder:" + id);
    }

    void saveFolder(String id, JSONObject f) {
        prefs.edit().putString("folder:" + id, f.toString()).apply();
    }

    /** Forgets a folder and removes every home-screen reference to it. */
    void deleteFolder(String id) {
        String entry = "f:" + id;
        List<String> fav = favourites();
        fav.remove(entry);
        Map<Integer, String> g = grid();
        g.values().removeIf(entry::equals);
        List<String> dock = dock();
        dock.removeIf(entry::equals);
        prefs.edit().remove("folder:" + id).putString("fav", String.join("\n", fav))
                .putString("grid", gridJson(g).toString()).putString("dock", String.join("\n", dock)).apply();
    }

    void removeFromHome(String entry) {
        Map<Integer, String> g = grid();
        g.values().removeIf(entry::equals);
        List<String> dock = dock();
        dock.removeIf(entry::equals);
        prefs.edit().putString("grid", gridJson(g).toString()).putString("dock", String.join("\n", dock)).apply();
    }

    List<String> folderIds() {
        List<String> ids = new ArrayList<>();
        for (String k : prefs.getAll().keySet()) if (k.startsWith("folder:")) ids.add(k.substring(7));
        return ids;
    }

    List<String> folderApps(String id) {
        return strings(folder(id).optJSONArray("apps"));
    }

    /** Changes a folder's entries and saves it; false (nothing saved) if it can't be written. */
    boolean editFolderApps(String id, Consumer<List<String>> change) {
        JSONObject f = folder(id);
        List<String> a = strings(f.optJSONArray("apps"));
        change.accept(a);
        try { f.put("apps", new JSONArray(a)); } catch (Exception e) { return false; }
        saveFolder(id, f);
        return true;
    }

    /** A new folder ("where": fav) holding these entries; returns its id, or null if it can't be written. */
    String newFolder(String name, List<String> apps) {
        String id = String.valueOf(System.currentTimeMillis());
        JSONObject f = new JSONObject();
        try {
            f.put("name", name).put("where", "fav").put("apps", new JSONArray(apps));
        } catch (Exception e) {
            return null;
        }
        saveFolder(id, f);
        return id;
    }

    static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    // ---- sets of entries chosen in menus (music apps, break reminder, summary)

    boolean inSet(String key, String v) {
        return prefs.getStringSet(key, Set.of()).contains(v);
    }

    void toggleSet(String key, String v) {
        Set<String> s = new HashSet<>(prefs.getStringSet(key, Set.of()));
        if (!s.remove(v)) s.add(v);
        prefs.edit().putStringSet(key, s).apply();
    }
}
