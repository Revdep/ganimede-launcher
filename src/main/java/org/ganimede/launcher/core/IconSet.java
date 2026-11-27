package org.ganimede.launcher.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The built-in icon set (assets/icons.bin, packed from Arcticons by tools/icons/pack.py, which documents the format):
 * per icon a list of paths on a 48x48 grid, ready to draw; a name per icon; an index from apps to icons.
 */
public final class IconSet {
    /** The file's op codes are the ordinals of the first five; MOVE_CLOSED is a MOVE whose subpath closes. */
    public enum Op { MOVE, LINE, CUBIC, CLOSE, OVAL, MOVE_CLOSED }

    /** Stroke ends and corners as the SVG declares them; the names are Android's Paint.Cap and Paint.Join. */
    public enum Cap { ROUND, BUTT, SQUARE }
    public enum Join { ROUND, MITER, BEVEL }

    /** One SVG shape; {@code points} holds x, y pairs in grid units: 1 per move or line, 3 per cubic, 2 per oval (its box). */
    public record PathData(boolean stroke, boolean fill, boolean evenOdd, Cap cap, Join join, float width, float alpha,
                           Op[] ops, float[] points) {}

    private static final int MAGIC = 'I' | 'C' << 8 | 'N' << 16 | '1' << 24, HEADER = 40, Q = 32;
    private static final Op[] OPS = Op.values();
    private static final Cap[] CAPS = Cap.values();
    private static final Join[] JOINS = Join.values();
    private static final int[] POINTS = {1, 1, 3, 0, 2};

    private final ByteBuffer data;
    private final int count, start;
    private final int[] nameAt;
    private final IconIndex index;

    private IconSet(ByteBuffer data) {
        this.data = data;
        count = data.getInt(24);
        start = HEADER + 4 * (count + 1);
        nameAt = new int[count];
        Reader r = new Reader(data.getInt(32));
        for (int i = 0; i < count; i++) {
            nameAt[i] = r.pos;
            int length = r.varint(); // read before adding: pos += varint() would add to the old pos
            r.pos += length;
        }
        index = new IconIndex(data, data.getInt(36), data.getInt(28));
    }

    /** @throws IllegalArgumentException when the bytes are not a format-1 icon set */
    public static IconSet of(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < HEADER || b.getInt(0) != MAGIC) throw new IllegalArgumentException("not an ICN1 icon set");
        return new IconSet(b);
    }

    /** The Arcticons commit the set was packed from, in hex. */
    public String version() {
        StringBuilder s = new StringBuilder(40);
        for (int i = 4; i < 24; i++) s.append(String.format("%02x", data.get(i)));
        return s.toString();
    }

    public int count() {
        return count;
    }

    public String name(int id) {
        Reader r = new Reader(nameAt[id]);
        int length = r.varint();
        return new String(data.array(), r.pos, length, StandardCharsets.UTF_8);
    }

    public IconIndex index() {
        return index;
    }

    public List<PathData> icon(int id) {
        Reader r = new Reader(start + data.getInt(HEADER + 4 * id));
        List<PathData> paths = new ArrayList<>();
        int x = 0, y = 0;
        for (int n = r.varint(); n > 0; n--) {
            int style = data.get(r.pos) & 0xFF;
            float width = (data.get(r.pos + 1) & 0xFF) / 16f, alpha = (data.get(r.pos + 2) & 0xFF) / 255f;
            r.pos += 3;
            Op[] ops = new Op[r.varint()];
            int[] q = new int[ops.length * 6];
            int used = 0, move = -1, first = 0;
            boolean z = false;
            for (int o = 0; o < ops.length; o++) {
                Op op = ops[o] = OPS[data.get(r.pos++)];
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
            paths.add(new PathData((style & 1) != 0, (style & 2) != 0, (style & 4) != 0, CAPS[style >> 3 & 3],
                    JOINS[style >> 5 & 3], width, alpha, ops, points));
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

    /** LEB128 varints and zigzag deltas, read forward from {@code pos}. */
    private final class Reader {
        int pos;

        Reader(int pos) {
            this.pos = pos;
        }

        int varint() {
            int v = 0;
            for (int shift = 0; ; shift += 7) {
                int b = data.get(pos++) & 0xFF;
                v |= (b & 0x7F) << shift;
                if (b < 0x80) return v;
            }
        }

        int zigzag() {
            int v = varint();
            return (v >>> 1) ^ -(v & 1);
        }
    }
}
