package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

public class PagingTest {
    private static final float W = 1080, FLICK = 1000;

    public static void testShortDragTurnsThePage() {
        eq(1, Paging.target(0, 3, -200, 0, W, FLICK), "a sixth of the width to the left");
        eq(0, Paging.target(1, 3, 200, 0, W, FLICK), "a sixth of the width to the right");
    }

    public static void testTinyDragStays() {
        eq(1, Paging.target(1, 3, -100, 0, W, FLICK), "under a sixth");
    }

    public static void testQuickFlickTurnsEvenWhenShort() {
        eq(2, Paging.target(1, 3, -40, -1500, W, FLICK), "fast to the left");
        eq(0, Paging.target(1, 3, 40, 1500, W, FLICK), "fast to the right");
    }

    public static void testFlickWinsOverDistance() {
        eq(1, Paging.target(1, 3, -300, 1500, W, FLICK), "dragged left, flicked back right: stays");
    }

    public static void testEdgesHold() {
        eq(0, Paging.target(0, 3, 400, 0, W, FLICK), "no page before the first");
        eq(2, Paging.target(2, 3, -400, 0, W, FLICK), "no page after the last");
        eq(0, Paging.target(0, 1, -400, -3000, W, FLICK), "a single page");
    }

    private static String focus(int current, int pages, int side) {
        return java.util.Arrays.toString(Paging.focusPages(current, pages, side));
    }

    // Issue 085: typing exits touch mode and Android gives the home a focus; a block of page 1 ending at the edge of
    // page 2 counts as "on screen" for the pager, takes it, and the pager scrolls back to page 1.
    public static void testOnlyThePageShownTakesFocus() {
        eq("[1]", focus(1, 2, 0), "no direction: the page shown only");
        eq("[0]", focus(0, 3, 0), "first page");
        eq("[]", focus(0, 0, 0), "no pages built yet");
        eq("[1]", focus(3, 2, 0), "a scroll left past a deleted page: the last page");
    }

    public static void testSidewaysFocusReachesTheNextPageThatWay() {
        eq("[1, 0]", focus(1, 3, -1), "left: the page before");
        eq("[1, 2]", focus(1, 3, 1), "right: the page after");
        eq("[0]", focus(0, 3, -1), "no page before the first");
        eq("[2]", focus(2, 3, 1), "no page after the last");
    }

    // Finding c1-2: left-hand use mirrors the home, but the pages stay left to right: the focus goes right for both
    // hands (the swipe's own direction is covered above, it takes no hand).
    public static void testFocusGoesRight() {
        eq("[0, 1]", focus(0, 2, 1), "right: the next page");
    }

    // Finding c3 M5: one page limit, the one a backup accepts.
    public static void testMaxPagesIsWhatABackupKeeps() {
        org.ganimede.launcher.Expect.yes(Backup.fits("grid_pages", Paging.MAX_PAGES), "the last page count a backup keeps");
        org.ganimede.launcher.Expect.yes(!Backup.fits("grid_pages", Paging.MAX_PAGES + 1), "one more is out");
    }

    // Finding c3 P2-2: a side key with no icon to move to turns the page whole, never half
    public static void testSideKeyTurnsOnePageAndStaysInRange() {
        eq(2, Paging.turn(1, 4, 1), "right");
        eq(0, Paging.turn(1, 4, -1), "left");
        eq(0, Paging.turn(0, 4, -1), "left on the first page stays");
        eq(3, Paging.turn(3, 4, 1), "right on the last page stays");
    }
}
