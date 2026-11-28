package org.ganimede.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import org.ganimede.launcher.core.IconSet;
import org.ganimede.launcher.core.LimitedInputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiPredicate;

/** Which icon an entry has: icon packs (the common appfilter.xml format, or the built-in set of core/IconSet), picked icons, categories. Drawing is Icons. */
final class IconPacks {
    private static final String[] PACK_ACTIONS = {"org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME"};

    private static final Object LOAD_LOCK = new Object();
    static final String CATEGORY_KEY = "icon_category";
    private static volatile String loadedPack;
    private static volatile Pack loaded = new Pack(null, null, Map.of(), null);
    private static final java.util.concurrent.atomic.AtomicInteger packRevision = new java.util.concurrent.atomic.AtomicInteger();

    private static final class Pack {
        final String name;
        final Resources resources;
        final Map<String, String> icons;
        final IconSet set; // the built-in set, instead of resources and icons
        Pack(String name, Resources resources, Map<String, String> icons, IconSet set) {
            this.name = name;
            this.resources = resources;
            this.icons = icons;
            this.set = set;
        }
    }

    /** Installed icon packs: package name → label. */
    static Map<String, String> packs(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> out = new LinkedHashMap<>();
        for (String a : PACK_ACTIONS) {
            List<ResolveInfo> l = pm.queryIntentActivities(new Intent(a), 0);
            for (ResolveInfo r : l) out.put(r.activityInfo.packageName, r.loadLabel(pm).toString());
        }
        return out;
    }

    /** Each icon pack's resources, opened once (a pack offers thousands of icons). */
    private static final Map<String, Resources> PACK_RES = new HashMap<>();

    private static Resources resources(Context c, String pack) throws PackageManager.NameNotFoundException {
        synchronized (PACK_RES) {
            Resources cached = PACK_RES.get(pack);
            if (cached != null) return cached;
        }
        Resources found = c.getPackageManager().getResourcesForApplication(pack);
        synchronized (PACK_RES) {
            Resources cached = PACK_RES.get(pack);
            if (cached != null) return cached;
            PACK_RES.put(pack, found);
            return found;
        }
    }

    /** Hands each item of a pack's appfilter (component, drawable) to `item` while it returns true. */
    private static void readAppfilter(Resources r, String pack, BiPredicate<String, String> item) throws Exception {
        int id = r.getIdentifier("appfilter", "xml", pack);
        InputStream in = null;
        XmlPullParser x = null;
        try {
            if (id != 0) {
                x = r.getXml(id);
            } else {
                in = r.getAssets().open("appfilter.xml");
                x = XmlPullParserFactory.newInstance().newPullParser();
                x.setInput(in, "UTF-8");
            }
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next())
                if (e == XmlPullParser.START_TAG && "item".equals(x.getName())
                        && !item.test(x.getAttributeValue(null, "component"), x.getAttributeValue(null, "drawable"))) return;
        } finally {
            if (x instanceof XmlResourceParser) ((XmlResourceParser) x).close(); // a resource parser holds a native block
            if (in != null) in.close();
        }
    }

    /** Reads the pack's appfilter once; later calls are free. */
    static void load(Context c, String pack) {
        synchronized (LOAD_LOCK) {
            if (pack == null ? loadedPack == null : pack.equals(loadedPack)) return;
            int revision = packRevision.get();
            Resources res = null;
            Map<String, String> icons = new HashMap<>();
            IconSet set = null;
            try {
                if (IconSet.PACK.equals(pack)) {
                    set = builtin(c);
                } else if (pack != null && !pack.isEmpty()) {
                    res = resources(c, pack);
                    readAppfilter(res, pack, (comp, draw) -> {
                        if (comp == null || draw == null || !comp.startsWith("ComponentInfo{")) return true;
                        ComponentName cn = ComponentName.unflattenFromString(comp.substring(14, comp.length() - 1));
                        if (cn != null) icons.put(cn.flattenToString(), draw);
                        return true;
                    });
                }
                if (revision != packRevision.get()) return;
                loaded = new Pack(pack, res, Map.copyOf(icons), set);
                loadedPack = pack;
            } catch (Exception e) {
                if (revision != packRevision.get()) return;
                loaded = new Pack(null, null, Map.of(), null);
                loadedPack = null; // a broken pack falls back now and is tried again on the next load
            }
        }
    }

    private static volatile IconSet builtin;
    /**
     * One drawable per icon of the built-in set (pack, picked, category): the same id gives the same object, so the
     * render cache, keyed by object, draws it once (apps of a category share its bitmap). Bounded: the picker browses all.
     */
    private static final android.util.LruCache<Integer, BuiltinIcon> GLYPHS = new android.util.LruCache<>(1024);

    /** @throws IllegalArgumentException when the icon's record is damaged */
    static BuiltinIcon glyph(IconSet set, int id) {
        BuiltinIcon g = GLYPHS.get(id);
        if (g == null) GLYPHS.put(id, g = new BuiltinIcon(set.icon(id))); // two threads may both decode it: the last one stays
        return g;
    }

    /**
     * The built-in set, read once: the home's pack, the icon picker and picked icons share it. Called on background
     * threads only (a 4 MB read); on the main thread {@link #named} uses what is already read.
     */
    static IconSet builtin(Context c) throws IOException {
        IconSet set = builtin;
        if (set == null) synchronized (Icons.class) { // two threads must not each read the 4 MB
            set = builtin;
            if (set == null) {
                try (InputStream in = c.getAssets().open("icons.bin")) {
                    builtin = set = IconSet.of(LimitedInputStream.readAll(in, 16 << 20)); // the set is 4 MB
                }
            }
        }
        return set;
    }

    /** Reads the built-in set now (Apps.load, in the background) when a picked icon needs it and the pack doesn't. */
    static void preload(Context c, SharedPreferences p) {
        try {
            if (IconSet.needed(p.getAll())) builtin(c);
        } catch (IOException | RuntimeException damaged) { // picked icons fall back to the usual ones
        }
    }

    /** Icon pickers open: they read the built-in set on the main thread, so a trim must not release it under them. */
    private static final java.util.concurrent.atomic.AtomicInteger PICKERS = new java.util.concurrent.atomic.AtomicInteger();

    static void pickerOpen(boolean open) {
        PICKERS.addAndGet(open ? 1 : -1);
    }

    /** An app was installed or updated: if it's an icon pack, it's read again on the next load. */
    static void forget(String pkg) {
        if (pkg != null && pkg.equals(loadedPack)) {
            packRevision.incrementAndGet();
            loadedPack = null;
            loaded = new Pack(null, null, Map.of(), null);
        }
        synchronized (PACK_RES) { PACK_RES.remove(pkg); }
    }

    /** The pack's icon for a component, or null. */
    static Drawable fromPack(ComponentName cn) {
        Pack pack = loaded;
        if (pack.set != null) {
            OptionalInt id = pack.set.index().find(cn.getPackageName(), cn.getClassName());
            try {
                return id.isPresent() ? glyph(pack.set, id.getAsInt()) : null;
            } catch (RuntimeException damaged) { // a bad record: the app's own icon
                return null;
            }
        }
        if (pack.resources == null) return null;
        String name = pack.icons.get(cn.flattenToString());
        if (name == null) return null;
        int id = pack.resources.getIdentifier(name, "drawable", pack.name);
        try {
            return id == 0 ? null : pack.resources.getDrawable(id, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The built-in glyph for an app that has no icon in the set (issue 087): its Play category from the set's tables, else
     * the one its manifest declares. Null with another pack, with "Category icons" off, or when the app has no category.
     */
    static BuiltinIcon category(SharedPreferences p, LauncherActivityInfo info) {
        Pack pack = loaded;
        IconSet set = pack.set;
        if (set == null || !p.getBoolean(CATEGORY_KEY, true)) return null;
        OptionalInt id = set.categoryGlyph(info.getComponentName().getPackageName(), info.getApplicationInfo().category);
        try {
            return id.isPresent() ? glyph(set, id.getAsInt()) : null;
        } catch (RuntimeException damaged) {
            return null;
        }
    }

    /** What {@link Apps#resolveIcon} depends on besides the picked icon: the settings, and whether the pack and the built-in set were read yet. */
    static String resolveStamp(SharedPreferences p) {
        return loadKey(p) + "|" + loadedPack + "|" + (builtin != null);
    }

    /** What Apps.load was last run for: a change of either makes the home read its apps again. */
    static String loadKey(SharedPreferences p) {
        return p.getString("icon_pack", "") + "|" + p.getBoolean(CATEGORY_KEY, true);
    }

    /**
     * Every icon a pack offers: the built-in set's names, or the drawable names an installed pack's appfilter lists,
     * without repeats. No cap (issue 081): names are only strings, and the picker draws only the cells on screen.
     */
    static java.util.List<String> packIcons(Context c, String pack) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        try {
            if (IconSet.PACK.equals(pack)) return builtin(c).names();
            readAppfilter(resources(c, pack), pack, (comp, draw) -> {
                if (draw != null) out.add(draw);
                return true;
            });
        } catch (Exception ignored) {
            // Unreadable pack: nothing to offer.
        }
        return new java.util.ArrayList<>(out);
    }

    /**
     * An icon named "pack|drawable" (how a picked icon is stored; the built-in set's by name, its ids change between
     * versions), or null: a name the set no longer has, or the set not read yet (see {@link #preload}), falls back to
     * the entry's usual icon.
     */
    static Drawable named(Context c, String packAndName) {
        String[] pp = packAndName.split("\\|", 2);
        if (pp.length != 2) return null;
        try {
            if (IconSet.PACK.equals(pp[0])) {
                IconSet set = builtin;
                OptionalInt id = set == null ? OptionalInt.empty() : set.id(pp[1]);
                return id.isPresent() ? glyph(set, id.getAsInt()) : null;
            }
            Resources r = resources(c, pp[0]);
            int id = r.getIdentifier(pp[1], "drawable", pp[0]);
            return id == 0 ? null : r.getDrawable(id, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** The icon picked for an entry from an installed pack (stored under "icon:<entry>"), or null. */
    static Drawable picked(Context c, SharedPreferences p, String entry) {
        return named(c, p.getString(IconSet.pickedKey(entry), ""));
    }


    /** Memory is short and no screen of ours shows: the picked glyphs go, and the 4 MB set too unless the pack or a picked icon uses it. */
    static void trim(boolean background, SharedPreferences p) {
        if (background) GLYPHS.evictAll();
        if (background && loaded.set == null && PICKERS.get() == 0 && !IconSet.needed(p.getAll())) builtin = null;
    }

    /** The built-in set when it is the loaded pack, else null. */
    static IconSet loadedSet() {
        return loaded.set;
    }

    private IconPacks() {}
}
