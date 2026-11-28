package org.ganimede.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.widget.ImageView;

import org.ganimede.launcher.core.GlyphFit;
import org.ganimede.launcher.core.IconLook;
import org.ganimede.launcher.core.IconMask;
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
    static final String[] GLYPH_NAMES = {"Original", "Line", "Bold", "Sharp", "Dots (minimal)"};
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

    /** Shaped icons are drawn once and reused: no bitmap work while scrolling. */
    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final String[] PACK_ACTIONS = {"org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME"};

    /** The icon_pack value of the built-in Arcticons set: a colon can't be in a package name. */
    static final String BUILTIN = "builtin:arcticons";

    private static final Object LOAD_LOCK = new Object();
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
        try {
            XmlPullParser x;
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
                if (BUILTIN.equals(pack)) {
                    set = IconSet.of(asset(c, "icons.bin"));
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

    /** The Icons settings' Calendar from the built-in set, decoded once (the set itself is not kept for it). */
    private static volatile BuiltinIcon sample;

    /**
     * The Icons settings' sample of a built-in glyph (Calendar) in the view: at once when the set was read, else the
     * placeholder first and the sample once the set is read on the render thread (never on the UI thread).
     */
    static void applySample(ImageView v, Drawable placeholder, IconLook look, int fg, int accent, int size) {
        BuiltinIcon s = sample;
        if (s != null) { apply(v, s, false, null, look, fg, accent, size); return; }
        apply(v, placeholder, false, null, look, fg, accent, size);
        Context c = v.getContext().getApplicationContext();
        RENDER.execute(() -> {
            try {
                if (sample == null) { // one render thread: the tiles queued after the first find it decoded
                    IconSet set = loaded.set != null ? loaded.set : IconSet.of(asset(c, "icons.bin")); // read here, then let go
                    sample = new BuiltinIcon(set.icon(set.index().find("com.google.android.calendar",
                            "com.android.calendar.AllInOneActivity").orElse(0)));
                }
                v.post(() -> apply(v, sample, false, null, look, fg, accent, size));
            } catch (IOException | RuntimeException e) {
                // an unreadable set: the placeholder stays
            }
        });
    }

    private static byte[] asset(Context c, String name) throws IOException {
        try (InputStream in = c.getAssets().open(name)) {
            return LimitedInputStream.readAll(in, 16 << 20); // the set is 4 MB
        }
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
                return id.isPresent() ? new BuiltinIcon(pack.set.icon(id.getAsInt())) : null;
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

    /** Every icon an installed pack offers (the drawable names its appfilter lists, without repeats). */
    static java.util.List<String> packIcons(Context c, String pack) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        try {
            readAppfilter(resources(c, pack), pack, (comp, draw) -> {
                if (draw != null) out.add(draw);
                return out.size() < 4000;
            });
        } catch (Exception ignored) {
            // Unreadable pack: nothing to offer.
        }
        return new java.util.ArrayList<>(out);
    }

    /** An icon named "pack|drawable" (how a picked icon is stored), or null. */
    static Drawable named(Context c, String packAndName) {
        String[] pp = packAndName.split("\\|", 2);
        if (pp.length != 2) return null;
        try {
            Resources r = resources(c, pp[0]);
            int id = r.getIdentifier(pp[1], "drawable", pp[0]);
            return id == 0 ? null : r.getDrawable(id, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** The icon picked for an entry from an installed pack (stored under "icon:<entry>"), or null. */
    static Drawable picked(Context c, SharedPreferences p, String entry) {
        return named(c, p.getString("icon:" + entry, ""));
    }

    /** The icon settings of the home (the drawer's own: Layout.drawerIconLook). */
    static IconLook look(SharedPreferences p) {
        return IconLook.read(k -> p.getString(k, null));
    }

    /**
     * Puts an icon into an ImageView as the icon model says (glyph treatment, container, fill, colour). {@code user}: the
     * profile of the app the icon is for (null: not an app's), whose badge goes on another profile's icon.
     */
    static void apply(ImageView v, Drawable icon, boolean fromPack, UserHandle user, IconLook look, int fg, int accent, int size) {
        int px = size <= 0 ? 96 : size;
        v.setBackground(null);
        v.setPadding(0, 0, 0, 0);
        IconLook.Colours colours = look.colours(accent, fg);
        v.setImageAlpha(colours.alpha());
        if (look.glyph().equals("dot")) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(colours.glyph());
            v.setImageDrawable(null);
            int pad = size * 7 / 20;
            v.setPadding(pad, pad, pad, pad);
            v.setBackground(new android.graphics.drawable.InsetDrawable(dot, pad));
            return;
        }
        Context c = v.getContext();
        Drawable mono = Build.VERSION.SDK_INT >= 33 && icon instanceof AdaptiveIconDrawable ? ((AdaptiveIconDrawable) icon).getMonochrome() : null;
        // the silhouette is judged once drawn, on the render thread: until then ALPHA stands for "silhouette or greys"
        IconSource source = IconSource.pick(fromPack, s -> s == IconSource.ARCTICONS ? icon instanceof BuiltinIcon
                : s != IconSource.MONOCHROME || mono != null);
        if (source == IconSource.ARCTICONS) { // built-in geometry, with the treatment's stroke (original draws as Line)
            BuiltinIcon fresh = (BuiltinIcon) copy(icon); // its own tint, alpha and look: the shared one stays as it is
            renderInto(v, ((BuiltinIcon) plain(icon, colours.glyph())).look(look), idOf(icon) + "|" + look + "|" + colours.glyph() + "|" + px, () -> {
                Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                fresh.render(new Canvas(b), look, colours.glyph(), px);
                return badged(c, b, user);
            });
        } else if (look.glyph().equals("original")) { // the app's own icon, cut to the container if there is one
            v.setImageDrawable(badged(c, look.container().equals("none") ? copy(icon) : clip(icon, look.container(), px), user));
            return;
        } else { // a glyph from the app's own icon: its monochrome layer, silhouette or greys (the same for every stroke)
            boolean tintGreys = look.plated() || look.color().equals("accent"); // today's Monochrome kept plain greys
            int tint = colours.glyph();
            boolean fit = look.plated();
            Drawable layer = source == IconSource.MONOCHROME ? mono : null;
            Drawable placeholder = copy(layer != null ? layer : icon); // the monochrome layer already looks as it will
            if (layer != null) placeholder.setTint(tint);
            renderInto(v, placeholder, idOf(icon) + "|" + source + "|" + fit + "|" + tintGreys + "|" + tint + "|" + px,
                    () -> badged(c, styled(icon, layer, fit, tint, tintGreys, px), user));
        }
        if (look.plated()) v.setBackground(plate(look.container(), colours, px));
    }

    /** An icon of another profile's app with that profile's badge (the work briefcase); others as they are. */
    static Drawable badged(Context c, Drawable d, UserHandle user) {
        return user == null || user.equals(Process.myUserHandle()) ? d : c.getPackageManager().getUserBadgedIcon(d, user);
    }

    private static Bitmap badged(Context c, Bitmap b, UserHandle user) {
        if (user == null || user.equals(Process.myUserHandle())) return b;
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        Drawable d = badged(c, new BitmapDrawable(c.getResources(), b), user);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(new Canvas(out));
        return out;
    }

    /** The icon as it is; a built-in icon is drawn in {@code tint}. */
    static Drawable plain(Drawable icon, int tint) {
        Drawable d = copy(icon);
        if (icon instanceof BuiltinIcon) d.setTint(tint);
        return d;
    }

    /**
     * An adaptive icon cut to a shape, edge to edge; an old square icon as it is (no white plate behind it), so nothing
     * looks like a sticker.
     */
    private static Drawable clip(Drawable icon, String shape, int size) {
        if (!(icon instanceof AdaptiveIconDrawable)) return copy(icon);
        if (size <= 0) size = 96;
        String key = idOf(icon) + "|" + shape + "|" + size;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return new BitmapDrawable(null, cached);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.clipPath(shapePath(shape, size));
        // Adaptive layers are 108dp with a 72dp safe zone: draw them oversized so the shape fills.
        AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
        if (a.getBackground() != null) draw(c, a.getBackground(), size, true);
        if (a.getForeground() != null) draw(c, a.getForeground(), size, true);
        CACHE.put(key, b);
        return new BitmapDrawable(null, b);
    }

    /** Renders on one background thread: a drawer of hundreds of apps must not block the UI. */
    private static final java.util.concurrent.Executor RENDER = java.util.concurrent.Executors.newSingleThreadExecutor();

    /**
     * Shows a drawn icon in the view: at once when it was drawn before, otherwise the placeholder first and the drawn
     * one as soon as it is ready (if the view still shows the placeholder).
     */
    private static void renderInto(ImageView v, Drawable placeholder, String key, java.util.function.Supplier<Bitmap> draw) {
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            v.setImageDrawable(new BitmapDrawable(null, cached));
            return;
        }
        v.setImageDrawable(placeholder);
        RENDER.execute(() -> {
            Bitmap b = draw.get();
            CACHE.put(key, b);
            v.post(() -> { if (v.getDrawable() == placeholder) v.setImageDrawable(new BitmapDrawable(null, b)); });
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

    static Drawable copy(Drawable d) {
        Drawable.ConstantState s = d.getConstantState();
        return s == null ? d.mutate() : s.newDrawable().mutate();
    }

    private Icons() {}
}
