package org.ganimede.launcher.core;

/** Which home page a sideways swipe lands on. */
public final class Paging {
    /**
     * A flick faster than {@code flick} (px/s) turns the page its way, or cancels a drag it goes against; otherwise
     * a drag of a sixth of the width turns the page. dx and vx are the finger's travel and speed (negative = left).
     */
    public static int target(int from, int pages, float dx, float vx, float width, float flick, boolean rtl) {
        int drag = -(int) Math.signum(dx), fling = -(int) Math.signum(vx);
        int dir = Math.abs(vx) > flick ? (drag != 0 && drag != fling ? 0 : fling) : Math.abs(dx) > width / 6 ? drag : 0;
        if (rtl) dir = -dir;
        return Math.max(0, Math.min(pages - 1, from + dir));
    }

    private Paging() {}
}
