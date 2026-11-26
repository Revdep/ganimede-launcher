package org.ganimede.launcher.core;

/** Decides whether a drawable's alpha contains a useful logo rather than an empty or solid shape. */
public final class IconMask {
    /**
     * A logo is at least 2% of the square and leaves at least a quarter of its own bounding box see-through: a
     * filled square, disc or rounded tile (an old icon) has too little, and would only make a flat blob.
     */
    public static boolean isUsable(int[] pixels, int width) {
        int opaque = 0, minX = width, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) < 0x20) continue;
            opaque++;
            int x = i % width, y = i / width;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        if (opaque * 100 < pixels.length * 2) return false;
        int box = (maxX - minX + 1) * (maxY - minY + 1);
        return (box - opaque) * 100 >= box * 25;
    }

    private IconMask() {}
}
