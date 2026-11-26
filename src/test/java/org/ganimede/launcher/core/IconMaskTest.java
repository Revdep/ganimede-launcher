package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.yes;

public class IconMaskTest {
    private static final int N = 20;

    /** An N x N square where fill(x, y) says which pixels are opaque. */
    private static int[] square(java.util.function.BiPredicate<Integer, Integer> fill) {
        int[] p = new int[N * N];
        for (int i = 0; i < p.length; i++) if (fill.test(i % N, i / N)) p[i] = 0xFF000000;
        return p;
    }

    public static void testOnlyLogoLikeAlphaMakesASilhouette() {
        yes(!IconMask.isUsable(square((x, y) -> false), N), "an empty drawable has no silhouette");
        yes(IconMask.isUsable(square((x, y) -> x >= 5 && x < 15 && y >= 5 && y < 15 && (x < 8 || y < 8)), N),
                "a logo in the middle of its square has a useful silhouette");
        yes(!IconMask.isUsable(square((x, y) -> true), N), "a full square would only make a featureless tile");
    }

    public static void testAFilledDiscIsNotALogo() {
        yes(!IconMask.isUsable(square((x, y) -> Math.hypot(x - 9.5, y - 9.5) <= 9.5), N),
                "a legacy round icon would become a flat disc");
        yes(IconMask.isUsable(square((x, y) -> {
            double d = Math.hypot(x - 9.5, y - 9.5);
            return d <= 9.5 && d >= 6;
        }), N), "a ring has see-through inside");
    }

    public static void testTheTwoPercentBound() {
        yes(IconMask.isUsable(square((x, y) -> (x == 0 && y < 4) || (x == N - 1 && y > N - 5)), N), "8 of 400 pixels is 2%");
        yes(!IconMask.isUsable(square((x, y) -> (x == 0 && y < 4) || (x == N - 1 && y == N - 1)), N), "5 of 400 pixels is less");
    }

    public static void testAQuarterOfTheBoundingBoxMustBeSeeThrough() {
        yes(IconMask.isUsable(square((x, y) -> !(x < 10 && y < 10)), N), "a quarter missing is enough");
        yes(!IconMask.isUsable(square((x, y) -> !(x < 10 && y < 10 && x + y < 19) || (x == 9 && y == 9)), N),
                "a little less is a solid shape");
    }
}
