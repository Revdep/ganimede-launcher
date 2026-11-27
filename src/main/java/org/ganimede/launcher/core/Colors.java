package org.ganimede.launcher.core;

/** Colours shared by the home screen, the settings and the preview (ARGB ints). */
public final class Colors {
    /** Light text (the default), dark text, the folder tint and the default accent. */
    public static final int WHITE = 0xFFFFFFFF, INK = 0xFF1B1820, LAVENDER = 0xFFD9CFF7, ACCENT = 0xFFB9A8F5;

    /** Perceived brightness, 0 (black) to 1 (white). */
    public static float luminance(int c) {
        return (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 255_000f;
    }

    /** Readable text on a colour: ink on light ones, white on dark ones (perceived brightness). */
    public static int on(int c) {
        return luminance(c) > 150_000 / 255_000f ? INK : WHITE;
    }

    /** An accent as text on a dark sheet: mixed toward white until its brightness is at least 0.4. */
    public static int onDark(int c) {
        while (luminance(c) < 0.4f && (c & 0xFFFFFF) != 0xFFFFFF) {
            int r = ((c >> 16) & 255) + (255 - ((c >> 16) & 255)) / 4 + 1, g = ((c >> 8) & 255) + (255 - ((c >> 8) & 255)) / 4 + 1,
                    b = (c & 255) + (255 - (c & 255)) / 4 + 1;
            c = 0xFF000000 | Math.min(r, 255) << 16 | Math.min(g, 255) << 8 | Math.min(b, 255);
        }
        return c;
    }

    /** WCAG contrast ratio of two opaque colours, 1 (none) to 21 (black on white). */
    public static double contrast(int a, int b) {
        double la = relativeLuminance(a), lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double relativeLuminance(int c) {
        return 0.2126 * linear(c >> 16 & 255) + 0.7152 * linear(c >> 8 & 255) + 0.0722 * linear(c & 255);
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /**
     * A line icon on the home: the accent when it reaches 3:1 (WCAG non-text contrast) on the backdrop the text colour
     * fg was chosen for (white text: a dark one, ink text: a light one), else fg itself.
     */
    public static int lineIcon(int accent, int fg) {
        return contrast(accent, on(fg)) >= 3 ? accent : fg;
    }

    /** The veil over the wallpaper for a darkening of pct % (night-blue on light text, warm white on dark text). */
    public static int veil(int pct, boolean darkText) {
        int a = Math.max(0, Math.min(95, pct)) * 255 / 100;
        return alpha(darkText ? 0xFFF8F0 : 0x060418, a);
    }

    /** The colour's own red, green and blue at an opacity of a (0-255). */
    public static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    private Colors() {}
}
