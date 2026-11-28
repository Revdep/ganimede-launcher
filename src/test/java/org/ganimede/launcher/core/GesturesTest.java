package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

public class GesturesTest {
    private static final float FLICK = 600;
    private static final float SCREEN = 1080, DENSITY = 3;

    public static void testDrawerOpensPastAThirdFromHome() {
        yes(Gestures.drawerOpens(0, 0.31f, true, FLICK), "opening: past 0.3");
        yes(!Gestures.drawerOpens(0, 0.3f, true, FLICK), "opening: not yet");
    }

    public static void testDrawerClosesPastAThirdFromTheList() {
        yes(Gestures.drawerOpens(0, 0.71f, false, FLICK), "closing: still above 0.7 stays open");
        yes(!Gestures.drawerOpens(0, 0.7f, false, FLICK), "closing: a third down closes");
    }

    public static void testAFlickDecides() {
        yes(Gestures.drawerOpens(-700, 0.05f, true, FLICK), "flick up opens however short");
        yes(!Gestures.drawerOpens(700, 0.95f, false, FLICK), "flick down closes however short");
        yes(Gestures.drawerOpens(-600, 0.1f, true, FLICK) == false, "exactly the flick speed is not a flick");
    }

    public static void testSettleTimeFollowsTheFinger() {
        eq(260L, Gestures.settleMs(500, 0), "no speed: the plain glide");
        eq(120L, Gestures.settleMs(100, 5000), "fast: never under 120 ms");
        eq(280L, Gestures.settleMs(2000, 2000), "slow: never over 280 ms");
        eq(200L, Gestures.settleMs(400, 2000), "in between: distance over speed");
    }

    public static void testSheetClosesPastAQuarterOrOnAFlick() {
        yes(Gestures.sheetCloses(true, 101, 400, 0, 800), "pulled past a quarter");
        yes(!Gestures.sheetCloses(true, 100, 400, 0, 800), "a quarter exactly springs back");
        yes(Gestures.sheetCloses(true, 10, 400, 801, 800), "a quick flick down");
        yes(!Gestures.sheetCloses(false, 300, 400, 2000, 800), "a cancelled touch never closes it");
    }

    public static void testCardFliesOffPastAThird() {
        yes(Gestures.thrown(-101, 300), "a third to the left");
        yes(!Gestures.thrown(100, 300), "a third exactly springs back");
    }

    public static void testAParentOnlyTakesTheDominantDragPastSlop() {
        yes(Gestures.startsDrag(9, 3, 8), "horizontal card drag");
        yes(!Gestures.startsDrag(8, 0, 8), "touch slop is exclusive");
        yes(!Gestures.startsDrag(9, 10, 8), "the cross-axis drag wins");
    }

    public static void testASingleTapFiresAtOnceWhenNoMultiTapIsSet() {
        yes(Gestures.firesNow(1, false), "nothing waits for more taps");
        yes(!Gestures.firesNow(1, true), "a double tap is set: wait");
        yes(!Gestures.firesNow(2, true), "a triple tap is set: wait");
        yes(Gestures.firesNow(3, true), "the third tap never waits");
    }

    public static void testTapsCapAtThree() {
        eq(0, Gestures.action(1), "one tap");
        eq(2, Gestures.action(3), "three taps");
        eq(2, Gestures.action(7), "more taps are still the triple");
    }

    public static void testTheButtonSnapsToTheNearerEdge() {
        yes(Gestures.leftEdge(100, 50, 1000), "centre at 125: left half");
        yes(!Gestures.leftEdge(475, 50, 1000), "centre at 500 exactly: right");
        yes(!Gestures.leftEdge(900, 50, 1000), "right half");
    }

    public static void testADrawerDragMustBeClearlyVertical() {
        yes(Gestures.startsDrag(20, 10 * 1.5f, 16), "twice as far as sideways");
        yes(!Gestures.startsDrag(20, 14 * 1.5f, 16), "a diagonal is not a drawer drag");
        yes(!Gestures.startsDrag(30, 20 * 1.5f, 16), "1.5 times exactly is not enough");
    }

    public static void testAlphabetGesturesStartAtEitherPhysicalEdge() {
        yes(Gestures.atEitherEdge(29, 1080, 58), "left edge");
        yes(Gestures.atEitherEdge(1051, 1080, 58), "right edge");
        yes(!Gestures.atEitherEdge(540, 1080, 58), "middle of the screen");
    }

    // Opposite-edge arguments are x px, screen width px, density, then whether the alphabet is on the left.
    public static void testTheOppositeAlphabetStripIs32DpOnTheLeftWhenRightHanded() {
        yes(Gestures.inOppositeEdgeGap(60, SCREEN, DENSITY, false), "right-handed: x 20 dp is inside the left strip");
        yes(Gestures.inOppositeEdgeGap(96, SCREEN, DENSITY, false), "right-handed: the inner 32 dp boundary belongs to the strip");
        yes(!Gestures.inOppositeEdgeGap(97, SCREEN, DENSITY, false), "right-handed: past 32 dp leaves the left strip");
    }

    public static void testTheOppositeAlphabetStripIs32DpOnTheRightWhenLeftHanded() {
        yes(Gestures.inOppositeEdgeGap(1020, SCREEN, DENSITY, true), "left-handed: x 20 dp from the right is inside the strip");
        yes(Gestures.inOppositeEdgeGap(984, SCREEN, DENSITY, true), "left-handed: the inner 32 dp boundary belongs to the strip");
        yes(!Gestures.inOppositeEdgeGap(983, SCREEN, DENSITY, true), "left-handed: past 32 dp leaves the right strip");
    }

    public static void testAppsAlwaysEndBeforeTheAlphabetTouchColumn() {
        eq(72, Gestures.alphabetEndPaddingDp(0), "visible or hidden: reserve the 58 dp alphabet column and 14 dp gap");
        eq(84, Gestures.alphabetEndPaddingDp(12), "the alphabet offset also moves the apps away from its touch column");
    }

    public static void testLegacyAppsOffsetsStartAtTheGestureStrip() {
        eq(32, Gestures.effectiveAppsOffsetDp(4), "a legacy offset below the strip starts at its inner edge");
        eq(32, Gestures.effectiveAppsOffsetDp(32), "the strip boundary stays unchanged");
        eq(36, Gestures.effectiveAppsOffsetDp(36), "an offset already outside the strip stays unchanged");
    }

    public static void testAfterALongPressMovingPastTheSlopPicksTheIconUpOtherwiseItsMenuStays() {
        yes(!Gestures.pickedUp(5, 6, 8), "held in place (7.8 px of 8): the menu stays");
        yes(Gestures.pickedUp(0, -8, 8), "8 px up, the slop itself: the menu closes, the drag starts");
        yes(Gestures.pickedUp(6, 6, 8), "8.5 px diagonally counts, any direction");
    }
}
