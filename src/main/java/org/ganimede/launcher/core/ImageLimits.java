package org.ganimede.launcher.core;

/** Bounds declared by an imported image before Android is allowed to decode it. */
public final class ImageLimits {
    private static final int MAX_SIDE = 16_384;
    private static final long MAX_PIXELS = 100_000_000L;

    public static boolean acceptable(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE
                && (long) width * height <= MAX_PIXELS;
    }

    private ImageLimits() {}
}
