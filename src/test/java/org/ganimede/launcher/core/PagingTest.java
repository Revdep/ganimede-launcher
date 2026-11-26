package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

public class PagingTest {
    private static final float W = 1080, FLICK = 1000;

    public static void testShortDragTurnsThePage() {
        eq(1, Paging.target(0, 3, -200, 0, W, FLICK, false), "a sixth of the width to the left");
        eq(0, Paging.target(1, 3, 200, 0, W, FLICK, false), "a sixth of the width to the right");
    }

    public static void testTinyDragStays() {
        eq(1, Paging.target(1, 3, -100, 0, W, FLICK, false), "under a sixth");
    }

    public static void testQuickFlickTurnsEvenWhenShort() {
        eq(2, Paging.target(1, 3, -40, -1500, W, FLICK, false), "fast to the left");
        eq(0, Paging.target(1, 3, 40, 1500, W, FLICK, false), "fast to the right");
    }

    public static void testFlickWinsOverDistance() {
        eq(1, Paging.target(1, 3, -300, 1500, W, FLICK, false), "dragged left, flicked back right: stays");
    }

    public static void testEdgesHold() {
        eq(0, Paging.target(0, 3, 400, 0, W, FLICK, false), "no page before the first");
        eq(2, Paging.target(2, 3, -400, 0, W, FLICK, false), "no page after the last");
        eq(0, Paging.target(0, 1, -400, -3000, W, FLICK, false), "a single page");
    }

    public static void testMirroredScreenTurnsTheOtherWay() {
        eq(1, Paging.target(0, 3, 200, 0, W, FLICK, true), "right-to-left: dragging right goes forward");
    }
}
