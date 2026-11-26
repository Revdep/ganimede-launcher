package org.ganimede.launcher.core;

/** The alphabet's letters bulging out sideways under the finger. */
public final class Wave {
    /**
     * Sideways pull of letter i of n in the alphabet wave:
     * d = (i+1)/n - (finger - top + step/2) / height, sigma = amplitude / height, pull = amplitude * 2^(-4 d^2 / sigma).
     */
    public static float pull(int i, int n, float finger, float top, float step, float height, float amplitude) {
        if (amplitude <= 0 || height <= 0 || n == 0) return 0;
        float d = (i + 1f) / n - (finger - top + step / 2) / height;
        float sigma = amplitude / height;
        return (float) (amplitude * Math.pow(2, -4 * d * d / sigma));
    }

    /** Letter size in px (all sizes px): the chosen one, or 14/16 of it when n rows of 1.2 x size don't fit the column. */
    public static float textSize(int n, float size, float viewHeight, float d) {
        return n * size * 1.2f <= viewHeight - 112 * d ? size : size * 14f / 16f;
    }

    /** Row height: 1.2 x the text size, never more than 19/16 of the chosen size (alphabet_wave_max_section_size). */
    public static float step(float text, float size) {
        return Math.min(text * 1.2f, size * 19f / 16f);
    }

    /** Column top: low (64% of the view), its bottom 64dp above the edge, never above 48dp (alphabet_wave_recommended_bottom_padding). */
    public static float top(float viewHeight, float columnHeight, float d) {
        return Math.max(48 * d, Math.min(viewHeight * 0.64f - columnHeight / 2, viewHeight - 64 * d - columnHeight));
    }

    /** Row under y, clamped to 0..n-1. */
    public static int index(float y, float top, float step, int n) {
        return Math.max(0, Math.min(n - 1, (int) Math.floor((y - top) / step)));
    }

    /**
     * Amplitude: the 64dp pressed offset (alphabet_wave_pressed_offset) plus the finger's distance from the edge, capped at
     * the half width minus 12sp + 44sp + 12sp (handle margin, handle diameter, margin), times the grow-in progress.
     */
    public static float amplitude(float fromEdge, float width, float progress, float d, float sp) {
        return Math.max(0, Math.min(64 * d + fromEdge, width / 2f - 68 * sp)) * progress;
    }

    private Wave() {}
}
