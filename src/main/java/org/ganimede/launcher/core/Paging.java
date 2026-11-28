package org.ganimede.launcher.core;

/**
 * Which home page a sideways swipe lands on, and which pages may hold the focus. Pages run left to right for both
 * hands (left-hand use mirrors the rest of the home, not the pages: their grid is in screen coordinates).
 */
public final class Paging {
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
        int next = current + side;
        return side == 0 || next < 0 || next >= pages ? new int[]{current} : new int[]{current, next};
    }

    private Paging() {}
}
