package org.ganimede.launcher.core;

/**
 * Which home page a sideways swipe lands on, and which pages may hold the focus. Pages run left to right for both
 * hands (left-hand use mirrors the List home only; Classic is never mirrored).
 */
public final class Paging {
    /** The most home pages: what a backup keeps (Backup's grid_pages) and every place that adds a page stops at. */
    public static final int MAX_PAGES = 100;

    /**
     * A flick faster than {@code flick} (px/s) turns the page its way, or cancels a drag it goes against; otherwise
     * a drag of a sixth of the width turns the page. dx and vx are the finger's travel and speed (negative = left).
     */
    public static int target(int from, int pages, float dx, float vx, float width, float flick) {
        int drag = -(int) Math.signum(dx), fling = -(int) Math.signum(vx);
        int dir = Math.abs(vx) > flick ? (drag != 0 && drag != fling ? 0 : fling) : Math.abs(dx) > width / 6 ? drag : 0;
        return Math.max(0, Math.min(pages - 1, from + dir));
    }

    /**
     * The pages whose views may take focus (Launcher3's PagedView.addFocusables): the page shown, and the next one on
     * the side focus moves to (side: -1 left, 1 right, 0 neither). Others are off screen, though a view of the page
     * before can end exactly at the screen's edge, where the pager counts it as shown and scrolls back to it.
     */
    public static int[] focusPages(int current, int pages, int side) {
        if (pages <= 0) return new int[0];
        current = Math.max(0, Math.min(pages - 1, current));
        int next = turn(current, pages, side);
        return next == current ? new int[]{current} : new int[]{current, next};
    }

    /** The page a side key turns to from {@code current} (side: -1 left, 1 right): the next one, or the same at the end. */
    public static int turn(int current, int pages, int side) {
        return Math.max(0, Math.min(pages - 1, current + side));
    }

    private Paging() {}
}
