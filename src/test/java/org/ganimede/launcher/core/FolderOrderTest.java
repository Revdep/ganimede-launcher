package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;
import java.util.Map;

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

    public static void testAPopUpLooksTheSameUnlessWhatItDrawsChanges() {
        Map<String, String> names = Map.of("a", "Mail", "b", "Maps"), icons = Map.of("a", "", "b", "arcticons|maps");
        var before = FolderOrder.looks(List.of("a", "b"), names::get, icons::get);
        eq(before, FolderOrder.looks(List.of("b", "a"), names::get, icons::get), "reordered: the same look");
        Map<String, String> renamed = Map.of("a", "Posta", "b", "Maps");
        eq(false, before.equals(FolderOrder.looks(List.of("a", "b"), renamed::get, icons::get)), "renamed: rebuilt");
        Map<String, String> picked = Map.of("a", "arcticons|okay", "b", "arcticons|maps");
        eq(false, before.equals(FolderOrder.looks(List.of("a", "b"), names::get, picked::get)), "a new icon: rebuilt");
        eq(false, before.equals(FolderOrder.looks(List.of("a", "b"), names::get, Map.of("b", "arcticons|maps")::get)),
                "an entry no longer drawn (uninstalled): rebuilt");
    }

    public static void testAnUpdateOfAnAppInTheFolderRedrawsItsIconOtherAppsUpdatesChangeNothing() {
        List<String> stored = List.of("a", "b"); // c is installed but not in this folder
        Map<String, String> names = Map.of("a", "Mail", "b", "Maps", "c", "Camera");
        String a = FolderOrder.drawnIcon("", "/data/app/~~x1/a-1"), b = FolderOrder.drawnIcon("", "/data/app/~~y1/b-1");
        String c = FolderOrder.drawnIcon("", "/data/app/~~z1/c-1");
        Map<String, String> before = Map.of("a", a, "b", b, "c", c);
        Map<String, String> otherUpdated = Map.of("a", a, "b", b, "c", FolderOrder.drawnIcon("", "/data/app/~~z2/c-2"));
        Map<String, String> mailUpdated = Map.of("a", FolderOrder.drawnIcon("", "/data/app/~~x2/a-2"), "b", b, "c", c);
        var look = FolderOrder.looks(stored, names::get, before::get);
        eq(look, FolderOrder.looks(stored, names::get, otherUpdated::get), "Camera updated: same look");
        eq(false, look.equals(FolderOrder.looks(stored, names::get, mailUpdated::get)),
                "Mail, in the folder, updated: rebuilt (its own icon may have changed)");
        eq(false, a.equals(FolderOrder.drawnIcon("arcticons|okay", "/data/app/~~x1/a-1")), "a picked icon: new look");
    }
}
