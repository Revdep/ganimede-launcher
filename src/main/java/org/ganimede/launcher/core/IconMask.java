package org.ganimede.launcher.core;

/** Decides whether a drawable's alpha contains a useful logo rather than an empty or solid shape. */
public final class IconMask {
    /**
     * A logo is at least 2% of the square and leaves at least a quarter of its own bounding box see-through: a
     * filled square, disc or rounded tile (an old icon) has too little, and would only make a flat blob.
     */
    public static boolean isUsable(int[] pixels, int width) {
        int opaque = 0;
        for (int p : pixels) if ((p >>> 24) >= GlyphFit.SEEN) opaque++;
        if (opaque * 100 < pixels.length * 2) return false;
        float[] b = GlyphFit.bounds(pixels, width);
        int box = (int) ((b[2] - b[0]) * (b[3] - b[1]));
        return (box - opaque) * 100 >= box * 25;
    }

    private IconMask() {}
}
