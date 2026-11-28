package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Rectangular classic-home items laid over the existing icon cell map. */
public final class Blocks {
    public static final class Item {
        public final String id;
        public final int page, col, row, colSpan, rowSpan;

        public Item(String id, int page, int col, int row, int colSpan, int rowSpan) {
            this.id = id;
            this.page = page;
            this.col = col;
            this.row = row;
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Item)) return false;
            Item b = (Item) o;
            return id.equals(b.id) && page == b.page && col == b.col && row == b.row
                    && colSpan == b.colSpan && rowSpan == b.rowSpan;
        }

        @Override public int hashCode() {
            return Objects.hash(id, page, col, row, colSpan, rowSpan);
        }

        @Override public String toString() {
            return id + "@" + page + ":" + col + "," + row + "+" + colSpan + "x" + rowSpan;
        }
    }

    public static final class Result {
        public final List<Item> blocks;
        public final Map<Integer, String> cells;
        public final int pages;
        public final boolean changed;

        Result(List<Item> blocks, Map<Integer, String> cells, int pages, boolean changed) {
            this.blocks = blocks;
            this.cells = cells;
            this.pages = pages;
            this.changed = changed;
        }
    }

    public static boolean overlaps(Item a, Item b) {
        return a.page == b.page && a.col < b.col + b.colSpan && b.col < a.col + a.colSpan
                && a.row < b.row + b.rowSpan && b.row < a.row + a.rowSpan;
    }

    /** The grid cells (page * per + row * cols + col) the blocks cover, in order. */
    public static Set<Integer> cells(List<Item> blocks, int cols, int rows) {
        Set<Integer> out = new TreeSet<>();
        for (Item b : blocks)
            for (int r = b.row; r < b.row + b.rowSpan; r++)
                for (int c = b.col; c < b.col + b.colSpan; c++) out.add(b.page * cols * rows + r * cols + c);
        return out;
    }

    /** True when the rectangle is inside its page and touches neither a block nor an icon. */
    public static boolean free(Item at, List<Item> blocks, Map<Integer, String> cells, int cols, int rows) {
        if (!fits(at, cols, rows)) return false;
        for (Item b : blocks) if (!b.id.equals(at.id) && overlaps(at, b)) return false;
        for (int i : cells(List.of(at), cols, rows)) if (!empty(cells.get(i))) return false;
        return true;
    }

    /** Adds at the first free area from the current page onward, appending one empty page if needed. */
    public static Result add(String id, int colSpan, int rowSpan, int currentPage, int pages, int cols, int rows,
                             List<Item> blocks, Map<Integer, String> cells) {
        List<Item> out = new ArrayList<>(blocks);
        Map<Integer, String> icons = new TreeMap<>(cells);
        if (contains(out, id) || colSpan < 1 || rowSpan < 1 || colSpan > cols || rowSpan > rows)
            return new Result(out, icons, pages, false);
        for (int p = Math.max(0, currentPage); p < pages; p++) {
            Item at = first(id, colSpan, rowSpan, p, out, icons, cols, rows);
            if (at != null) { out.add(at); return new Result(out, icons, pages, true); }
        }
        Item at = new Item(id, pages, 0, 0, colSpan, rowSpan);
        out.add(at);
        return new Result(out, icons, pages + 1, true);
    }

    /** Moves or resizes one block. Other blocks are hard limits; icons underneath make room. */
    /** The block made to fit a grid that has become smaller (narrower first, then moved back inside); null if broken. */
    public static Item clamp(Item b, int cols, int rows) {
        if (b.page < 0 || b.col < 0 || b.row < 0 || b.colSpan < 1 || b.rowSpan < 1) return null;
        int w = Math.min(b.colSpan, cols), h = Math.min(b.rowSpan, rows);
        return new Item(b.id, b.page, Math.min(b.col, cols - w), Math.min(b.row, rows - h), w, h);
    }

    /** The block with one edge moved by cells: positive grows it, negative shrinks it (edge: left, right, top, bottom). */
    public static Item edgeStep(Item b, String edge, int cells) {
        switch (edge) {
            case "left": return new Item(b.id, b.page, b.col - cells, b.row, b.colSpan + cells, b.rowSpan);
            case "right": return new Item(b.id, b.page, b.col, b.row, b.colSpan + cells, b.rowSpan);
            case "top": return new Item(b.id, b.page, b.col, b.row - cells, b.colSpan, b.rowSpan + cells);
            default: return new Item(b.id, b.page, b.col, b.row, b.colSpan, b.rowSpan + cells);
        }
    }

    public static Result put(Item at, int pages, int cols, int rows, List<Item> blocks, Map<Integer, String> cells) {
        List<Item> out = new ArrayList<>();
        Item old = null;
        for (Item b : blocks) {
            if (b.id.equals(at.id)) old = b;
            else out.add(b);
        }
        Map<Integer, String> icons = new TreeMap<>(cells);
        if (at.page < 0 || at.page >= pages || !fits(at, cols, rows))
            return new Result(new ArrayList<>(blocks), icons, pages, false);
        for (Item b : out) if (overlaps(at, b))
            return new Result(new ArrayList<>(blocks), icons, pages, false);
        out.add(at);
        if (at.equals(old)) return new Result(out, icons, pages, false);
        int count = makeRoom(at, pages, cols, rows, out, icons);
        return new Result(out, icons, count, true);
    }

    /** First-version conversion: clock at the top, then each old strip widget in a free full-width block. */
    public static Result migrate(Map<Integer, String> cells, List<String> widgetIds, boolean clockOn,
                                 int pages, int cols, int rows) {
        List<Item> blocks = new ArrayList<>();
        Map<Integer, String> icons = new TreeMap<>(cells);
        int count = Math.max(1, pages), height = Math.min(2, rows);
        if (clockOn) {
            Item clock = new Item("clock", 0, 0, 0, cols, height);
            blocks.add(clock);
            count = makeRoom(clock, count, cols, rows, blocks, icons);
        }
        for (String id : widgetIds) {
            if (id == null || id.isEmpty() || contains(blocks, id)) continue;
            Result r = add(id, cols, height, 0, count, cols, rows, blocks, icons);
            blocks = r.blocks;
            icons = r.cells;
            count = r.pages;
        }
        return new Result(blocks, icons, count, clockOn || !widgetIds.isEmpty());
    }

    public static boolean contains(List<Item> blocks, String id) {
        for (Item b : blocks) if (b.id.equals(id)) return true;
        return false;
    }

    /** Page reorder matching Grid.movePage. */
    public static List<Item> movePage(List<Item> blocks, int pages, int from, int to) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pages; i++) order.add(i);
        order.add(to, order.remove(from));
        List<Item> out = new ArrayList<>();
        for (Item b : blocks) out.add(copy(b, order.indexOf(b.page)));
        return out;
    }

    /** Drops a page's blocks and shifts later pages back. */
    public static List<Item> deletePage(List<Item> blocks, int page) {
        List<Item> out = new ArrayList<>();
        for (Item b : blocks) if (b.page != page) out.add(copy(b, b.page > page ? b.page - 1 : b.page));
        return out;
    }

    private static Item first(String id, int w, int h, int page, List<Item> blocks, Map<Integer, String> cells,
                              int cols, int rows) {
        for (int row = 0; row <= rows - h; row++)
            for (int col = 0; col <= cols - w; col++) {
                Item at = new Item(id, page, col, row, w, h);
                if (free(at, blocks, cells, cols, rows)) return at;
            }
        return null;
    }

    /** Moves any icon lying under a block to the nearest free cell (adding a page only when full). */
    public static Result evict(List<Item> blocks, Map<Integer, String> cells, int pages, int cols, int rows) {
        Map<Integer, String> out = new TreeMap<>(cells);
        int count = pages;
        for (Item b : blocks) count = makeRoom(b, count, cols, rows, blocks, out);
        return new Result(new ArrayList<>(blocks), out, count, !out.equals(new TreeMap<>(cells)));
    }

    private static int makeRoom(Item block, int pages, int cols, int rows, List<Item> blocks,
                                Map<Integer, String> cells) {
        int per = cols * rows;
        List<Map.Entry<Integer, String>> moved = new ArrayList<>();
        for (int i : cells(List.of(block), cols, rows)) { // in order
            String value = cells.remove(i);
            if (!empty(value)) moved.add(Map.entry(i, value));
        }
        int count = pages;
        for (Map.Entry<Integer, String> icon : moved) {
            int target = nearest(icon.getKey(), count, cols, rows, blocks, cells);
            if (target < 0) {
                target = count * per;
                count++;
            }
            cells.put(target, icon.getValue());
        }
        return count;
    }

    private static int nearest(int from, int pages, int cols, int rows, List<Item> blocks,
                               Map<Integer, String> cells) {
        int per = cols * rows, fp = from / per, fr = from % per / cols, fc = from % cols;
        int best = -1, distance = Integer.MAX_VALUE;
        Set<Integer> covered = cells(blocks, cols, rows);
        for (int i = 0; i < pages * per; i++) {
            if (!empty(cells.get(i)) || covered.contains(i)) continue;
            int p = i / per, r = i % per / cols, c = i % cols;
            int d = Math.abs(p - fp) * (rows + cols) + Math.abs(r - fr) + Math.abs(c - fc);
            if (d < distance) { distance = d; best = i; }
        }
        return best;
    }

    private static boolean fits(Item b, int cols, int rows) {
        return b.col >= 0 && b.row >= 0 && b.colSpan >= 1 && b.rowSpan >= 1
                && b.col + b.colSpan <= cols && b.row + b.rowSpan <= rows;
    }

    private static boolean empty(String s) { return s == null || s.isEmpty(); }

    private static Item copy(Item b, int page) {
        return new Item(b.id, page, b.col, b.row, b.colSpan, b.rowSpan);
    }

    private Blocks() {}
}
