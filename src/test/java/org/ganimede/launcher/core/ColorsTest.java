package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
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
}
