package org.ganimede.launcher.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.ganimede.launcher.core.IconSet.Cap;
import org.ganimede.launcher.core.IconSet.Join;

/**
 * How every icon looks (issue 078): a glyph treatment and a container (with its fill), in the accent or the text colour.
 * "original" is the app's own icon (cut to the container, if any); "dot" replaces the icon and ignores the container.
 */
public record IconLook(String glyph, String container, String fill, String color) {
    public static final String[] GLYPHS = {"original", "line", "bold", "sharp", "dot"};
    public static final String[] CONTAINERS = {"none", "squircle", "circle", "square", "teardrop"};
    public static final String[] FILLS = {"solid", "soft"};
    public static final String[] COLORS = {"accent", "text"};
    /** The home's settings; the drawer's own are the same with "drawer_" in front (DrawerSettings.key). */
    public static final String[] KEYS = {"icon_glyph", "icon_container", "icon_fill", "icon_color"};
    /** The settings the model replaced: a backup, a phone or a copied theme from before still carries them. */
    public static final String[] LEGACY = {"icon_shape", "themed_shape", "drawer_icon_shape"};
    private static final String[][] VALUES = {GLYPHS, CONTAINERS, FILLS, COLORS};
    private static final int SOFT_PLATE = 0x38, TEXT_NO_PLATE = 0xD0; // 22% of 255 = 56; today's Monochrome alpha

    /** The colours to draw with: plate 0 = no plate, border 0 = no border; alpha is the glyph's opacity (0-255). */
    public record Colours(int plate, int border, int glyph, int alpha) {}

    /** The stroke of a built-in glyph in 48-grid units; caps and joins named as Android's Paint.Cap and Paint.Join. */
    public record Stroke(float width, Cap cap, Join join) {
        /** Zero-length subpaths (dots) show only with a ROUND or SQUARE cap: under BUTT they would vanish. */
        public Cap dotCap() { return cap == Cap.BUTT ? Cap.ROUND : cap; }
    }

    /** Unknown or missing values fall back to the first of their list (original, none, solid, accent). */
    public static IconLook of(String glyph, String container, String fill, String color) {
        return new IconLook(pick(GLYPHS, glyph), pick(CONTAINERS, container), pick(FILLS, fill), pick(COLORS, color));
    }

    /** Reads the four settings by their home key (the reader maps it to the drawer's own key when it has to). */
    public static IconLook read(UnaryOperator<String> value) {
        return of(value.apply(KEYS[0]), value.apply(KEYS[1]), value.apply(KEYS[2]), value.apply(KEYS[3]));
    }

    /** Whether a value may be stored under a key: model keys take only their listed values; other keys aren't ruled here. */
    public static boolean allows(String key, String value) {
        String home = key.startsWith("drawer_") ? key.substring(7) : key;
        int i = Arrays.asList(KEYS).indexOf(home);
        return i < 0 || Arrays.asList(VALUES[i]).contains(value);
    }

    /**
     * The old icon_shape / themed_shape / drawer_icon_shape as the new settings, and the old ones removed (null values):
     * nothing when none of them is there. The drawer's tinted icons used the home's plate, so they take it here too.
     */
    public static Map<String, Object> migrate(Map<String, ?> current) {
        Map<String, Object> out = new HashMap<>();
        Object t = current.get("themed_shape");
        String plate = t instanceof String ? (String) t : "circle"; // today's default plate
        if (current.containsKey("icon_shape") || current.containsKey("themed_shape")) {
            put(out, "", legacy(current.get("icon_shape"), plate));
            out.put("icon_shape", null);
            out.put("themed_shape", null);
        }
        if (current.containsKey("drawer_icon_shape")) {
            put(out, "drawer_", legacy(current.get("drawer_icon_shape"), plate));
            out.put("drawer_icon_shape", null);
        }
        return out;
    }

    private static IconLook legacy(Object shape, String plate) {
        String s = shape instanceof String ? (String) shape : "original";
        switch (s) {
            case "theme": return of("line", plate, "solid", "accent");
            case "mono": return of("line", "none", "solid", "text");
            case "dot": return of("dot", "none", "solid", "accent");
            default: return of("original", s, "solid", "accent"); // the four cut shapes; anything else: none
        }
    }

    private static void put(Map<String, Object> out, String prefix, IconLook l) {
        out.put(prefix + KEYS[0], l.glyph);
        out.put(prefix + KEYS[1], l.container);
        out.put(prefix + KEYS[2], l.fill);
        out.put(prefix + KEYS[3], l.color);
    }

    /** Whether fill and colour show: on a glyph or a dot, and with Original only over the built-in set's glyphs. */
    public boolean tinted(boolean builtinSet) {
        return builtinSet || !glyph.equals("original");
    }

    /** A plate is drawn: a container chosen, and not Dots (which replaces the icon). */
    public boolean plated() {
        return !container.equals("none") && !glyph.equals("dot");
    }

    /** One rule for every glyph, with C the accent or the text colour (fg). */
    public Colours colours(int accent, int fg) {
        int c = color.equals("text") ? fg : accent;
        if (glyph.equals("dot")) return new Colours(0, 0, c, 0xFF); // the dot is C, as it always was
        int line = Colors.lineIcon(c, fg); // a glyph on the wallpaper or on a 22% plate: issue 077's 3:1 rule
        if (!plated()) return new Colours(0, 0, line, color.equals("text") && !glyph.equals("original") ? TEXT_NO_PLATE : 0xFF);
        if (fill.equals("soft")) return new Colours(Colors.alpha(c, SOFT_PLATE), line, line, 0xFF);
        return new Colours(c, 0, Colors.on(c), 0xFF);
    }

    /** Line, Bold and Sharp set width, cap and join of every stroked path (the set's own are not used); others draw as Line. */
    public Stroke stroke() {
        switch (glyph) {
            case "bold": return new Stroke(2.2f, Cap.ROUND, Join.ROUND);
            case "sharp": return new Stroke(1.4f, Cap.BUTT, Join.MITER);
            default: return new Stroke(1f, Cap.ROUND, Join.ROUND);
        }
    }

    private static String pick(String[] values, String v) {
        return Arrays.asList(values).contains(v) ? v : values[0];
    }
}
