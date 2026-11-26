package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

/** Parts moved in Settings stay on the screen and off each other. */
public class FitTest {
    public static void testStackAndSpan() {
        // Moved parts: kept 4 from the edges (0..100) and never on top of each other.
        int[] s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{0, -25}, 4, 96, -1);
        eq(new int[]{0, -10}, s, "stack"); // the lower part stops right under the upper one
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{-50, 80}, 4, 96, -1);
        eq(new int[]{-6, 36}, s, "stack"); // top edge and bottom edge
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{50, 0}, 4, 96, -1);
        eq(new int[]{46, 36}, s, "stack"); // pushing the upper part down stops at the screen's bottom with both on it
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{0, -15}, 4, 96, 1);
        eq(new int[]{-5, -15}, s, "stack"); // the moved lower part pushes the upper one up
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{0, -60}, 4, 96, 1);
        eq(new int[]{-6, -16}, s, "stack"); // ...until the upper one reaches the top edge
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{30, 0}, 4, 96, 0);
        eq(new int[]{30, 20}, s, "stack"); // the moved upper part pushes the lower one down
        eq(-4, Fit.span(8, 50, -20, 4, 96), "span above the top");
        eq(46, Fit.span(8, 50, 60, 4, 96), "span below the bottom");
        eq(4, Fit.span(0, 200, 0, 4, 96), "span taller than the screen");
    }

    public static void testStackBoundaries() {
        int[] none = Fit.stack(new int[0], new int[0], new int[0], 4, 96, -1);
        eq(0, none.length, "no parts: nothing to shift");
        // Height 0: a part with no height still stays inside the limits.
        int[] s = Fit.stack(new int[]{50}, new int[]{50}, new int[]{-80}, 4, 96, -1);
        eq(-46, s[0], "a zero-height part stops at the top limit");
        s = Fit.stack(new int[]{50}, new int[]{50}, new int[]{80}, 4, 96, -1);
        eq(46, s[0], "a zero-height part stops at the bottom limit");
        // One block.
        s = Fit.stack(new int[]{10}, new int[]{30}, new int[]{-20}, 4, 96, 0);
        eq(-6, s[0], "one block pinned: stops at the top limit");
        s = Fit.stack(new int[]{10}, new int[]{30}, new int[]{100}, 4, 96, -1);
        eq(66, s[0], "one block: its bottom ends on the bottom limit");
        s = Fit.stack(new int[]{10}, new int[]{30}, new int[]{7}, 4, 96, -1);
        eq(7, s[0], "one block inside the limits: moved as asked");
        // Pinned exactly at the limit: it stays and does not push anyone.
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{-6, 0}, 4, 96, 0);
        eq(-6, s[0], "pinned part on the top limit stays there");
        eq(0, s[1], "its neighbour is not pushed");
        s = Fit.stack(new int[]{10, 40}, new int[]{30, 60}, new int[]{0, 36}, 4, 96, 1);
        eq(36, s[1], "pinned part on the bottom limit stays there");
        eq(0, s[0], "its neighbour is not pushed");
    }
}
