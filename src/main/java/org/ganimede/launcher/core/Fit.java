package org.ganimede.launcher.core;

/** Keeps the parts of the home moved in Settings on the screen and off each other. */
public final class Fit {
    /**
     * Parts stacked top to bottom (their laid-out top/bottom and the shift asked for): the shifts that keep each one
     * inside [lo, hi] and off its neighbours, as close to what was asked as fits. Part `pinned` (the one just moved;
     * -1 for none, then the top wins) goes where it was asked and pushes its neighbours until they reach an edge.
     */
    public static int[] stack(int[] top, int[] bottom, int[] shift, int lo, int hi, int pinned) {
        int n = top.length;
        int[] t = new int[n], out = new int[n];
        for (int i = 0; i < n; i++) t[i] = top[i] + shift[i];
        if (pinned >= 0 && pinned < n) {
            for (int i = pinned - 1; i >= 0; i--) t[i] = Math.min(t[i], t[i + 1] - (bottom[i] - top[i]));
            for (int i = pinned + 1; i < n; i++) t[i] = Math.max(t[i], t[i - 1] + bottom[i - 1] - top[i - 1]);
        }
        for (int i = 0; i < n; i++) t[i] = Math.max(t[i], i == 0 ? lo : t[i - 1] + bottom[i - 1] - top[i - 1]);
        for (int i = n - 1; i >= 0; i--) t[i] = Math.min(t[i], (i == n - 1 ? hi : t[i + 1]) - (bottom[i] - top[i]));
        for (int i = 0; i < n; i++) t[i] = Math.max(t[i], i == 0 ? lo : t[i - 1] + bottom[i - 1] - top[i - 1]);
        for (int i = 0; i < n; i++) out[i] = t[i] - top[i];
        return out;
    }

    /** The sideways shift that keeps [left, right] inside [lo, hi]; the left edge wins when it's too wide. */
    public static int span(int left, int right, int shift, int lo, int hi) {
        int l = Math.min(left + shift, hi - (right - left));
        return Math.max(l, lo) - left;
    }

    private Fit() {}
}
