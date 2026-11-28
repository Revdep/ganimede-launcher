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

    private static String focus(int current, int pages, int side, boolean rtl) {
        return java.util.Arrays.toString(Paging.focusPages(current, pages, side, rtl));
    }

    // Issue 085: typing exits touch mode and Android gives the home a focus; a block of page 1 ending at the edge of
    // page 2 counts as "on screen" for the pager, takes it, and the pager scrolls back to page 1.
    public static void testOnlyThePageShownTakesFocus() {
        eq("[1]", focus(1, 2, 0, false), "no direction: the page shown only");
        eq("[0]", focus(0, 3, 0, false), "first page");
        eq("[]", focus(0, 0, 0, false), "no pages built yet");
        eq("[1]", focus(3, 2, 0, false), "a scroll left past a deleted page: the last page");
    }

    public static void testSidewaysFocusReachesTheNextPageThatWay() {
        eq("[1, 0]", focus(1, 3, -1, false), "left: the page before");
        eq("[1, 2]", focus(1, 3, 1, false), "right: the page after");
        eq("[0]", focus(0, 3, -1, false), "no page before the first");
        eq("[2]", focus(2, 3, 1, false), "no page after the last");
    }

    public static void testMirroredScreenReachesTheOtherWay() {
        eq("[0, 1]", focus(0, 2, -1, true), "right-to-left: left is the next page");
    }
}
