package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The classic home's fixed cells: index page * per + row * cols + col → entry ("component", "f:folder",
 * "s:shortcut"), with per = rows * cols cells on each page. A missing or empty value is an empty cell.
 */
public final class Grid {
    /** First empty cell from `from` to before `to` that no block covers (reserved), or -1 when they're all taken. */
    public static int firstEmpty(Map<Integer, String> cells, Collection<Integer> reserved, int from, int to) {
        for (int i = from; i < to; i++) if (!reserved.contains(i) && empty(cells, i)) return i;
        return -1;
    }

    /** The cell `want` while it's empty and uncovered, else the first free one from `from` to before `to`; -1: none. */
    public static int freeCell(Map<Integer, String> cells, Collection<Integer> reserved, int want, int from, int to) {
        return want >= 0 && firstEmpty(cells, reserved, want, want + 1) == want ? want : firstEmpty(cells, reserved, from, to);
    }

    /**
     * Columns, rows and dock slots for a screen of this size in dp: 5x5 and 5 from 406x694, 4x4 and 4 from 255x420, else 3x3.
     * Judged on the short and the long side, so the same device gets the same grid in either orientation.
     */
    public static int[] defaults(float widthDp, float heightDp) {
        float shortSide = Math.min(widthDp, heightDp), longSide = Math.max(widthDp, heightDp);
        int n = shortSide >= 406 && longSide >= 694 ? 5 : shortSide >= 255 && longSide >= 420 ? 4 : 3;
        return new int[]{n, n, n};
    }

    /** First use: the favourites, in order, as far as they fit. */
    public static Map<Integer, String> seed(List<String> favourites, int size) {
        Map<Integer, String> out = new TreeMap<>();
        for (int i = 0; i < Math.min(size, favourites.size()); i++) out.put(i, favourites.get(i));
        return out;
    }

    /**
     * Applies a favourites change to classic placement. Existing entries keep their cells, including favourites moved
     * by hand; newly chosen ones use free cells on page one. Returns chosen entries that did not fit there.
     */
    public static List<String> syncFavourites(Map<Integer, String> cells, Collection<Integer> reserved,
                                               Collection<String> elsewhere, List<String> before,
                                               List<String> after, int firstPageSize) {
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);
        cells.values().removeIf(removed::contains);
        Set<String> outside = new HashSet<>(elsewhere);
        Set<String> existingFavourites = new HashSet<>(before);
        Set<String> handled = new HashSet<>();
        List<String> missing = new ArrayList<>();
        for (String entry : after) {
            if (!handled.add(entry) || outside.contains(entry)) continue;
            int current = -1;
            for (Map.Entry<Integer, String> cell : cells.entrySet())
                if (entry.equals(cell.getValue())) { current = cell.getKey(); break; }
            if (current >= 0 && (current < firstPageSize || existingFavourites.contains(entry))) continue;
            int at = firstEmpty(cells, reserved, 0, firstPageSize);
            if (at < 0) { if (current < 0) missing.add(entry); }
            else {
                cells.values().removeIf(entry::equals);
                cells.put(at, entry);
            }
        }
        return missing;
    }

    /** Page `from` moved to position `to`; the pages between shift over by one. Cells past the last page are dropped. */
    public static Map<Integer, String> movePage(Map<Integer, String> cells, int per, int pages, int from, int to) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pages; i++) order.add(i);
        order.add(to, order.remove(from));
        Map<Integer, String> out = new TreeMap<>();
        for (int p = 0; p < pages; p++)
            for (int c = 0; c < per; c++) {
                String e = cells.get(order.get(p) * per + c);
                if (e != null && !e.isEmpty()) out.put(p * per + c, e);
            }
        return out;
    }

    /** Page `page` taken out: the later pages move back one. */
    public static Map<Integer, String> deletePage(Map<Integer, String> cells, int per, int pages, int page) {
        Map<Integer, String> out = movePage(cells, per, pages, page, pages - 1);
        out.keySet().removeIf(i -> i >= (pages - 1) * per);
        return out;
    }

    /**
     * Drawer off: every app lives on the home pages. The apps (in the order given) that aren't on a page, in the dock
     * or in a folder there go into the free cells from page two on. Placements hidden by a temporarily smaller grid
     * stay saved. Returns the page count to keep, or 0 when nothing changed.
     */
    public static int placeAll(Map<Integer, String> cells, int per, int pages, Collection<String> dock, List<String> apps,
                               Function<String, List<String>> folderApps) {
        Set<String> placed = new HashSet<>(dock);
        placed.addAll(cells.values());
        for (String e : new ArrayList<>(placed)) if (e.startsWith("f:")) placed.addAll(folderApps.apply(e));
        List<String> missing = new ArrayList<>(apps);
        missing.removeAll(placed);
        if (missing.isEmpty()) return 0;
        int at = per;
        for (String e : missing) {
            while (!empty(cells, at)) at++;
            cells.put(at++, e);
        }
        return Math.max(pages, (at + per - 1) / per);
    }

    private static boolean empty(Map<Integer, String> cells, int i) {
        String e = cells.get(i);
        return e == null || e.isEmpty();
    }

    private Grid() {}
}
