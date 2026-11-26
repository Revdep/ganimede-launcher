package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

/** A dragged brick lands on the 4 dp grid and clings to guide lines that are close. */
public class SnapTest {
    public static void testGridRoundsToTheNearestStep() {
        eq(4, Snap.grid(5, 4), "just past a step");
        eq(8, Snap.grid(7, 4), "nearly the next step");
        eq(-8, Snap.grid(-7, 4), "negative offsets too");
        eq(0, Snap.grid(1, 4), "a tremble stays put");
    }

    public static void testStartClingsToALineWithinReach() {
        eq(new int[]{40, 40}, Snap.magnet(43, 100, new int[]{4, 40, 300}, 6), "left edge on a neighbour's edge");
    }

    public static void testMiddleAndEndCling() {
        eq(new int[]{150, 200}, Snap.magnet(147, 100, new int[]{4, 200, 396}, 6), "centred on the screen's middle");
        eq(new int[]{296, 396}, Snap.magnet(300, 100, new int[]{4, 200, 396}, 6), "right edge on the right limit");
    }

    public static void testFarFromEveryLineStaysFree() {
        eq(new int[]{60, Snap.NONE}, Snap.magnet(60, 100, new int[]{4, 200, 396}, 6), "nothing close");
    }

    public static void testTheNearestLineWins() {
        eq(new int[]{102, 102}, Snap.magnet(100, 50, new int[]{95, 102}, 6), "two lines in reach of the start");
        eq(new int[]{51, 101}, Snap.magnet(50, 50, new int[]{45, 101}, 6), "the end is nearer its line than the start");
    }
}
