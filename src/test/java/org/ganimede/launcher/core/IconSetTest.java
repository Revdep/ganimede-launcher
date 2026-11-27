package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.ganimede.launcher.core.IconSet.Cap;
import org.ganimede.launcher.core.IconSet.Join;
import org.ganimede.launcher.core.IconSet.Op;
import org.ganimede.launcher.core.IconSet.PathData;

/** The packer (tools/icons/pack.py) and the decoder agree; the fixture is packed from the SVGs next to it. */
public class IconSetTest {
    private static final float Q = 1 / 32f;

    static IconSet load(String file) {
        try {
            return IconSet.of(Files.readAllBytes(Path.of(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static IconSet fixture() {
        return load("src/test/resources/icons/icons-fixture.bin");
    }

    static int id(IconSet set, String name) {
        for (int i = 0; i < set.count(); i++) if (set.name(i).equals(name)) return i;
        throw new AssertionError("no icon " + name);
    }

    private static List<PathData> icon(String name) {
        IconSet set = fixture();
        return set.icon(id(set, name));
    }

    private static void near(float[] expected, float[] actual, String what) {
        eq(expected.length, actual.length, what + ": number of coordinates");
        for (int i = 0; i < expected.length; i++)
            yes(Math.abs(expected[i] - actual[i]) <= Q, what + ": coordinate " + i + " expected " + expected[i] + ", got " + actual[i]);
    }

    public static void testRoundTripPath() {
        List<PathData> paths = icon("square");
        eq(1, paths.size(), "one SVG path, one decoded path");
        PathData p = paths.get(0);
        eq(new Op[]{Op.MOVE_CLOSED, Op.LINE, Op.LINE, Op.CLOSE}, p.ops(), "M L L Z, a closed outline");
        near(new float[]{4, 4, 44, 4, 44, 44}, p.points(), "M(4,4) L(44,4) L(44,44)");
        yes(p.stroke() && !p.fill() && !p.evenOdd(), "stroked, not filled");
        eq(1f, p.width(), "stroke width 1");
        eq(1f, p.alpha(), "opaque");
    }

    public static void testQuantisationWithinOneThirtySecond() {
        float x = icon("quantised").get(0).points()[0];
        yes(Math.abs(x - 10.01f) <= Q, "10.01 comes back within 1/32, got " + x);
    }

    public static void testTransformFlattened() {
        PathData p = icon("transform").get(0);
        eq(new Op[]{Op.MOVE, Op.LINE}, p.ops(), "an open line");
        near(new float[]{4, 5, 12, 5}, p.points(), "translate(2,3) scale(2) applied to M1 1 L5 1");
        eq(2f, p.width(), "scale(2) doubles the stroke too");
    }

    public static void testArcBecomesCubicsWithSameEnds() {
        PathData p = icon("arc").get(0);
        Op[] ops = p.ops();
        float[] pts = p.points();
        eq(Op.MOVE, ops[0], "starts with a move");
        yes(ops.length >= 2, "at least one curve");
        for (int i = 1; i < ops.length; i++) eq(Op.CUBIC, ops[i], "the arc is only cubics, op " + i);
        near(new float[]{10, 24}, new float[]{pts[0], pts[1]}, "first part starts at (10,24)");
        near(new float[]{38, 24}, new float[]{pts[pts.length - 2], pts[pts.length - 1]}, "last cubic ends at (38,24)");
    }

    public static void testOvalAndEvenOdd() {
        PathData p = icon("oval").get(0);
        eq(new Op[]{Op.OVAL}, p.ops(), "a circle stays an oval");
        near(new float[]{14, 14, 34, 34}, p.points(), "cx 24 cy 24 r 10 is the box (14,14)-(34,34)");
        yes(p.fill() && !p.stroke(), "filled");
        yes(p.evenOdd(), "fill-rule evenodd is kept");
    }

    public static void testClosedFlag() {
        List<PathData> paths = icon("closed");
        eq(Op.MOVE_CLOSED, paths.get(0).ops()[0], "ended by Z: closed");
        eq(Op.MOVE, paths.get(1).ops()[0], "ends away from its start: open");
        eq(Op.MOVE_CLOSED, paths.get(2).ops()[0], "ends where it started (within 1/32): closed");
    }

    public static void testClassRulesOverridePresentationAttributes() {
        List<PathData> paths = icon("classwins");
        eq(1, paths.size(), "one path");
        yes(paths.get(0).stroke() && !paths.get(0).fill(), "fill=\"#fff\" loses to the class rule fill:none, as in CSS");
    }

    public static void testGroupOpacityMultiplies() {
        float alpha = icon("opacity").get(0).alpha();
        yes(Math.abs(alpha - 0.25f) <= 1 / 255f, "opacity .5 inside a group at .5 is .25, got " + alpha);
    }

    public static void testFillAndStrokeKeepTheirOwnOpacity() {
        List<PathData> paths = icon("twoalpha");
        eq(2, paths.size(), "one shape with two opacities becomes two paths");
        PathData fill = paths.get(0), stroke = paths.get(1);
        yes(fill.fill() && !fill.stroke(), "the fill first, as SVG paints it");
        yes(Math.abs(fill.alpha() - 0.5f) <= 1 / 255f, "fill-opacity .5, got " + fill.alpha());
        yes(stroke.stroke() && !stroke.fill(), "then the stroke");
        eq(1f, stroke.alpha(), "the stroke stays opaque");
        eq(fill.ops(), stroke.ops(), "same outline");
    }

    public static void testCapsAndJoinsAsDeclared() {
        List<PathData> paths = icon("caps");
        eq(Cap.BUTT, paths.get(0).cap(), "no stroke-linecap declared: SVG's default butt");
        eq(Join.MITER, paths.get(0).join(), "no stroke-linejoin declared: SVG's default miter");
        eq(Cap.ROUND, paths.get(1).cap(), "round declared");
        eq(Join.ROUND, paths.get(1).join(), "round declared");
        eq(Cap.SQUARE, paths.get(2).cap(), "square declared");
        eq(Join.BEVEL, paths.get(2).join(), "bevel declared");
    }

    public static void testRealAssetDecodesAll() {
        IconSet set = load("src/main/assets/icons.bin");
        eq(15459, set.count(), "one icon per SVG in Arcticons icons/white at the pinned commit");
        int paths = 0;
        for (int i = 0; i < set.count(); i++) paths += set.icon(i).size();
        yes(paths > set.count(), "every icon decodes, most into several paths: " + paths);
    }

    public static void testRealAssetCarriesTheArcticonsCommit() {
        eq("da2fa5694ccfea800a9b88f556131a20908e95c1", load("src/main/assets/icons.bin").version(),
                "the asset says which Arcticons commit it was packed from (README, pack.py)");
    }
}
