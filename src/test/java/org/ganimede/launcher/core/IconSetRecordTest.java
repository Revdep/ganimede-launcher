package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.allocated;
import static org.ganimede.launcher.Expect.yes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A damaged icon record, built byte by byte: refused without a memory peak out of proportion to its size. */
public class IconSetRecordTest {
    /** An ICN1 set of one icon whose one path claims {@code ops} ops, each a LINE with no points: it ends early. */
    private static byte[] claimingOps(int ops) {
        ByteBuffer rec = ByteBuffer.allocate(16 + ops);
        rec.put((byte) 1).put((byte) 1).put((byte) 16).put((byte) 255); // one path: stroke, width 1, opaque
        for (int v = ops; ; v >>>= 7) { // the op count, LEB128
            if (v < 0x80) { rec.put((byte) v); break; }
            rec.put((byte) (v & 0x7F | 0x80));
        }
        for (int i = 0; i < ops; i++) rec.put((byte) 1);
        int len = rec.position(), start = 48, names = start + len;
        ByteBuffer b = ByteBuffer.allocate(names + 2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 'I' | 'C' << 8 | 'N' << 16 | '1' << 24).putInt(24, 1).putInt(28, 0).putInt(32, names).putInt(36, names + 2);
        b.putInt(40, 0).putInt(44, len);
        b.put(start, rec.array(), 0, len);
        b.put(names, (byte) 1).put(names + 1, (byte) 'a');
        return b.array();
    }

    public static void testDamagedOpCountNeedsNoHugeArray() throws Exception {
        int ops = 1 << 20;
        IconSet set = IconSet.of(claimingOps(ops));
        long bytes = allocated(() -> {
            try {
                set.icon(0);
                throw new AssertionError("a record that ends early must be refused");
            } catch (IllegalArgumentException damaged) {
                // refused, as it should be
            }
        });
        // the ops array and one int per byte left are needed; six ints per claimed op (24 B each) are not
        yes(bytes < 12L * ops, "decoding a damaged " + ops + " B record allocated " + bytes + " B");
    }
}
