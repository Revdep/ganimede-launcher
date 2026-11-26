package org.ganimede.launcher.core;

/** Precision for a finger: a dragged part lands on a grid and clings to the guide lines it comes close to. */
public final class Snap {
    /** "No line": what magnet answers when nothing is in reach. */
    public static final int NONE = Integer.MIN_VALUE;

    /** v on the nearest multiple of step. */
    public static int grid(int v, int step) {
        return Math.round((float) v / step) * step;
    }

    /**
     * A part [start, start + size] dragged along one axis past guide lines (screen edges, the middle, its neighbours'
     * edges): its start, middle or end clings to the nearest line within reach. Answers {start, line}, the line NONE
     * when it stays free.
     */
    public static int[] magnet(int start, int size, int[] lines, int reach) {
        int best = reach + 1, move = 0, at = NONE;
        for (int line : lines) {
            for (int anchor : new int[]{0, size / 2, size}) {
                int d = line - (start + anchor);
                if (Math.abs(d) < best) { best = Math.abs(d); move = d; at = line; }
            }
        }
        return new int[]{start + move, at};
    }

    private Snap() {}
}
