package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ganimede.launcher.core.IconSet.Cap;
import org.ganimede.launcher.core.IconSet.Join;

/** The one icon model (issue 078): glyph treatment x container, fill and colour; the old shapes migrate into it. */
public class IconLookTest {
    private static final int RED = 0xFFE01010, WHITE = 0xFFFFFFFF, NAVY = 0xFF1A237E;

    private static Map<String, Object> legacy(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static void migrates(String what, Map<String, Object> old, String glyph, String container, String fill, String color) {
        Map<String, Object> r = IconLook.migrate(old);
        eq(List.of(glyph, container, fill, color),
                List.of(r.get("icon_glyph"), r.get("icon_container"), r.get("icon_fill"), r.get("icon_color")), what);
    }

    public static void testMigratesLegacyShapes() {
        migrates("original", legacy("icon_shape", "original"), "original", "none", "solid", "accent");
        migrates("teardrop", legacy("icon_shape", "teardrop"), "original", "teardrop", "solid", "accent");
        migrates("tinted on a square", legacy("icon_shape", "theme", "themed_shape", "square"), "line", "square", "solid", "accent");
        migrates("tinted, plate never chosen: today's circle", legacy("icon_shape", "theme"), "line", "circle", "solid", "accent");
        migrates("tinted without a plate", legacy("icon_shape", "theme", "themed_shape", "none"), "line", "none", "solid", "accent");
        migrates("monochrome", legacy("icon_shape", "mono"), "line", "none", "solid", "text");
        migrates("dots", legacy("icon_shape", "dot"), "dot", "none", "solid", "accent");
        migrates("cutout (never shipped, unknown)", legacy("icon_shape", "cutout"), "original", "none", "solid", "accent");
        migrates("only the plate was stored", legacy("themed_shape", "square"), "original", "none", "solid", "accent");
    }

    public static void testMigrationRemovesLegacyKeys() {
        Map<String, Object> r = IconLook.migrate(legacy("icon_shape", "theme", "themed_shape", "square"));
        yes(r.containsKey("icon_shape") && r.get("icon_shape") == null, "icon_shape is removed");
        yes(r.containsKey("themed_shape") && r.get("themed_shape") == null, "themed_shape is removed");
        eq(Map.of(), IconLook.migrate(legacy("icon_glyph", "line")), "already migrated: nothing changes");
        eq(Map.of(), IconLook.migrate(Map.of()), "a fresh install: nothing to migrate");
    }

    public static void testDrawerTintedUsesHomePlate() {
        Map<String, Object> r = IconLook.migrate(legacy("drawer_icon_shape", "theme", "themed_shape", "square"));
        eq(List.of("line", "square", "solid", "accent"), List.of(r.get("drawer_icon_glyph"), r.get("drawer_icon_container"),
                r.get("drawer_icon_fill"), r.get("drawer_icon_color")), "the drawer's tinted icons take the home's plate");
        yes(r.containsKey("drawer_icon_shape") && r.get("drawer_icon_shape") == null, "drawer_icon_shape is removed");
        eq("text", IconLook.migrate(legacy("drawer_icon_shape", "mono")).get("drawer_icon_color"), "drawer monochrome");
        r = IconLook.migrate(legacy("icon_shape", "theme"));
        for (String k : r.keySet()) yes(!k.startsWith("drawer_"), "no drawer key without drawer_icon_shape: " + k);
    }

    private static void colours(String what, IconLook look, int accent, int plate, int border, int glyph, int alpha) {
        IconLook.Colours c = look.colours(accent, WHITE);
        eq(List.of(plate, border, glyph, alpha), List.of(c.plate(), c.border(), c.glyph(), c.alpha()), what);
    }

    public static void testColourRule() {
        colours("accent, solid plate", IconLook.of("line", "circle", "solid", "accent"), RED, RED, 0, WHITE, 0xFF);
        colours("accent, soft plate: 22% plate, 1-unit border", IconLook.of("line", "circle", "soft", "accent"),
                RED, 0x38E01010, RED, RED, 0xFF);
        colours("accent, no plate", IconLook.of("line", "none", "solid", "accent"), RED, 0, 0, RED, 0xFF);
        colours("accent, no plate: the fill means nothing", IconLook.of("line", "none", "soft", "accent"), RED, 0, 0, RED, 0xFF);
        colours("text, solid plate", IconLook.of("line", "square", "solid", "text"), RED, WHITE, 0, Colors.INK, 0xFF);
        colours("text, soft plate", IconLook.of("line", "square", "soft", "text"), RED, 0x38FFFFFF, WHITE, WHITE, 0xFF);
        colours("text, no plate: today's Monochrome", IconLook.of("line", "none", "solid", "text"), RED, 0, 0, WHITE, 0xD0);
        colours("light accent, solid plate: dark glyph", IconLook.of("bold", "circle", "solid", "accent"),
                0xFFB9A8F5, 0xFFB9A8F5, 0, Colors.INK, 0xFF);
        colours("dots: the dot in the chosen colour", IconLook.of("dot", "none", "solid", "text"), RED, 0, 0, WHITE, 0xFF);
        // Issue 077's line-icon rule: without a plate an accent under 3:1 on the backdrop gives way to the text colour.
        colours("dark accent, no plate: the text colour", IconLook.of("line", "none", "solid", "accent"), 0xFF3F2B96, 0, 0, WHITE, 0xFF);
        colours("dark accent on a solid plate: kept", IconLook.of("line", "circle", "solid", "accent"), 0xFF3F2B96, 0xFF3F2B96, 0, WHITE, 0xFF);
    }

    public static void testOriginalAndDotsKeepFullOpacity() {
        // A text colour left from Night rain or a migrated Monochrome: the app's own icon and the dot stay opaque.
        eq(0xFF, IconLook.of("original", "none", "solid", "text").colours(RED, WHITE).alpha(), "original, text colour");
        eq(0xFF, IconLook.of("original", "circle", "solid", "text").colours(RED, WHITE).alpha(), "original on a shape, text colour");
        eq(0xFF, IconLook.of("dot", "none", "solid", "text").colours(RED, WHITE).alpha(), "dots, text colour");
    }

    public static void testDotsAreTheChosenColourWhateverTheContrast() {
        // The dot is C, as before the model: the 3:1 line-icon rule is for glyphs, not for the dot.
        eq(0xFFB9A8F5, IconLook.of("dot", "none", "solid", "accent").colours(0xFFB9A8F5, Colors.INK).glyph(), "lavender dot, dark text");
        eq(NAVY, IconLook.of("dot", "none", "solid", "accent").colours(NAVY, WHITE).glyph(), "navy dot, light text");
    }

    public static void testSoftPlateGlyphFollowsTheLineIconRule() {
        // A 22% plate gives no contrast: glyph and border take the text colour when the accent is under 3:1.
        colours("navy accent, soft plate, light text", IconLook.of("line", "circle", "soft", "accent"), NAVY,
                Colors.alpha(NAVY, 0x38), WHITE, WHITE, 0xFF);
    }

    public static void testFillAndColourMatterWhereAGlyphIsDrawn() {
        // Original shows the apps' own icons, except those the built-in set covers: those are glyphs in the fill and colour.
        yes(!IconLook.of("original", "circle", "solid", "accent").tinted(false), "Original, no built-in set: own colours only");
        yes(IconLook.of("original", "circle", "solid", "accent").tinted(true), "Original over the built-in set: its glyphs are tinted");
        yes(IconLook.of("line", "none", "solid", "accent").tinted(false), "Line: always a glyph");
        yes(IconLook.of("dot", "none", "solid", "accent").tinted(false), "Dots: a dot in the colour");
    }

    private static void stroke(String glyph, float width, Cap cap, Join join) {
        IconLook.Stroke s = IconLook.of(glyph, "none", "solid", "accent").stroke();
        eq(List.of(width, cap, join), List.of(s.width(), s.cap(), s.join()), glyph);
    }

    public static void testStrokeParams() {
        stroke("line", 1f, Cap.ROUND, Join.ROUND);
        stroke("bold", 2.2f, Cap.ROUND, Join.ROUND);
        stroke("sharp", 1.4f, Cap.BUTT, Join.MITER);
        stroke("original", 1f, Cap.ROUND, Join.ROUND); // built-in glyphs have no colours of their own: drawn as Line
    }

    public static void testUnknownValuesFallBack() {
        IconLook l = IconLook.of("cutout", "hexagon", "x", "x");
        eq(List.of("original", "none", "solid", "accent"), List.of(l.glyph(), l.container(), l.fill(), l.color()), "unknown values");
        l = IconLook.of(null, null, null, null);
        eq(List.of("original", "none", "solid", "accent"), List.of(l.glyph(), l.container(), l.fill(), l.color()), "missing values");
    }

    public static void testReadsHomeOrDrawerKeys() {
        Map<String, String> p = Map.of("icon_glyph", "bold", "icon_container", "squircle", "icon_fill", "soft", "icon_color", "text");
        eq(IconLook.of("bold", "squircle", "soft", "text"), IconLook.read(p::get), "the four settings");
    }

    public static void testAllowedValues() {
        yes(IconLook.allows("icon_container", "circle") && IconLook.allows("drawer_icon_glyph", "sharp"), "listed values");
        yes(!IconLook.allows("icon_glyph", "cutout") && !IconLook.allows("drawer_icon_color", "soft"), "unlisted values");
        yes(IconLook.allows("font", "anything"), "not a model key: not this rule's business");
    }
}
