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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.widget.ImageView;

import org.ganimede.launcher.core.GlyphFit;
import org.ganimede.launcher.core.IconCache;
import org.ganimede.launcher.core.IconLook;
import org.ganimede.launcher.core.IconMask;
import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.IconSet;
import org.ganimede.launcher.core.IconSource;
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

/** Icon packs (the common appfilter.xml format, or the built-in set of core/IconSet) and icon shapes. */
final class Icons {
    /** The names of IconLook.GLYPHS, CONTAINERS, FILLS and COLORS, in their order. */
    static final String[] GLYPH_NAMES = {"Original", "Line", "Bold", "Sharp", "Duotone", "Hollow", "Dots (minimal)"};
    static final String[] CONTAINER_NAMES = {"No plate", "Squircle", "Circle", "Rounded square", "Teardrop"};
    static final String[] FILL_NAMES = {"Solid", "Soft"};
    static final String[] COLOR_NAMES = {"Theme colour", "Text colour"};
    /**
     * A number for each icon object, never reused (identity hash codes can repeat, and a freed icon's code can come
     * back for another one): the cache below can't hand out the wrong app's picture.
     */
    private static final java.util.Map<Drawable, Integer> IDS = new java.util.WeakHashMap<>();
    private static int nextId;

    private static int idOf(Drawable d) {
        synchronized (IDS) {
            Integer i = IDS.get(d);
            if (i == null) IDS.put(d, i = ++nextId);
            return i;
        }
    }

    /** Shaped icons are drawn once and reused: no bitmap work while scrolling. Sized for the device on the first apply. */
    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<String, Bitmap>(IconCache.bytes(0)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static boolean sized; // main thread only
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

    /** The Icons settings' Calendar from the built-in set, decoded once. */
    private static volatile BuiltinIcon sample;

    /**
     * The Icons settings' sample of a built-in glyph (Calendar) in the view: at once when the set was read, else the
     * placeholder first and the sample once the set is read on the render thread (never on the UI thread).
     */
    static void applySample(ImageView v, Drawable placeholder, IconLook look, int fg, int accent, int size) {
        BuiltinIcon s = sample;
        if (s != null) { apply(v, s, false, null, null, look, fg, accent, size); return; }
        v.setImageDrawable(placeholder); // as it is: drawn in the look, it would be thrown away at once
        Context c = v.getContext().getApplicationContext();
        RENDER.execute(() -> {
            try {
                if (sample == null) { // one render thread: the tiles queued after the first find it decoded
                    IconSet set = loaded.set != null ? loaded.set : builtin(c); // read here, on the render thread
                    sample = glyph(set, set.index().find("com.google.android.calendar",
                            "com.android.calendar.AllInOneActivity").orElse(0));
                }
                MAIN.post(() -> apply(v, sample, false, null, null, look, fg, accent, size));
            } catch (IOException | RuntimeException e) {
                // an unreadable set: the placeholder stays
            }
        });
    }

    private static volatile IconSet builtin;
    /**
     * One drawable per icon of the built-in set (pack, picked, category): the same id gives the same object, so the
     * render cache, keyed by object, draws it once (apps of a category share its bitmap). Bounded: the picker browses all.
     */
    private static final android.util.LruCache<Integer, BuiltinIcon> GLYPHS = new android.util.LruCache<>(1024);

    /** @throws IllegalArgumentException when the icon's record is damaged */
    private static BuiltinIcon glyph(IconSet set, int id) {
        BuiltinIcon g = GLYPHS.get(id);
        if (g == null) GLYPHS.put(id, g = new BuiltinIcon(set.icon(id))); // two threads may both decode it: the last one stays
        return g;
    }

    /**
     * The built-in set, read once: the home's pack, the icon picker and picked icons share it. Called on background
     * threads only (a 4 MB read); on the main thread {@link #named} uses what is already read.
     */
    private static IconSet builtin(Context c) throws IOException {
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

    /**
     * Memory is short: fewer drawn icons; with no screen of ours showing, none, and the 4 MB set too unless the pack or a
     * picked icon uses it (read again when needed). Hiding the home alone frees nothing: it comes back at once.
     */
    static void trim(int level, SharedPreferences p) {
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                || level < android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return;
        boolean background = level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
        CACHE.trimToSize(background ? 0 : CACHE.size() / 2);
        if (background) GLYPHS.evictAll();
        if (background && loaded.set == null && PICKERS.get() == 0 && !IconSet.needed(p.getAll())) builtin = null;
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

    /** The icon settings of the home (the drawer's own: Layout.drawerIconLook). */
    static IconLook look(SharedPreferences p) {
        return IconLook.read(k -> p.getString(k, null));
    }

    /**
     * Puts an icon into an ImageView as the icon model says (glyph treatment, container, fill, colour). {@code user}: the
     * profile of the app the icon is for (null: not an app's), whose badge goes on another profile's icon.
     */
    static void apply(ImageView v, Drawable icon, boolean fromPack, BuiltinIcon category, UserHandle user, IconLook look, int fg, int accent, int size) {
        if (!sized) { // a drawer of 150 icons at 80 dp on 3.5x is 47 MB: more than a fixed cache holds
            sized = true;
            CACHE.resize(IconCache.bytes(v.getContext().getSystemService(android.app.ActivityManager.class).getMemoryClass()));
        }
        int px = size <= 0 ? 96 : size;
        v.setBackground(null);
        v.setPadding(0, 0, 0, 0);
        IconLook.Colours colours = look.colours(accent, fg);
        Context c = v.getContext();
        if (look.glyph().equals("dot")) { // a dot in C, 3/10 of the icon, with its profile's badge like any icon
            String key = "dot|" + colours.glyph() + "|" + user + "|" + px;
            Bitmap b = CACHE.get(key);
            if (b == null) {
                b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(colours.glyph());
                int pad = px * 7 / 20;
                new Canvas(b).drawOval(pad, pad, px - pad, px - pad, paint);
                CACHE.put(key, b = badged(c, b, user));
            }
            v.setImageDrawable(new BitmapDrawable(null, b));
            return;
        }
        Drawable mono = Build.VERSION.SDK_INT >= 33 && icon instanceof AdaptiveIconDrawable ? ((AdaptiveIconDrawable) icon).getMonochrome() : null;
        // the silhouette is judged once drawn, on the render thread: until then ALPHA stands for "silhouette or greys"
        IconSource source = IconSource.pick(fromPack, s -> s == IconSource.ARCTICONS ? icon instanceof BuiltinIcon
                : s == IconSource.CATEGORY ? category != null && !look.glyph().equals("original")
                : s != IconSource.MONOCHROME || mono != null);
        if (source == IconSource.ARCTICONS || source == IconSource.CATEGORY) { // built-in geometry, with the treatment's stroke (original draws as Line)
            Drawable glyph = source == IconSource.CATEGORY ? category : icon; // the app has no entry: its category's glyph
            BuiltinIcon fresh = (BuiltinIcon) copy(glyph); // its own tint, alpha and look: the shared one stays as it is
            renderInto(v, ((BuiltinIcon) plain(glyph, colours.glyph())).look(look), colours.alpha(), user, idOf(glyph) + "|" + user + "|" + look + "|" + colours.glyph() + "|" + colours.fill() + "|" + px, () -> {
                Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                fresh.render(new Canvas(b), look, colours.glyph(), colours.fill(), px);
                return b;
            });
        } else if (look.glyph().equals("original")) { // the app's own icon, cut to the container if there is one
            if (!look.container().equals("none") && icon instanceof AdaptiveIconDrawable) v.setImageDrawable(clip(c, (AdaptiveIconDrawable) icon, look.container(), px, user));
            else if (user == null || user.equals(Process.myUserHandle())) v.setImageDrawable(copy(icon));
            else renderInto(v, copy(icon), 0xFF, user, idOf(icon) + "|" + user + "|own|" + px, () -> { // the badge once, in the cache, not at every bind
                Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                draw(new Canvas(b), icon, px, false);
                return b;
            });
            return;
        } else { // a glyph from the app's own icon: its monochrome layer, silhouette or greys (the same for every stroke)
            boolean tintGreys = look.plated() || look.color().equals("accent"); // today's Monochrome kept plain greys
            int tint = colours.glyph();
            boolean fit = look.plated();
            Drawable layer = source == IconSource.MONOCHROME ? mono : null;
            Drawable placeholder = copy(layer != null ? layer : icon); // the monochrome layer already looks as it will
            if (layer != null) placeholder.setTint(tint);
            renderInto(v, placeholder, colours.alpha(), user, idOf(icon) + "|" + user + "|" + source + "|" + fit + "|" + tintGreys + "|" + tint + "|" + px,
                    () -> styled(icon, layer, fit, tint, tintGreys, px));
        }
        if (look.plated()) v.setBackground(plate(look.container(), colours, px));
    }

    /** An icon of another profile's app with that profile's badge (the work briefcase); others as they are. */
    static Drawable badged(Context c, Drawable d, UserHandle user) {
        if (user == null || user.equals(Process.myUserHandle())) return d;
        try {
            return c.getPackageManager().getUserBadgedIcon(d, user);
        } catch (RuntimeException gone) { // a profile removed right now (SecurityException): no badge, no crash
            return d;
        }
    }

    /**
     * A picture drawn for the cache, with another profile's badge at the picture's own size; others as they are. Only
     * before it is cached: the platform paints the badge into a mutable bitmap in place (ApplicationPackageManager.
     * getBadgedDrawable), and sizes it by the drawable's intrinsic size, so the density is the bitmap's own.
     */
    private static Bitmap badged(Context c, Bitmap b, UserHandle user) {
        if (user == null || user.equals(Process.myUserHandle())) return b;
        BitmapDrawable plain = new BitmapDrawable(c.getResources(), b);
        plain.setTargetDensity(b.getDensity());
        Drawable d = badged(c, plain, user);
        if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap(); // b badged in place, or a badged copy
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(new Canvas(out));
        return out;
    }

    /** The picture at an opacity, in a new bitmap (a badge drawn on it afterwards stays opaque). */
    private static Bitmap faded(Bitmap b, int alpha) {
        if (alpha == 0xFF) return b;
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(alpha);
        new Canvas(out).drawBitmap(b, 0, 0, paint);
        return out;
    }

    /** The icon as it is; a built-in icon is drawn in {@code tint}. */
    static Drawable plain(Drawable icon, int tint) {
        Drawable d = copy(icon);
        if (icon instanceof BuiltinIcon) d.setTint(tint);
        return d;
    }

    /**
     * An adaptive icon cut to a shape, edge to edge, with its profile's badge (an old square icon stays as it is, no
     * white plate behind it, so nothing looks like a sticker).
     */
    private static Drawable clip(Context context, AdaptiveIconDrawable icon, String shape, int size, UserHandle user) {
        String key = idOf(icon) + "|" + user + "|" + shape + "|" + size;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return new BitmapDrawable(null, cached);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.clipPath(shapePath(shape, size));
        // Adaptive layers are 108dp with a 72dp safe zone: draw them oversized so the shape fills.
        if (icon.getBackground() != null) draw(c, icon.getBackground(), size, true);
        if (icon.getForeground() != null) draw(c, icon.getForeground(), size, true);
        b = badged(context, b, user);
        CACHE.put(key, b);
        return new BitmapDrawable(null, b);
    }

    /** Renders on one background thread: a drawer of hundreds of apps must not block the UI. */
    private static final java.util.concurrent.Executor RENDER = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Results go back through the main looper: View.post on a view not yet attached queues until it attaches, and can be lost. */
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Shows a drawn icon in the view: at once when it was drawn before, otherwise the placeholder first and the drawn
     * one as soon as it is ready (if the view still shows the placeholder). {@code alpha}: the glyph's opacity, drawn into
     * the picture under the badge, which stays opaque.
     */
    private static void renderInto(ImageView v, Drawable placeholder, int alpha, UserHandle user, String key, java.util.function.Supplier<Bitmap> draw) {
        Context c = v.getContext();
        String k = key + "|" + alpha;
        Bitmap cached = CACHE.get(k);
        if (cached != null) {
            v.setImageDrawable(new BitmapDrawable(null, cached));
            return;
        }
        placeholder.setAlpha(alpha);
        v.setImageDrawable(placeholder);
        RENDER.execute(() -> {
            try {
                Bitmap drawn = CACHE.get(k); // the same icon queued twice before the first was drawn: once
                if (drawn == null) CACHE.put(k, drawn = badged(c, faded(draw.get(), alpha), user)); // the badge goes into the cached picture
                Bitmap shown = drawn;
                MAIN.post(() -> { if (v.getDrawable() == placeholder) v.setImageDrawable(new BitmapDrawable(null, shown)); });
            } catch (RuntimeException e) { // a drawable that fails to draw: the placeholder stays, the home goes on
            }
        });
    }

    /**
     * The glyph from the app's own icon: its monochrome layer ({@code mono}, when IconSource picked it) or its alpha
     * silhouette in the tint, else (a silhouette IconMask finds unusable) its greys, tinted when {@code themed}. On a
     * container ({@code fit}) it is drawn again centred on its pixels, 26/48 of the side, as the built-in glyphs
     * (GlyphFit). Runs on the render thread.
     */
    private static Bitmap styled(Drawable icon, Drawable mono, boolean fit, int tint, boolean themed, int size) {
        Drawable foreground = icon instanceof AdaptiveIconDrawable ? ((AdaptiveIconDrawable) icon).getForeground() : null;
        Drawable layer = mono != null ? mono : foreground != null ? foreground : icon;
        boolean oversize = mono == null && foreground != null;
        int[] pixels = new int[size * size];
        pixels(layer, oversize, null, null, size, pixels);
        IconSource source = mono != null ? IconSource.MONOCHROME : IconMask.isUsable(pixels, size) ? IconSource.ALPHA : IconSource.GREYS;
        if (source == IconSource.GREYS) {
            layer = icon;
            oversize = false;
            pixels(layer, false, null, null, size, pixels);
        }
        // drawn again through the fit, not the bitmap scaled: vector layers stay sharp
        if (fit) {
            float[] bounds = GlyphFit.bounds(pixels, size);
            pixels(layer, oversize, GlyphFit.onContainer(bounds, 0, size), bounds, size, pixels);
        }
        if (source != IconSource.GREYS) {
            for (int i = 0; i < pixels.length; i++) pixels[i] = (pixels[i] & 0xFF000000) | (tint & 0xFFFFFF);
        } else {
            int tr = Color.red(tint), tg = Color.green(tint), tb = Color.blue(tint);
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                int light = (Color.red(p) * 54 + Color.green(p) * 183 + Color.blue(p) * 19) >> 8;
                int r = themed ? light * tr / 255 : light;
                int g = themed ? light * tg / 255 : light;
                int b = themed ? light * tb / 255 : light;
                pixels[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    /**
     * The drawable's pixels over the square; with a fit, placed by it and cut to the {@code bounds} it was measured on
     * (an adaptive layer's overscan, a long shadow, stays out of the fitted box).
     */
    private static void pixels(Drawable source, boolean adaptiveLayer, GlyphFit fit, float[] bounds, int size, int[] out) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (fit != null) {
            c.translate(fit.dx(), fit.dy());
            c.scale(fit.scale(), fit.scale());
            c.clipRect(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
        draw(c, source, size, adaptiveLayer);
        b.getPixels(out, 0, size, 0, 0, size, size);
    }

    /** Draws a copy of the drawable over the square; adaptive layers (108dp, 72dp safe zone) overscan so the shape fills. */
    private static void draw(Canvas c, Drawable source, int size, boolean adaptiveLayer) {
        Drawable d = copy(source);
        int extra = adaptiveLayer ? size / 4 : 0;
        d.setBounds(-extra, -extra, size + extra, size + extra);
        d.draw(c);
    }

    /** The container in the plate colour, with a border 1/48 of the size inside its edge when there is one (Soft). */
    private static Drawable plate(String shape, IconLook.Colours colours, int size) {
        String key = "plate|" + shape + "|" + colours.plate() + "|" + colours.border() + "|" + size;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return new BitmapDrawable(null, cached);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colours.plate());
        c.drawPath(shapePath(shape, size), paint);
        if (colours.border() != 0) {
            float w = size / 48f;
            paint.setColor(colours.border());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w);
            c.save();
            c.scale((size - w) / size, (size - w) / size, size / 2f, size / 2f); // the stroke's outer edge on the shape's
            c.drawPath(shapePath(shape, size), paint);
            c.restore();
        }
        CACHE.put(key, b);
        return new BitmapDrawable(null, b);
    }

    private static Path shapePath(String shape, int size) {
        Path path = new Path();
        RectF r = new RectF(0, 0, size, size);
        float s = size;
        switch (shape) {
            case "circle": path.addOval(r, Path.Direction.CW); break;
            case "square": path.addRoundRect(r, s * 0.22f, s * 0.22f, Path.Direction.CW); break;
            case "teardrop": // round everywhere except a small corner bottom-right
                path.addRoundRect(r, new float[]{s / 2, s / 2, s / 2, s / 2, s * 0.12f, s * 0.12f, s / 2, s / 2}, Path.Direction.CW);
                break;
            default: squircle(path, size);
        }
        return path;
    }

    private static void squircle(Path p, int s) {
        float r = s / 2f;
        for (int i = 0; i <= 64; i++) {
            double t = Math.PI * 2 * i / 64;
            double cos = Math.cos(t), sin = Math.sin(t);
            float x = (float) (r + r * Math.signum(cos) * Math.pow(Math.abs(cos), 0.5));
            float y = (float) (r + r * Math.signum(sin) * Math.pow(Math.abs(sin), 0.5));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }

    /** The soft plate of a folder's or shortcut's icon, as the home draws it. */
    static android.graphics.drawable.GradientDrawable softTile(int size) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(size / 3f);
        bg.setColor(Colors.alpha(Colors.LAVENDER, 0x55));
        return bg;
    }

    /** {@link #softTile} with a mark ("folder" or "link") in the middle. */
    static Drawable markedTile(String mark, int size, int fg) {
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{softTile(size),
                new android.graphics.drawable.InsetDrawable(new Glyph(mark, fg), size / 4)});
    }

    static Drawable copy(Drawable d) {
        Drawable.ConstantState s = d.getConstantState();
        return s == null ? d.mutate() : s.newDrawable().mutate();
    }

    private Icons() {}
}
