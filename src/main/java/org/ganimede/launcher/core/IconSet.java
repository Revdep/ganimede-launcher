package org.ganimede.launcher.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The built-in icon set (assets/icons.bin, packed from Arcticons by tools/icons/pack.py, which documents the format):
 * per icon a list of paths on a 48x48 grid, ready to draw; a name per icon; an index from apps to icons.
 */
public final class IconSet {
    /** The built-in set's id where an icon pack's package name goes (icon_pack, picked icons): a colon can't be in one. */
    public static final String PACK = "builtin:arcticons";

    /** The file's op codes are the ordinals of the first five; MOVE_CLOSED is a MOVE whose subpath closes. */
    public enum Op { MOVE, LINE, CUBIC, CLOSE, OVAL, MOVE_CLOSED }

    /** Stroke ends and corners of a look (IconLook); the names are Android's Paint.Cap and Paint.Join. */
    public enum Cap { ROUND, BUTT, SQUARE }
    public enum Join { ROUND, MITER, BEVEL }

    /**
     * One SVG shape; {@code points} holds x, y pairs in grid units: 1 per move or line, 3 per cubic, 2 per oval (its box).
     * Width, cap and join are the look's (IconLook), not the SVG's.
     */
    public record PathData(boolean stroke, boolean fill, boolean evenOdd, float alpha, Op[] ops, float[] points) {}

    private static final int MAGIC = 'I' | 'C' << 8 | 'N' << 16 | '2' << 24, HEADER = 52, Q = 32;
    private static final Op[] OPS = Op.values();
    private static final int[] POINTS = {1, 1, 3, 0, 2};

    /** How many x, y points an op takes: 1 per move or line, 3 per cubic, 2 per oval (its box), none for close. */
    public static int points(Op op) {
        return op == Op.MOVE_CLOSED ? 1 : POINTS[op.ordinal()];
    }

    private final ByteBuffer data;
    private final int count, start;
    private final int[] nameAt;
    private final IconIndex index;
    private final IconIndex categoryIndex;
    private final List<Integer> categoryGlyphs = new ArrayList<>();

    /** Every count and offset is checked against the bytes here or in {@link Reader}: a damaged file is refused, not read past. */
    private IconSet(ByteBuffer data) {
        this.data = data;
        int length = data.capacity();
        count = data.getInt(24);
        int indexCount = data.getInt(28), names = data.getInt(32), indexAt = data.getInt(36);
        if (count < 0 || count > (length - HEADER) / 4 - 1) throw bad("icon count");
        start = HEADER + 4 * (count + 1);
        for (int i = 0, previous = 0; i <= count; i++) {
            int off = data.getInt(HEADER + 4 * i);
            if (off < previous || off > length - start) throw bad("icon offset " + i);
            previous = off;
        }
        if (indexCount < 0 || indexAt < 0 || indexAt > length || indexCount > (length - indexAt) / 8) throw bad("index");
        if (names < 0 || names > length) throw bad("names");
        nameAt = new int[count];
        Reader r = new Reader(names, length);
        for (int i = 0; i < count; i++) {
            nameAt[i] = r.pos;
            r.skip(r.varint());
        }
        index = new IconIndex(data, indexAt, indexCount);
        int keys = data.getInt(40), categoryAt = data.getInt(44), tableAt = data.getInt(48);
        if (keys < 0 || categoryAt < 0 || categoryAt > length || keys > (length - categoryAt) / 8) throw bad("category index");
        if (tableAt < 0 || tableAt > length - 2) throw bad("category table");
        categoryIndex = new IconIndex(data, categoryAt, keys);
        int n = data.getShort(tableAt) & 0xFFFF;
        if (tableAt + 2 + 2 * n > length) throw bad("category glyphs");
        for (int i = 0; i < n; i++) {
            int glyph = data.getShort(tableAt + 2 + 2 * i) & 0xFFFF;
            if (glyph >= count) throw bad("category glyph " + i);
            categoryGlyphs.add(glyph);
        }
    }

    private static IllegalArgumentException bad(String what) {
        return new IllegalArgumentException("damaged icon set: " + what);
    }

    /** @throws IllegalArgumentException when the bytes are not an icon set (ICN2), or a damaged one */
    public static IconSet of(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < HEADER || b.getInt(0) != MAGIC) throw new IllegalArgumentException("not an ICN2 icon set");
        return new IconSet(b);
    }

    public int count() {
        return count;
    }

    public String name(int id) {
        Reader r = new Reader(nameAt[id], data.capacity());
        int length = r.varint();
        return new String(data.array(), r.pos, length, StandardCharsets.UTF_8); // the constructor checked the length
    }

    /** Every icon's name, in id order (the packer sorts them). */
    public List<String> names() {
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(name(i));
        return out;
    }

    /**
     * The id of the icon with this name (a binary search: the packer sorts the names); picked icons are stored by name,
     * as ids change between versions of the set.
     */
    public OptionalInt id(String name) {
        int lo = 0, hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1, c = name(mid).compareTo(name);
            if (c == 0) return OptionalInt.of(mid);
            if (c < 0) lo = mid + 1; else hi = mid - 1;
        }
        return OptionalInt.empty();
    }

    /**
     * The icon picker's search over keys that are names or "pack|name": the name matches as an app name does
     * ({@link Search#rank}), best first; a blank query keeps every key, in order.
     */
    public static List<String> search(List<String> keys, String query) {
        return query.isBlank() ? keys : Search.rank(keys, k -> k.substring(k.indexOf('|') + 1), k -> 0, query);
    }

    /** The preference name of an entry's picked icon: delete it with the entry, or the set stays needed (and in the backup). */
    public static String pickedKey(String entry) {
        return "icon:" + entry;
    }

    /**
     * The picked-icon keys of an app's entries in the main profile ("icon:<package>/<class>", no "#<user>" of a work
     * profile's copy): deleted when it is uninstalled (see {@link #pickedKey}).
     */
    public static List<String> pickedKeysOf(Map<String, ?> prefs, String pkg) {
        List<String> out = new ArrayList<>();
        for (String k : prefs.keySet()) if (k.startsWith(pickedKey(pkg + "/")) && k.indexOf('#') < 0) out.add(k);
        return out;
    }

    /** Whether any entry's picked icon ("icon:<entry>" = "builtin:arcticons|name") comes from this set. */
    public static boolean needed(Map<String, ?> prefs) {
        for (Map.Entry<String, ?> e : prefs.entrySet())
            if (e.getKey().startsWith(pickedKey("")) && e.getValue() instanceof String v && v.startsWith(PACK + "|")) return true;
        return false;
    }

    /** The built-in icon (id) that stands for the Play category of an app without an icon in the set; empty when unknown. */
    public OptionalInt categoryGlyph(String pkg) {
        OptionalInt category = categoryIndex.find(pkg);
        return category.isPresent() && category.getAsInt() < categoryGlyphs.size()
                ? OptionalInt.of(categoryGlyphs.get(category.getAsInt())) : OptionalInt.empty();
    }

    /** {@link #categoryGlyph(String)}, else the glyph of the category the app declares in its manifest ({@code ApplicationInfo.category}). */
    public OptionalInt categoryGlyph(String pkg, int manifestCategory) {
        OptionalInt known = categoryGlyph(pkg);
        if (known.isPresent() || categoryGlyphs.isEmpty()) return known;
        return AppCategory.fromManifest(manifestCategory).filter(c -> c.ordinal() < categoryGlyphs.size()).map(c -> OptionalInt.of(categoryGlyphs.get(c.ordinal()))).orElse(OptionalInt.empty());
    }

    public IconIndex index() {
        return index;
    }

    /** @throws IllegalArgumentException when the icon's record is damaged */
    public List<PathData> icon(int id) {
        if (id < 0 || id >= count) throw bad("icon id " + id); // an id from a damaged index
        Reader r = new Reader(start + data.getInt(HEADER + 4 * id), start + data.getInt(HEADER + 4 * (id + 1)));
        List<PathData> paths = new ArrayList<>();
        int x = 0, y = 0;
        for (int n = r.count(); n > 0; n--) {
            int style = r.u8();
            float alpha = r.u8() / 255f;
            Op[] ops = new Op[r.count()];
            int[] q = new int[(int) Math.min(6L * ops.length, r.end - r.pos)]; // a point takes a byte at least: no more than the bytes left
            int used = 0, move = -1, first = 0;
            boolean z = false;
            for (int o = 0; o < ops.length; o++) {
                int code = r.u8();
                if (code >= POINTS.length) throw bad("op " + code);
                Op op = ops[o] = OPS[code];
                if (op == Op.MOVE || op == Op.OVAL) {
                    if (move >= 0 && closes(q, first, used, z)) ops[move] = Op.MOVE_CLOSED;
                    move = op == Op.MOVE ? o : -1;
                    first = used;
                    z = false;
                }
                z |= op == Op.CLOSE;
                for (int j = POINTS[op.ordinal()]; j > 0; j--) {
                    q[used++] = x += r.zigzag();
                    q[used++] = y += r.zigzag();
                }
            }
            if (move >= 0 && closes(q, first, used, z)) ops[move] = Op.MOVE_CLOSED;
            float[] points = new float[used];
            for (int i = 0; i < used; i++) points[i] = q[i] / (float) Q;
            paths.add(new PathData((style & 1) != 0, (style & 2) != 0, (style & 4) != 0, alpha, ops, points));
        }
        return paths;
    }

    /**
     * A subpath (its points q[first..used)) is closed when it has a Z or ends on its start point: issue 080 fills only
     * closed shapes. Exact on the stored 1/32 grid, so "within 1/32" of the SVG.
     */
    private static boolean closes(int[] q, int first, int used, boolean z) {
        return z || used > first + 2 && q[first] == q[used - 2] && q[first + 1] == q[used - 1];
    }

    /** Bytes, LEB128 varints and zigzag deltas, read forward from {@code pos}, never at or past {@code end}. */
    private final class Reader {
        int pos;
        final int end;

        Reader(int pos, int end) {
            if (pos < 0 || pos > end || end > data.capacity()) throw bad("range");
            this.pos = pos;
            this.end = end;
        }

        int u8() {
            if (pos >= end) throw bad("record ends early");
            return data.get(pos++) & 0xFF;
        }

        int varint() {
            int v = 0;
            for (int shift = 0; shift < 32; shift += 7) {
                int b = u8();
                v |= (b & 0x7F) << shift;
                if (b < 0x80) return v;
            }
            throw bad("varint");
        }

        /** A count of items of at least one byte each: never more than the bytes left, so no huge array. */
        int count() {
            int n = varint();
            if (n < 0 || n > end - pos) throw bad("count");
            return n;
        }

        void skip(int n) {
            if (n < 0 || n > end - pos) throw bad("length");
            pos += n;
        }

        int zigzag() {
            int v = varint();
            return (v >>> 1) ^ -(v & 1);
        }
    }
}
