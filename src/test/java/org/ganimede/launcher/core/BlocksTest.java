package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class BlocksTest {
    private static Map<Integer, String> cells(Object... kv) {
        Map<Integer, String> out = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((Integer) kv[i], (String) kv[i + 1]);
        return out;
    }

    private static Blocks.Item b(String id, int page, int col, int row, int cols, int rows) {
        return new Blocks.Item(id, page, col, row, cols, rows);
    }

    public static void testCellsAreTheGridIndexesEveryBlockCovers() {
        // 3 columns x 2 rows, 6 cells a page: a 2x1 on page 0 row 1, a 1x2 on page 1 column 2
        eq(Set.of(3, 4, 8, 11), Blocks.cells(List.of(b("clock", 0, 0, 1, 2, 1), b("7", 1, 2, 0, 1, 2)), 3, 2),
                "page * per + row * cols + col for each covered cell");
        eq(Set.of(), Blocks.cells(List.of(), 3, 2), "no blocks, no cells");
    }

    public static void testCollisionIncludesBlocksAndIconCells() {
        Blocks.Item placed = b("clock", 0, 1, 1, 2, 2);
        yes(Blocks.overlaps(placed, b("7", 0, 2, 2, 1, 1)), "rectangles overlap");
        yes(!Blocks.overlaps(placed, b("7", 1, 2, 2, 1, 1)), "another page does not overlap");
        yes(!Blocks.free(b("8", 0, 0, 0, 1, 1), List.of(placed), cells(0, "app"), 4, 4),
                "an icon occupies its cell");
        yes(Blocks.free(b("8", 0, 3, 0, 1, 1), List.of(placed), cells(0, "app"), 4, 4),
                "a clear cell is free");
    }

    public static void testAddSearchesCurrentThenFollowingPages() {
        Map<Integer, String> icons = cells(9, "a");
        Blocks.Result r = Blocks.add("9", 2, 1, 1, 2, 3, 2, List.of(b("clock", 1, 0, 0, 2, 1)), icons);
        eq(List.of(b("clock", 1, 0, 0, 2, 1), b("9", 1, 1, 1, 2, 1)), r.blocks,
                "first fitting area on the current page");
        eq(2, r.pages, "no page added");
    }

    public static void testAddCreatesOnePageOnlyWhenNeeded() {
        Blocks.Result r = Blocks.add("9", 2, 2, 0, 1, 2, 2, List.of(),
                cells(0, "a", 1, "b", 2, "c", 3, "d"));
        eq(List.of(b("9", 1, 0, 0, 2, 2)), r.blocks, "block starts the new page");
        eq(2, r.pages, "one page added");
    }

    public static void testMoveMakesRoomInNearestFreeCellsInReadingOrder() {
        Map<Integer, String> icons = cells(0, "a", 1, "b", 4, "x");
        Blocks.Result r = Blocks.put(b("7", 0, 0, 0, 2, 1), 1, 3, 2, List.of(b("7", 0, 2, 1, 1, 1)), icons);
        eq(cells(2, "b", 3, "a", 4, "x"), r.cells,
                "covered icons move nearest first; ties use cell reading order");
        eq(List.of(b("7", 0, 0, 0, 2, 1)), r.blocks, "the block moved");
    }

    public static void testMakeRoomAddsAPageOnlyAfterExistingPagesFill() {
        Map<Integer, String> icons = cells(0, "a", 1, "b", 2, "c", 3, "d");
        Blocks.Result r = Blocks.put(b("clock", 0, 0, 0, 2, 1), 1, 2, 2, List.of(), icons);
        eq(cells(2, "c", 3, "d", 4, "a", 5, "b"), r.cells, "displaced icons continue on the new page");
        eq(2, r.pages, "one page is enough");
    }

    public static void testResizeIsLimitedByGridAndOtherBlocks() {
        Blocks.Item old = b("7", 0, 1, 1, 1, 1);
        List<Blocks.Item> blocks = List.of(old, b("clock", 0, 0, 0, 2, 1));
        Blocks.Result outside = Blocks.put(b("7", 0, 1, 1, 3, 1), 1, 3, 3, blocks, cells());
        yes(!outside.changed, "past the right edge is rejected");
        Blocks.Result collision = Blocks.put(b("7", 0, 0, 0, 2, 2), 1, 3, 3, blocks, cells());
        yes(!collision.changed, "another block limits resize");
        Blocks.Result ok = Blocks.put(b("7", 0, 1, 1, 2, 2), 1, 3, 3, blocks, cells(5, "app"));
        yes(ok.changed, "icons make room for a valid resize");
    }

    public static void testMigrationPlacesClockFirstAndLegacyWidgetsAfterIt() {
        Map<Integer, String> icons = cells(0, "a", 1, "b", 2, "c");
        Blocks.Result r = Blocks.migrate(icons, List.of("10", "11"), true, 1, 3, 3);
        eq(b("clock", 0, 0, 0, 3, 2), r.blocks.get(0), "clock at the top, full width and two rows");
        eq(b("10", 1, 0, 0, 3, 2), r.blocks.get(1), "first old widget in the first fitting area");
        eq(b("11", 2, 0, 0, 3, 2), r.blocks.get(2), "second old widget gets the next page");
        eq(cells(6, "a", 7, "b", 8, "c"), r.cells, "clock migration makes room for old icons");
        eq(3, r.pages, "migration adds only the pages it needs");
    }

    public static void testPageEditsMoveAndDeleteBlocksWithTheirPages() {
        List<Blocks.Item> blocks = List.of(b("clock", 0, 0, 0, 3, 2), b("9", 2, 1, 1, 1, 1));
        eq(List.of(b("clock", 2, 0, 0, 3, 2), b("9", 1, 1, 1, 1, 1)),
                Blocks.movePage(blocks, 3, 0, 2), "page reorder");
        eq(List.of(b("9", 1, 1, 1, 1, 1)), Blocks.deletePage(blocks, 0),
                "deleted page's block is gone and later pages shift back");
    }

    public static void testBlockShrinksToANarrowerGrid() {
        // Going from 5 to 4 columns must keep the full-width clock, just narrower, not drop it.
        eq(b("clock", 0, 0, 0, 4, 2), Blocks.clamp(b("clock", 0, 0, 0, 5, 2), 4, 5), "full width stays full width");
        eq(b("9", 0, 2, 3, 2, 2), Blocks.clamp(b("9", 0, 3, 4, 2, 2), 4, 5), "moved back inside the grid");
        eq(b("9", 0, 1, 1, 2, 2), Blocks.clamp(b("9", 0, 1, 1, 2, 2), 4, 5), "a block that fits is unchanged");
        eq(null, Blocks.clamp(b("9", 0, -1, 0, 2, 2), 4, 5), "a broken block is dropped");
    }

    public static void testIconsNeverStayUnderABlock() {
        // Favourites seeded before the clock block existed sit in its cells: they move to the nearest free cells.
        List<Blocks.Item> blocks = List.of(b("clock", 0, 0, 0, 4, 2));
        Map<Integer, String> g = cells(0, "a", 1, "b", 9, "c");
        Blocks.Result r = Blocks.evict(blocks, g, 1, 4, 4);
        yes(r.changed, "something moved");
        eq(cells(8, "a", 9, "c", 10, "b"), r.cells, "a and b leave the clock's 2 rows, c stays");
        eq(1, r.pages, "no page added while there is room");
        yes(!Blocks.evict(blocks, cells(8, "a"), 1, 4, 4).changed, "nothing under the block: unchanged");
    }

    public static void testEdgeStepGrowsAndShrinksOneCell() {
        Blocks.Item at = b("9", 0, 1, 1, 2, 2);
        eq(b("9", 0, 1, 1, 3, 2), Blocks.edgeStep(at, "right", 1), "right grows");
        eq(b("9", 0, 0, 1, 3, 2), Blocks.edgeStep(at, "left", 1), "left grows towards the left");
        eq(b("9", 0, 1, 1, 2, 1), Blocks.edgeStep(at, "bottom", -1), "bottom shrinks");
        eq(b("9", 0, 1, 2, 2, 1), Blocks.edgeStep(at, "top", -1), "top shrinks from above");
    }

    public static void testEdgeStepNeverBelowOneCell() {
        Blocks.Item at = b("9", 0, 1, 1, 2, 1);
        Blocks.Result r = Blocks.put(Blocks.edgeStep(at, "bottom", -1), 1, 4, 4, List.of(at), cells());
        yes(!r.changed, "a block cannot vanish by shrinking");
    }
}
