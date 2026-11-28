package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

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

    /** The points of each subpath, split where a move starts one. */
    private static List<float[]> subpaths(PathData p) {
        List<float[]> out = new ArrayList<>();
        int from = 0, at = 0;
        for (Op op : p.ops()) {
            if ((op == Op.MOVE || op == Op.MOVE_CLOSED) && at > from) {
                out.add(Arrays.copyOfRange(p.points(), from, at));
                from = at;
            }
            at += 2 * new int[]{1, 1, 3, 0, 2, 1}[op.ordinal()];
        }
        out.add(Arrays.copyOfRange(p.points(), from, at));
        return out;
    }

    public static void testRoundTripPath() {
        List<PathData> paths = icon("square");
        eq(1, paths.size(), "one SVG path, one decoded path");
        PathData p = paths.get(0);
        eq(new Op[]{Op.MOVE_CLOSED, Op.LINE, Op.LINE, Op.CLOSE}, p.ops(), "M L L Z, a closed outline");
        near(new float[]{4, 4, 44, 4, 44, 44}, p.points(), "M(4,4) L(44,4) L(44,44)");
        yes(p.stroke() && !p.fill() && !p.evenOdd(), "stroked, not filled");
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

    public static void testDashesBecomeSubpaths() {
        List<PathData> paths = icon("dash");
        eq(1, paths.size(), "one dashed line, one path");
        List<float[]> dashes = subpaths(paths.get(0));
        eq(7, dashes.size(), "40 units of \"0 0 4 2\" are 7 dashes; the leading zero dash and zero gap add no dot");
        for (int i = 0; i < 7; i++) near(new float[]{4 + 6 * i, 10, 8 + 6 * i, 10}, dashes.get(i), "dash " + i);
    }

    public static void testDotsAreZeroLengthSubpaths() {
        List<float[]> dots = subpaths(icon("dots").get(0));
        eq(6, dots.size(), "\"0 0 0 2\" along 10 units: a dot every 2, both ends included");
        for (int i = 0; i < 6; i++)
            near(new float[]{4 + 2 * i, 10, 4 + 2 * i, 10}, dots.get(i), "dot " + i + " is a zero-length line, drawn by its round cap");
    }

    public static void testDashOffsetShiftsThePattern() {
        List<float[]> dashes = subpaths(icon("dashoffset").get(0));
        eq(4, dashes.size(), "\"4 2\" from 1 unit in, along 20 units");
        near(new float[]{4, 10, 7, 10}, dashes.get(0), "the first dash has 3 of its 4 units left");
        near(new float[]{9, 10, 13, 10}, dashes.get(1), "then the pattern");
        near(new float[]{21, 10, 24, 10}, dashes.get(3), "the last dash stops at the line's end");
    }

    public static void testDashedCircleStartsAtItsRightmostPoint() {
        PathData p = icon("dashcircle").get(0);
        List<float[]> dashes = subpaths(p);
        eq(1, dashes.size(), "\"2 100\" on a circle of length 62.8: one dash");
        float[] d = dashes.get(0);
        near(new float[]{34, 24}, new float[]{d[0], d[1]}, "SVG starts a circle at (cx + r, cy)");
        near(new float[]{24 + 10 * (float) Math.cos(.2), 24 + 10 * (float) Math.sin(.2)},
                new float[]{d[d.length - 2], d[d.length - 1]}, "and goes clockwise: 2 units is 0.2 rad");
    }

    public static void testDashedShapeKeepsItsFill() {
        List<PathData> paths = icon("dashfill");
        eq(2, paths.size(), "the fill and the dashed stroke are two paths");
        PathData fill = paths.get(0), stroke = paths.get(1);
        yes(fill.fill() && !fill.stroke(), "the fill first, as SVG paints it");
        eq(new Op[]{Op.MOVE_CLOSED, Op.LINE, Op.LINE, Op.CLOSE}, fill.ops(), "the fill keeps the whole outline");
        yes(stroke.stroke() && !stroke.fill(), "then the stroke");
        near(new float[]{4, 4, 8, 4}, subpaths(stroke).get(0), "dashed from the outline's start");
    }

    public static void testRealAssetDecodesAll() {
        IconSet set = load("src/main/assets/icons.bin");
        eq(15459, set.count(), "one icon per SVG in Arcticons icons/white at the pinned commit");
        int paths = 0;
        for (int i = 0; i < set.count(); i++) {
            int n = set.icon(i).size();
            yes(n > 0, "icon " + set.name(i) + " has no path: it would draw nothing");
            paths += n;
        }
        yes(paths > set.count(), "every icon decodes, most into several paths: " + paths);
    }

    public static void testRealAssetCarriesTheArcticonsCommit() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("src/main/assets/icons.bin"));
        StringBuilder hex = new StringBuilder();
        for (int i = 4; i < 24; i++) hex.append(String.format("%02x", bytes[i]));
        eq("da2fa5694ccfea800a9b88f556131a20908e95c1", hex.toString(),
                "the asset says which Arcticons commit it was packed from (README, pack.py)");
    }

    /** Issue 081: the picker searches the names section; a key may carry its pack ("pack|name"). */
    public static void testSearchSubstring() {
        List<String> names = List.of("calendar_31", "calendar_google_31", "okay");
        eq(List.of("calendar_google_31"), IconSet.search(names, "google"), "google: only the Google calendar");
        eq(List.of("calendar_31", "calendar_google_31"), IconSet.search(names, "cal"), "cal: both calendars, best first");
        eq(List.of(), IconSet.search(names, "zzz"), "zzz: nothing");
        eq(names, IconSet.search(names, " "), "an empty search lists every icon, in order");
        eq(List.of("builtin:arcticons|okay"), IconSet.search(List.of("p|calendar_31", "builtin:arcticons|okay"), "okay"),
                "only the name after the pack is searched");
    }

    /** Issue 081: every icon of the real set is offered (no 4000 cap), found by search and by name. */
    public static void testSearchHasNoCap() {
        IconSet set = load("src/main/assets/icons.bin");
        List<String> names = set.names();
        eq(15459, names.size(), "every name the packer wrote (one per SVG), not 4000");
        yes(IconSet.search(names, "okay").contains("okay"), "okay, far past the 4000th name, is found");
        eq("okay", set.name(set.id("okay").getAsInt()), "a picked icon is found again by its name");
        yes(set.id("zzz_not_an_icon").isEmpty(), "a name this version of the set lacks finds no icon");
    }

    /** Issue 081: the set is read in the background when an entry's picked icon comes from it, not on the main thread. */
    public static void testNeededForPickedIcon() {
        yes(IconSet.needed(java.util.Map.of("icon:f:12", "builtin:arcticons|okay", "mode", "classic")), "a folder's icon picked from the set");
        yes(!IconSet.needed(java.util.Map.of("icon:f:12", "com.pack.icons|ic_phone", "icon_pack", "com.pack.icons")),
                "icons from an installed pack only");
        yes(!IconSet.needed(java.util.Map.of("rn:x", "builtin:arcticons|okay")), "only picked icons count");
        yes(!IconSet.needed(java.util.Map.of()), "nothing picked");
    }

    /** Fable c2 P2-2: a deleted folder's picked icon key is the one the set check looks at, so removing it frees the 4 MB set. */
    public static void testDroppedPickKeepsNothingNeeded() {
        java.util.Map<String, Object> prefs = new java.util.HashMap<>(java.util.Map.of("mode", "classic"));
        prefs.put(IconSet.pickedKey("f:12"), "builtin:arcticons|okay");
        yes(IconSet.needed(prefs), "the folder's pick needs the set");
        prefs.remove(IconSet.pickedKey("f:12"));
        yes(!IconSet.needed(prefs), "its key gone, nothing needs the set");
    }

    /** Fable c3 P2-1: an uninstalled app's picked icons are found by its package, nothing else's (not a longer package name). */
    public static void testPickedKeysOfPackage() {
        java.util.Map<String, Object> prefs = new java.util.HashMap<>();
        prefs.put(IconSet.pickedKey("com.a/com.a.Main"), "builtin:arcticons|okay");
        prefs.put(IconSet.pickedKey("com.a/com.a.Other"), "builtin:arcticons|okay");
        prefs.put(IconSet.pickedKey("com.ab/com.ab.Main"), "builtin:arcticons|okay");
        prefs.put(IconSet.pickedKey("f:12"), "builtin:arcticons|okay");
        prefs.put("rn:com.a/com.a.Main", "x");
        eq(java.util.Set.of("icon:com.a/com.a.Main", "icon:com.a/com.a.Other"), new java.util.HashSet<>(IconSet.pickedKeysOf(prefs, "com.a")),
                "both of com.a's entries, not com.ab, a folder or a rename");
    }

    /** Reads everything a set offers, as the launcher does: every icon and name, and an index lookup. */
    private static void readAll(IconSet set) {
        for (int i = 0; i < set.count(); i++) {
            set.icon(i);
            set.name(i);
        }
        set.index().find("com.example", "com.example.Main");
        set.categoryGlyph("com.example.bank", 5);
    }

    /** Only IllegalArgumentException may come out of a damaged set: the caller falls back to the app's own icon. */
    private static void rejectsOrReads(byte[] bytes, String what) {
        try {
            readAll(IconSet.of(bytes));
        } catch (IllegalArgumentException expected) {
            // refused: the caller shows the app's own icon
        } catch (RuntimeException | Error e) {
            throw new AssertionError(what + ": " + e, e);
        }
    }

    public static void testTruncatedSetIsRefused() throws IOException {
        byte[] whole = Files.readAllBytes(Path.of("src/test/resources/icons/icons-fixture.bin"));
        for (int n = 0; n < whole.length; n++) rejectsOrReads(Arrays.copyOf(whole, n), "cut to " + n + " bytes");
    }

    public static void testFlippedByteIsRefusedOrRead() throws IOException {
        flipEveryByte("src/test/resources/icons/icons-fixture.bin");
    }

    public static void testFlippedByteInFormatTwoIsRefusedOrRead() throws IOException {
        flipEveryByte("src/test/resources/icons/icons-fixture2.bin");
    }

    private static void flipEveryByte(String file) throws IOException {
        byte[] whole = Files.readAllBytes(Path.of(file));
        for (int i = 0; i < whole.length; i++) {
            for (int flip : new int[]{0xFF, 0x80, 0x01}) {
                byte[] bad = whole.clone();
                bad[i] ^= (byte) flip;
                rejectsOrReads(bad, "byte " + i + " xor " + flip);
            }
        }
    }

    private static IconSet withCategories() {
        return load("src/test/resources/icons/icons-fixture2.bin");
    }

    /** Issue 087: the app categories of format 2. */
    public static void testCategoryHitAndMiss() {
        IconSet set = withCategories();
        eq(OptionalInt.of(id(set, "arc")), set.categoryGlyph("com.example.bank"), "Finance has the glyph the packer was given");
        eq(OptionalInt.of(id(set, "square")), set.categoryGlyph("com.example.shop"), "the curated table's category");
        eq(OptionalInt.empty(), set.categoryGlyph("com.example.none"), "a package nobody knows");
        eq(OptionalInt.empty(), set.categoryGlyph("pkgA"), "an app with an Arcticons icon never gets a category");
    }

    public static void testFormatOneHasNoCategories() {
        IconSet set = fixture();
        eq(OptionalInt.empty(), set.categoryGlyph("com.example.bank"), "no section, no category");
    }

    public static void testDamagedCategorySectionRefused() throws IOException {
        byte[] whole = Files.readAllBytes(Path.of("src/test/resources/icons/icons-fixture2.bin"));
        for (int field : new int[]{40, 44, 48}) { // key count, index offset, table offset
            byte[] bad = whole.clone();
            java.nio.ByteBuffer.wrap(bad).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(field, bad.length + 1);
            try {
                IconSet.of(bad);
                throw new AssertionError("field at " + field + " past the end of the file was accepted");
            } catch (IllegalArgumentException expected) {
                yes(expected.getMessage().startsWith("damaged icon set"), expected.getMessage());
            }
        }
    }

    public static void testTruncatedCategorySetIsRefused() throws IOException {
        byte[] whole = Files.readAllBytes(Path.of("src/test/resources/icons/icons-fixture2.bin"));
        for (int n = 0; n < whole.length; n++) rejectsOrReads(Arrays.copyOf(whole, n), "cut to " + n + " bytes");
    }

    public static void testRealAssetCategoriesTotal() {
        IconSet set = load("src/main/assets/icons.bin");
        // the table is in the enum's order: what the manifest names reaches the glyph the enum names
        for (int manifest = 0; manifest <= 8; manifest++) {
            AppCategory c = AppCategory.fromManifest(manifest).orElseThrow();
            eq(c.glyph(), set.name(set.categoryGlyph("com.example.unknown", manifest).orElseThrow()), c + ": the glyph the packer chose is the one Java names");
        }
        eq("emoji_classical_building", set.name(set.categoryGlyph("it.bnl.apps.banking").orElseThrow()), "from the curated table");
    }

    /** The category of an app: the tables first (dated, but the curated one is checked), then what the app declares. */
    public static void testManifestCategoryIsTheLastResort() {
        IconSet set = withCategories();
        eq(OptionalInt.of(id(set, "arc")), set.categoryGlyph("com.example.bank", 5), "the table beats the manifest (NEWS)");
        eq(OptionalInt.of(id(set, "square")), set.categoryGlyph("com.example.unknown", 5), "not in the tables: its declared category");
        eq(OptionalInt.empty(), set.categoryGlyph("com.example.unknown", -1), "undefined: none");
        eq(OptionalInt.empty(), fixture().categoryGlyph("com.example.unknown", 5), "format 1 has no glyph table");
    }
}
