package org.ganimede.launcher.core;

/**
 * The geometry and timing of a pop-up or sheet that grows out of what was touched and shrinks back onto it.
 * Progress p runs from 0 (folded onto its origin) to 1 (open). Lengths are pixels along one axis.
 */
public final class Morph {
    public static final long OPEN_MS = 250, CLOSE_MS = 200, SNAP_MS = 160;
    /** Never a dot (it would be gone before it is seen), never so big that the growth goes unnoticed. */
    public static final float MIN_SCALE = 0.1f, MAX_SCALE = 0.85f;
    /** A bottom sheet rises this much narrower, leaning towards what opened it. */
    public static final float SHEET_SCALE = 0.92f;

    /** The scale a card starts from: about the size of its origin. */
    public static float startScale(float originW, float originH, float cardW, float cardH) {
        if (cardW <= 0 || cardH <= 0) return MAX_SCALE;
        return clamp(Math.max(originW / cardW, originH / cardH), MIN_SCALE, MAX_SCALE);
    }

    /**
     * The pivot, measured from the card's start, that leaves the card scaled by s centred on the origin's middle o:
     * the folded card sits on the origin and grows out of it.
     */
    public static float pivot(float cardStart, float cardSize, float o, float s) {
        return (o - s * (cardStart + cardSize / 2)) / (1 - s) - cardStart;
    }

    public static float scale(float from, float p) {
        return from + (1 - from) * p;
    }

    /** Fully there by 30% of the way: it shows as it grows, and fades only as a close lands on its origin. */
    public static float alpha(float p) {
        return clamp(p / 0.3f, 0, 1);
    }

    /** How long icon i waits to come in: one after the other, the later ones together. */
    public static long stagger(int i) {
        return Math.min(i, 10) * 18L;
    }

    /** How long the rest of a trip takes: a close that interrupts an opening goes back from where it is. */
    public static long ms(long full, float from, float to) {
        return Math.round(full * Math.min(1, Math.abs(to - from)));
    }

    /** How open a sheet still is when pulled down dy px of its height. */
    public static float pulled(float dy, float height) {
        return height <= 0 ? 0 : clamp(1 - dy / height, 0, 1);
    }

    /** How far below its place a sheet p of the way open sits: the inverse of pulled. */
    public static float drop(float p, float height) {
        return (1 - p) * height;
    }

    /** A point o seen from a card spanning start..start+size, kept on the card. */
    public static float inside(float o, float start, float size) {
        return clamp(o - start, 0, size);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private Morph() {}
}
