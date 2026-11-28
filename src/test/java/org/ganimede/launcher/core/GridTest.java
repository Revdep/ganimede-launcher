package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class GridTest {
    private static Map<Integer, String> cells(Object... kv) {
        Map<Integer, String> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Integer) kv[i], (String) kv[i + 1]);
        return m;
    }

    public static void testFirstEmptyCell() {
        eq(1, Grid.firstEmpty(cells(0, "a", 2, "b"), Set.of(), 0, 4), "the gap");
        eq(0, Grid.firstEmpty(cells(0, ""), Set.of(), 0, 4), "an empty name is an empty cell");
        eq(-1, Grid.firstEmpty(cells(0, "a", 1, "b"), Set.of(), 0, 2), "every page full");
        eq(-1, Grid.firstEmpty(cells(), Set.of(), 0, 0), "no cells at all");
        eq(6, Grid.firstEmpty(cells(1, "", 4, "a"), Set.of(5), 4, 8), "page two of four cells: past a full and a reserved cell");
        eq(-1, Grid.firstEmpty(cells(4, "a", 5, "b"), Set.of(6, 7), 4, 8), "page two taken by icons and a block");
    }

    public static void testFreeCellKeepsThePressedCellOnlyWhileItIsEmpty() {
        eq(5, Grid.freeCell(cells(4, "a"), Set.of(), 5, 4, 8), "the pressed cell, still empty");
        eq(6, Grid.freeCell(cells(4, "a", 5, "new app"), Set.of(), 5, 4, 8), "taken meanwhile: the page's next free one");
        eq(7, Grid.freeCell(cells(4, "a"), Set.of(5, 6), 5, 4, 8), "covered by a block meanwhile");
        eq(-1, Grid.freeCell(cells(4, "a", 5, "b", 6, "c", 7, "d"), Set.of(), 5, 4, 8), "the page filled up");
        eq(4, Grid.freeCell(cells(), Set.of(), -1, 4, 8), "nothing pressed: the page's first free one");
    }

    public static void testSeedTakesTheFavouritesInOrderUpToTheSize() {
        eq(cells(0, "a", 1, "f:1"), Grid.seed(List.of("a", "f:1", "s:x"), 2), "the first two");
    }

    public static void testFavouritesShareTheFirstPageWithoutResettingManualMoves() {
        Map<Integer, String> g = cells(0, "b", 1, "x", 4, "a");
        eq(List.of(), Grid.syncFavourites(g, Set.of(2), List.of("d"), List.of("a", "b", "d"),
                List.of("a", "c", "d"), 4), "every new favourite fits");
        eq(cells(0, "c", 1, "x", 4, "a"), g,
                "removed favourites leave; a manually moved favourite stays put; the new one uses page one");
    }

    public static void testFavouritesReportThoseThatDoNotFitOnTheFirstPage() {
        Map<Integer, String> g = cells(0, "x");
        eq(List.of("b"), Grid.syncFavourites(g, Set.of(), List.of(), List.of(), List.of("a", "b"), 2),
                "the item beyond the page is reported");
        eq(cells(0, "x", 1, "a"), g, "free first-page cells are still used");
    }

    public static void testNewFavouriteMovesFromAnAutomaticLaterPage() {
        Map<Integer, String> g = cells(1, "x", 4, "a", 5, "c");
        Grid.syncFavourites(g, Set.of(2), List.of(), List.of("a"), List.of("a", "c"), 4);
        eq(cells(0, "c", 1, "x", 4, "a"), g,
                "an existing favourite keeps its manual page while a newly chosen app joins page one");
    }

    public static void testMovePageShiftsThoseBetween() {
        // 2 cells per page, 3 pages: page 0 goes last, pages 1 and 2 move back one
        Map<Integer, String> g = cells(0, "a", 1, "b", 2, "c", 5, "d");
        eq(cells(0, "c", 3, "d", 4, "a", 5, "b"), Grid.movePage(g, 2, 3, 0, 2), "first to last");
        eq(cells(0, "c", 2, "a", 3, "b", 5, "d"), Grid.movePage(g, 2, 3, 1, 0), "second to first");
    }

    public static void testDeletePageMovesTheLaterOnesBack() {
        Map<Integer, String> g = cells(0, "a", 2, "b", 3, "c", 4, "d");
        eq(cells(0, "a", 2, "d"), Grid.deletePage(g, 2, 3, 1), "page two gone, page three now second");
        eq(cells(0, "b", 1, "c", 2, "d"), Grid.deletePage(g, 2, 3, 0), "first page gone");
    }

    public static void testPlaceAllFillsFreeCellsFromPageTwo() {
        // 2 cells per page, 1 page; "x" is temporarily hidden past it, "b" sits in a folder, "c" in the dock
        Map<Integer, String> g = cells(0, "f:1", 7, "x");
        int pages = Grid.placeAll(g, 2, 1, List.of("c"), List.of("a", "b", "c", "x", "y"),
                e -> e.equals("f:1") ? List.of("b") : List.of());
        eq(cells(0, "f:1", 2, "a", 3, "y", 7, "x"), g,
                "missing apps fill visible cells while hidden placement stays saved");
        eq(2, pages, "only missing apps add pages");
    }

    public static void testPlaceAllSkipsTakenCellsAndKeepsMorePages() {
        Map<Integer, String> g = cells(2, "a", 4, "b");
        eq(4, Grid.placeAll(g, 2, 4, List.of(), List.of("a", "b", "c"), e -> List.of()), "never fewer pages");
        eq(cells(2, "a", 3, "c", 4, "b"), g, "into the first free cell from page two");
    }

    public static void testPlaceAllWithNothingMissingChangesNothing() {
        Map<Integer, String> g = cells(0, "a", 1, "b");
        eq(0, Grid.placeAll(g, 2, 1, List.of(), List.of("a"), e -> List.of()), "nothing to place");
        eq(cells(0, "a", 1, "b"), g, "untouched");
    }

    public static void testPlaceAllKeepsCellsHiddenByATemporarySmallerGrid() {
        Map<Integer, String> g = cells(0, "a", 9, "far");
        eq(0, Grid.placeAll(g, 2, 1, List.of(), List.of("a", "far"), e -> List.of()), "nothing needs moving");
        eq(cells(0, "a", 9, "far"), g, "the hidden placement returns when the grid grows again");
    }

    public static void testDefaultsStableAcrossOrientation() {
        eq("[5, 5, 5]", java.util.Arrays.toString(Grid.defaults(412, 915)), "portrait");
        eq("[5, 5, 5]", java.util.Arrays.toString(Grid.defaults(915, 412)), "landscape, same grid");
        eq("[4, 4, 4]", java.util.Arrays.toString(Grid.defaults(300, 500)), "smaller");
        eq("[3, 3, 3]", java.util.Arrays.toString(Grid.defaults(200, 300)), "tiny");
    }

    private static String[] dock(String... s) { return s; }

    private static String show(Grid.Drop d) {
        return d.kind + " " + d.cells + " " + java.util.Arrays.toString(d.dock);
    }

    public static void testDropOnAnEmptyCellOrSlotMoves() {
        eq("MOVE {5=a} [, ]", show(Grid.drop(cells(0, "a"), dock("", ""), "g:0", "g:5", "a", "")), "grid to grid");
        eq("MOVE {} [a, ]", show(Grid.drop(cells(0, "a"), dock("", ""), "g:0", "d:0", "a", "")), "grid to dock");
        eq("MOVE {3=a} [, ]", show(Grid.drop(cells(), dock("a", ""), "d:0", "g:3", "a", "")), "dock to grid");
        eq("MOVE {} [, a]", show(Grid.drop(cells(), dock("a", ""), "d:0", "d:1", "a", "")), "dock to dock");
    }

    public static void testDropOnItselfDoesNothing() {
        eq("NONE {0=a} [, ]", show(Grid.drop(cells(0, "a"), dock("", ""), "g:0", "g:0", "a", "")), "own cell");
        eq("NONE {} [a, ]", show(Grid.drop(cells(), dock("a", ""), "d:0", "d:0", "a", "")), "own slot");
    }

    public static void testDropOnAnotherAppMakesAFolder() {
        Grid.Drop d = Grid.drop(cells(0, "a", 1, "b"), dock("", ""), "g:0", "g:1", "a", "b");
        eq("NEW_FOLDER {1=a} [, ]", show(d), "the cell holds the dragged one until the folder exists");
        d.fill("f:7");
        eq("{1=f:7}", d.cells.toString(), "then the folder takes the cell");
    }

    public static void testDropOnADockedAppSwaps() {
        eq("SWAP {0=b} [, a]", show(Grid.drop(cells(0, "a"), dock("", "b"), "g:0", "d:1", "a", "b")), "no folder in the dock");
    }

    public static void testDropAnAppOnAFolderJoinsIt() {
        eq("JOIN_FOLDER {1=f:1} [, ]", show(Grid.drop(cells(0, "a", 1, "f:1"), dock("", ""), "g:0", "g:1", "a", "f:1")), "grid");
        eq("JOIN_FOLDER {} [f:1, ]", show(Grid.drop(cells(0, "a"), dock("f:1", ""), "g:0", "d:0", "a", "f:1")), "dock folder too");
    }

    public static void testDropAFolderOnAnIconSwaps() {
        eq("SWAP {0=a, 1=f:1} [, ]", show(Grid.drop(cells(0, "f:1", 1, "a"), dock("", ""), "g:0", "g:1", "f:1", "a")), "folder on app");
        eq("SWAP {0=f:2, 1=f:1} [, ]", show(Grid.drop(cells(0, "f:1", 1, "f:2"), dock("", ""), "g:0", "g:1", "f:1", "f:2")), "folder on folder");
        eq("SWAP {0=a} [f:1, ]", show(Grid.drop(cells(0, "f:1"), dock("a", ""), "g:0", "d:0", "f:1", "a")), "folder on a docked app");
    }

    public static void testDropOnRemoveTakesItOffTheHome() {
        eq("REMOVE {} [, ]", show(Grid.drop(cells(0, "a"), dock("", ""), "g:0", "remove", "a", "")), "from the grid");
        eq("REMOVE {} [, ]", show(Grid.drop(cells(), dock("a", ""), "d:0", "remove", "a", "")), "from the dock");
    }

    public static void testDropDoesNotChangeItsInputs() {
        Map<Integer, String> c = cells(0, "a");
        String[] d = dock("", "");
        Grid.drop(c, d, "g:0", "g:5", "a", "");
        eq("{0=a}", c.toString(), "cells");
        eq("[, ]", java.util.Arrays.toString(d), "dock");
    }
}
