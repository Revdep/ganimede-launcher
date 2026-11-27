package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;

public class FolderOrderTest {
    // a 2-column grid of 5 icons, 100 wide and 80 tall each: slots 0..4 have top-left corners at
    // (50,40) (150,40) (50,120) (150,120) (50,200)
    private static final float[] XS = {50, 150, 50, 150, 50}, YS = {40, 40, 120, 120, 200};

    public static void testTheSlotNearestTheFingerIsPicked() {
        eq(0, FolderOrder.nearest(60, 30, XS, YS), "finger over the first icon");
        eq(3, FolderOrder.nearest(140, 130, XS, YS), "finger over the fourth icon");
        eq(1, FolderOrder.nearest(190, 60, XS, YS), "between rows, nearer the top right one");
    }

    public static void testAFingerOutsideTheFolderStillLandsOnAnInsideSlot() {
        eq(4, FolderOrder.nearest(-500, 900, XS, YS), "far below-left: the last slot, never outside");
        eq(1, FolderOrder.nearest(900, -900, XS, YS), "far above-right: the top right slot");
    }

    public static void testMovingForwardShiftsTheOthersBack() {
        eq(List.of("b", "c", "a", "d"), FolderOrder.move(List.of("a", "b", "c", "d"), 0, 2), "a goes to place 2");
    }

    public static void testMovingBackShiftsTheOthersForward() {
        eq(List.of("a", "d", "b", "c"), FolderOrder.move(List.of("a", "b", "c", "d"), 3, 1), "d goes to place 1");
    }

    public static void testMovingOntoItselfOrPastTheEndsKeepsEveryEntry() {
        eq(List.of("a", "b", "c"), FolderOrder.move(List.of("a", "b", "c"), 1, 1), "same place: unchanged");
        eq(List.of("b", "c", "a"), FolderOrder.move(List.of("a", "b", "c"), 0, 9), "past the end: last place");
        eq(List.of("c", "a", "b"), FolderOrder.move(List.of("a", "b", "c"), 2, -4), "before the start: first place");
    }

    public static void testReorderedShownEntriesTakeTheirOwnPlacesAmongHiddenOnes() {
        // "x" is an app no longer installed: not shown, it keeps its place
        eq(List.of("c", "x", "a", "b"), FolderOrder.reorderShown(List.of("a", "x", "b", "c"), List.of("c", "a", "b")),
                "the shown ones are permuted, the missing one stays where it was");
    }

    public static void testAnEntryNoLongerInTheFolderLeavesTheStoredOrderUntouched() {
        // the folder was edited while the pop-up was open: "c" is not stored any more, so the order cannot be applied
        eq(List.of("a", "b"), FolderOrder.reorderShown(List.of("a", "b"), List.of("b", "c", "a")),
                "a stale shown list changes nothing");
        eq(List.of("a", "b"), FolderOrder.reorderShown(List.of("a", "b"), List.of("b")),
                "a shown list that lost an entry changes nothing");
    }

    public static void testChoosingAppsKeepsTheOrderAndCanDropAShortcut() {
        // stored: a shortcut between two apps; the dialog lists the shortcut first, then the apps alphabetically
        List<String> stored = List.of("b", "s:pkg|id|Mail", "a");
        eq(List.of("b", "a", "c"), FolderOrder.chosen(stored, List.of("a", "b", "c")),
                "the unchecked shortcut leaves, the kept ones keep their order, a new one goes last");
        eq(stored, FolderOrder.chosen(stored, List.of("s:pkg|id|Mail", "a", "b")), "nothing changed: the same order");
    }
}
