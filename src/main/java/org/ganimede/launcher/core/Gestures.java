package org.ganimede.launcher.core;

/** What a finished swipe decides. Speeds in px/s, positive = down or right. */
public final class Gestures {
    /** Fixed physical-edge strip reserved for the alphabet gesture. */
    public static final int OPPOSITE_EDGE_STRIP_DP = 32;

    /**
     * Whether the classic drawer ends open: a flick faster than `flick` decides; otherwise a third of the way is enough
     * to switch, either way. at: how open it is (0 home .. 1 list); opening: the drag started on the home screen.
     */
    public static boolean drawerOpens(float vy, float at, boolean opening, float flick) {
        return Math.abs(vy) > flick ? vy < 0 : opening ? at > 0.3f : at > 0.7f;
    }

    /** How long the drawer glides on, at the finger's speed, over the distance left (px). */
    public static long settleMs(float dist, float speed) {
        return (long) Math.max(120, Math.min(280, Math.abs(speed) > 1 ? dist / Math.abs(speed) * 1000 : 260));
    }

    /** A bottom sheet pulled down by ty px closes past a quarter of its height or on a quick flick, if the finger lifted. */
    public static boolean sheetCloses(boolean lifted, float ty, float height, float vy, float flick) {
        return lifted && (ty > height / 4f || vy > flick);
    }

    /** A card dragged dx px sideways is thrown away past a third of its width. */
    public static boolean thrown(float dx, float width) {
        return Math.abs(dx) > width / 3f;
    }

    /** Whether one axis has moved past touch slop and farther than the other axis. */
    public static boolean startsDrag(float primary, float cross, float slop) {
        return Math.abs(primary) > slop && Math.abs(primary) > Math.abs(cross);
    }

    /** A tap answers at once unless a double or triple tap is set and fewer than three taps came. */
    public static boolean firesNow(int taps, boolean multi) {
        return !multi || taps >= 3;
    }

    /** Which tap action (0 single .. 2 triple) a run of taps means; more than three count as three. */
    public static int action(int taps) {
        return Math.min(3, taps) - 1;
    }

    /** Whether a button at x, w wide, rests nearer the left edge of a parent parentW wide. */
    public static boolean leftEdge(float x, float w, float parentW) {
        return x + w / 2f < parentW / 2f;
    }

    /** Whether x is inside the same reach from either physical side of the screen. */
    public static boolean atEitherEdge(float x, float width, float reach) {
        return Math.min(x, width - x) <= reach;
    }

    /** Whether x is in the fixed physical-edge strip opposite the alphabet. */
    public static boolean inOppositeEdgeGap(float x, float width, float density, boolean alphabetOnLeft) {
        float strip = OPPOSITE_EDGE_STRIP_DP * density;
        return alphabetOnLeft ? x >= width - strip : x <= strip;
    }

    /** End padding that keeps apps before the alphabet's 58dp touch column and its 14dp gap. */
    public static int alphabetEndPaddingDp(int alphabetOffsetDp) {
        return 58 + alphabetOffsetDp + 14;
    }

    /** Stored app offsets from older versions still begin outside the opposite-edge gesture strip. */
    public static int effectiveAppsOffsetDp(int storedDp) {
        return Math.max(OPPOSITE_EDGE_STRIP_DP, storedDp);
    }

    private Gestures() {}
}
