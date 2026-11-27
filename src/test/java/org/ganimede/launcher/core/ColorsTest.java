package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.near;
import static org.ganimede.launcher.Expect.yes;

public class ColorsTest {
    public static void testInkOnLightWhiteOnDark() {
        eq(Colors.INK, Colors.on(0xFFB9A8F5), "lavender takes dark text");
        eq(Colors.INK, Colors.on(0xFFF0F0F0), "white takes dark text");
        eq(Colors.WHITE, Colors.on(0xFFE01010), "red takes white text");
        eq(Colors.WHITE, Colors.on(0xFF000000), "black takes white text");
    }

    public static void testVeilDarkensUpTo95Percent() {
        eq(0x00060418, Colors.veil(0, false), "none");
        eq(0x99060418, Colors.veil(60, false), "night blue under light text");
        eq(0x99FFF8F0, Colors.veil(60, true), "warm white under dark text");
        eq(Colors.veil(95, false), Colors.veil(200, false), "never fully opaque");
        eq(Colors.veil(0, false), Colors.veil(-5, false), "never negative");
    }

    public static void testAlphaKeepsTheColourAndReplacesTheOpacity() {
        eq(0xAAB9A8F5, Colors.alpha(0xFFB9A8F5, 0xAA), "opaque to 2/3");
        eq(0x33B9A8F5, Colors.alpha(0x11B9A8F5, 0x33), "any old opacity is replaced");
        eq(0xFFFFFFFF, Colors.alpha(0x00FFFFFF, 0xFF), "fully opaque");
        eq(0x00B9A8F5, Colors.alpha(Colors.ACCENT, 0), "invisible");
    }

    public static void testLuminanceSplitsLightFromDark() {
        eq(0f, Colors.luminance(0xFF000000), "black");
        eq(1f, Colors.luminance(0xFFFFFFFF), "white");
        yes(Colors.luminance(0xFFB9A8F5) > 0.5f, "lavender is light");
        yes(Colors.luminance(0xFF1B1820) < 0.5f, "ink is dark");
        yes(Colors.luminance(0xFF00FF00) > Colors.luminance(0xFF0000FF), "green looks brighter than blue");
    }

    public static void testAccentOnDarkIsLiftedUntilReadable() {
        eq(0xFFB9A8F5, Colors.onDark(0xFFB9A8F5), "a light accent stays");
        yes(Colors.luminance(Colors.onDark(0xFF000000)) >= 0.4f, "black becomes readable on dark");
        yes(Colors.luminance(Colors.onDark(0xFF1B1820)) >= 0.4f, "dark ink too");
    }

    public static void testContrastIsTheWcagRatio() {
        near(21f, (float) Colors.contrast(0xFF000000, 0xFFFFFFFF), "black on white");
        near(1f, (float) Colors.contrast(0xFFE01010, 0xFFE01010), "a colour on itself");
        near((float) Colors.contrast(0xFF000000, 0xFFFFFFFF), (float) Colors.contrast(0xFFFFFFFF, 0xFF000000), "order does not matter");
    }

    /**
     * A line icon on the home is drawn in the accent only when it reaches 3:1 (WCAG non-text contrast) on the backdrop
     * the text colour was chosen for: white text means a dark backdrop (ink), ink text a light one (white).
     */
    public static void testLineIconColourKeepsThreeToOne() {
        int indigo = 0xFF3F2B96, red = 0xFFE01010;
        yes(Colors.contrast(red, Colors.INK) >= 3, "red reaches 3:1 on ink");
        yes(Colors.contrast(indigo, Colors.INK) < 3, "indigo does not reach 3:1 on ink");
        yes(Colors.contrast(Colors.ACCENT, Colors.WHITE) < 3, "lavender does not reach 3:1 on white");
        eq(red, Colors.lineIcon(red, Colors.WHITE), "red on a dark wallpaper stays red");
        eq(Colors.ACCENT, Colors.lineIcon(Colors.ACCENT, Colors.WHITE), "lavender on a dark wallpaper stays lavender");
        eq(Colors.WHITE, Colors.lineIcon(indigo, Colors.WHITE), "indigo on a dark wallpaper falls back to the white text colour");
        eq(Colors.INK, Colors.lineIcon(Colors.ACCENT, Colors.INK), "lavender on a light wallpaper falls back to the ink text colour");
    }
}
