package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;

import org.ganimede.launcher.core.IconSet.Cap;
import org.ganimede.launcher.core.IconSet.Join;
import org.ganimede.launcher.core.IconSet.Op;
import org.ganimede.launcher.core.IconSet.PathData;

/** A glyph on a container: centred on its drawn bounds, 26/48 of the side, the stroke in px kept (issue 078). */
public class GlyphFitTest {
    private static void near(double expected, double actual, String what) {
        if (Math.abs(expected - actual) > 1e-3) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    private static void fit(GlyphFit f, double scale, double dx, double dy, double strokePx) {
        near(scale, f.scale(), "scale");
        near(dx, f.dx(), "x");
        near(dy, f.dy(), "y");
        near(strokePx, f.strokePx(), "stroke in px");
    }

    private static PathData line(float... points) {
        Op[] ops = new Op[points.length / 2];
        for (int i = 0; i < ops.length; i++) ops[i] = i == 0 ? Op.MOVE : Op.LINE;
        return new PathData(true, false, false, Cap.ROUND, Join.ROUND, 1, 1, ops, points);
    }

    public static void testCentresOnDrawnBounds() {
        fit(GlyphFit.onContainer(new float[]{8, 12, 28, 36}, 1, 96), 50 / 24.0, 10.5, -2, 2);
    }

    public static void testStrokeStaysConstant() {
        fit(GlyphFit.onContainer(new float[]{8, 12, 28, 36}, 2.2f, 96), 47.6 / 24, 12.3, 0.4, 4.4);
    }

    public static void testWideGlyphLimitedByWidth() {
        fit(GlyphFit.onContainer(new float[]{2, 20, 46, 28}, 1, 96), 50 / 44.0, 20.727273, 20.727273, 2);
    }

    public static void testNoContainerKeepsNativeGrid() {
        fit(GlyphFit.grid(1, 96), 2, 0, 0, 2);
    }

    public static void testBoundsOfEveryPath() {
        PathData oval = new PathData(false, true, false, Cap.BUTT, Join.MITER, 0, 1, new Op[]{Op.OVAL}, new float[]{30, 30, 40, 44});
        eq(List.of(8f, 12f, 40f, 44f), toList(GlyphFit.bounds(List.of(line(8, 12, 28, 36), oval))), "all paths' points, ovals by their box");
    }

    public static void testZeroLengthSubpathsAreDots() {
        // "M p L p": a dot that only a ROUND (or SQUARE) cap draws; Sharp's BUTT would lose it. It ends where it starts,
        // so the decoder marks its move MOVE_CLOSED; the open segment between keeps MOVE.
        PathData dashes = new PathData(true, false, false, Cap.BUTT, Join.MITER, 1, 1,
                new Op[]{Op.MOVE_CLOSED, Op.LINE, Op.MOVE, Op.LINE, Op.MOVE_CLOSED, Op.LINE},
                new float[]{4, 4, 4, 4, 10, 10, 20, 10, 30, 6, 30, 6});
        eq(List.of(4f, 4f, 30f, 6f), toList(GlyphFit.dots(dashes)), "the two zero-length subpaths, not the segment");
        eq(List.of(), toList(GlyphFit.dots(line(8, 12, 28, 36))), "a real segment is no dot");
    }

    public static void testSharpKeepsDotsRound() {
        eq(Cap.ROUND, IconLook.of("sharp", "none", "solid", "accent").stroke().dotCap(), "Sharp's dots stay round");
        eq(Cap.ROUND, IconLook.of("line", "none", "solid", "accent").stroke().dotCap(), "Line's dots are round anyway");
    }

    private static List<Float> toList(float[] a) {
        Float[] out = new Float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i];
        return List.of(out);
    }
}
