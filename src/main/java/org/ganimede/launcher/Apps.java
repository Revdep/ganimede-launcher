package org.ganimede.launcher;

import org.ganimede.launcher.core.Search;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The installed apps (every profile that isn't paused), with the names, icons and launch counts the home shows. */
final class Apps {
    static final class Item {
        final LauncherActivityInfo info;
        /** label: the app's own name, read once here (each read crosses into the system). */
        final String key, pkg, label, notifGroup;
        Drawable icon;
        boolean fromPack;
        /** The glyph of the app's category when it has no icon in the built-in set (issue 087), else null. */
        BuiltinIcon category;

        Item(LauncherActivityInfo info, String key) {
            this.info = info;
            this.key = key;
            pkg = info.getComponentName().getPackageName();
            notifGroup = NotifService.group(pkg, info.getUser());
            label = info.getLabel().toString();
        }
    }

    /** This profile's apps but Ganimede, A to Z by their own names: for the settings' lists and preview. */
    static List<Item> named(Context c) {
        List<Item> out = new ArrayList<>();
        for (LauncherActivityInfo info : c.getSystemService(LauncherApps.class).getActivityList(null, Process.myUserHandle()))
            if (!info.getComponentName().getPackageName().equals(c.getPackageName()))
                out.add(new Item(info, info.getComponentName().flattenToString()));
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private final Context c;
    private final SharedPreferences prefs;
    private final LauncherApps launcherApps;
    private final Handler ui;
    private List<Item> all = List.of();
    private Map<String, Item> byKey = Map.of(), byPkg = Map.of(), byGroup = Map.of();
    /** Counts loads: a slower, older load that finishes last must not replace a newer list. */
    private int loads;
    /** Pinned shortcuts' icons by "pkg|id", read once per load (two calls into the system each). */
    private final Map<String, Drawable> shortcutIcons = new HashMap<>();

    Apps(Context c, SharedPreferences prefs, LauncherApps launcherApps, Handler ui) {
        this.c = c;
        this.prefs = prefs;
        this.launcherApps = launcherApps;
        this.ui = ui;
    }

    /** Reads every app on a background thread, then (on the main thread, if no newer load started) runs done. */
    void load(Runnable done) {
        String pack = prefs.getString("icon_pack", "");
        int mine = ++loads;
        new Thread(() -> {
            try { Sunflower.reindex(c); } catch (RuntimeException e) { /* fewer results */ }
            UserManager um = c.getSystemService(UserManager.class);
            List<Item> out = new ArrayList<>();
            List<UserHandle> users;
            try { users = launcherApps.getProfiles(); } catch (RuntimeException e) { users = List.of(Process.myUserHandle()); }
            Icons.load(c, pack);
            Icons.preload(c, prefs); // a folder's or shortcut's picked built-in icon: drawn on the main thread at refreshAll
            for (UserHandle user : users) {
                    // Paused work profiles and a locked private space stay out of the list.
                    List<LauncherActivityInfo> list;
                    try {
                        if (!user.equals(Process.myUserHandle()) && um.isQuietModeEnabled(user)) continue;
                        list = launcherApps.getActivityList(null, user);
                    } catch (RuntimeException e) { // a profile going away right now
                        continue;
                    }
                    for (LauncherActivityInfo info : list) try {
                        if (info.getComponentName().getPackageName().equals(c.getPackageName())) continue;
                        Item it = new Item(info, info.getComponentName().flattenToString()
                                + (user.equals(Process.myUserHandle()) ? "" : "#" + um.getSerialNumberForUser(user)));
                        // An icon picked for this one app wins over the pack's and the app's own.
                        Drawable packed = Icons.picked(c, prefs, it.key);
                        if (packed == null) packed = Icons.fromPack(info.getComponentName());
                        it.fromPack = packed != null;
                        if (packed == null) it.category = Icons.category(prefs, info);
                        it.icon = packed != null ? packed : info.getIcon(0); // unbadged: Icons.apply badges what it draws
                        out.add(it);
                    } catch (RuntimeException oneApp) { // one app being updated can't take the whole list down
                        // left out until the next load
                    }
            }
            Map<String, Item> keys = new HashMap<>(), pkgs = new HashMap<>(), groups = new HashMap<>();
            for (Item it : out) { keys.putIfAbsent(it.key, it); pkgs.putIfAbsent(it.pkg, it); groups.putIfAbsent(it.notifGroup, it); }
            ui.post(() -> {
                if (mine != loads) return; // a newer load is on its way
                all = out;
                byKey = keys;
                byPkg = pkgs;
                byGroup = groups;
                shortcutIcons.clear();
                done.run();
            });
        }).start();
    }

    List<Item> all() {
        return all;
    }

    boolean isEmpty() {
        return all.isEmpty();
    }

    Item find(String key) {
        return byKey.get(key);
    }

    /** The first app of a package (for notifications, which name only the package). */
    Item byPackage(String pkg) {
        return byPkg.get(pkg);
    }

    /** The first app of a notification group (package and profile, see NotifService.group). */
    Item byGroup(String group) {
        return byGroup.get(group);
    }

    String label(Item it) {
        return label(prefs, it);
    }

    /** The label of the app a "package/class#user" key names; null when it is no longer installed. */
    static String labelOf(Context c, String key) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            return pm.getActivityInfo(ComponentName.unflattenFromString(key.split("#")[0]), 0).loadLabel(pm).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** An app's name: renamed by the person ("rn:<key>"), or its own. */
    static String label(SharedPreferences p, Item it) {
        return p.getString("rn:" + it.key, it.label);
    }

    int uses(Item it) {
        return prefs.getInt("use:" + it.key, 0);
    }

    Set<String> hidden() {
        return prefs.getStringSet("hidden", Set.of());
    }

    /** Apps not hidden, A to Z by section letter; with smart sorting the most used first within each letter. */
    List<Item> visible() {
        return visible(all, prefs);
    }

    /** The same for any list of apps (the Settings preview has its own). */
    static List<Item> visible(List<Item> all, SharedPreferences p) {
        Set<String> hidden = p.getStringSet("hidden", Set.of());
        List<Item> shown = new ArrayList<>();
        for (Item it : all) if (!hidden.contains(it.key)) shown.add(it);
        return Search.order(shown, it -> label(p, it), it -> p.getInt("use:" + it.key, 0), p.getBoolean("smart_sort", false));
    }

    /** An app's own shortcuts (dynamic and from its manifest); empty when this isn't the default home app. */
    List<ShortcutInfo> shortcuts(Item it) {
        try {
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setPackage(it.pkg)
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> l = launcherApps.getShortcuts(q, it.info.getUser());
            return l == null ? List.of() : l;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** One pinned shortcut's icon, or null (asked again next time: Ganimede may not be the home app yet). */
    Drawable shortcutIcon(String pkg, String id) {
        String k = pkg + "|" + id;
        Drawable d = shortcutIcons.get(k);
        if (d != null) return d;
        try {
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(List.of(id))
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> l = launcherApps.getShortcuts(q, Process.myUserHandle());
            if (l != null && !l.isEmpty()) d = launcherApps.getShortcutIconDrawable(l.get(0), c.getResources().getDisplayMetrics().densityDpi);
        } catch (Exception e) {
            // Only the default home app may read shortcuts: a plain tile instead.
        }
        if (d != null) shortcutIcons.put(k, d);
        return d;
    }
}
