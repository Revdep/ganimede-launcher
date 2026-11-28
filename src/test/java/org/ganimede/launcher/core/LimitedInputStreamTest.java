package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.allocated;
import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class LimitedInputStreamTest {
    private static LimitedInputStream stream(long limit) {
        return new LimitedInputStream(new ByteArrayInputStream(new byte[100]), limit);
    }

    public static void testReadStopsAtTheCap() throws IOException {
        LimitedInputStream in = stream(5);
        eq(3, in.read(new byte[3], 0, 3), "first block");
        eq(2, in.read(new byte[3], 0, 3), "the block is cut at the cap");
        eq(-1, in.read(), "single reads end at the cap");
        eq(-1, in.read(new byte[3], 0, 3), "block reads end at the cap");
    }

    public static void testSkipSharesTheSameBudget() throws IOException {
        LimitedInputStream in = stream(5);
        eq(3L, in.skip(3), "skip within the cap");
        eq(2L, in.skip(1_000_000), "skip is cut at the cap");
        eq(0L, in.skip(1), "nothing more can be skipped");
        eq(-1, in.read(), "and nothing more can be read");
    }

    public static void testReadAllTakesEveryByteUpToTheCap() throws IOException {
        eq(100, LimitedInputStream.readAll(new ByteArrayInputStream(new byte[100]), 100).length, "exactly the cap");
        eq(0, LimitedInputStream.readAll(new ByteArrayInputStream(new byte[0]), 100).length, "an empty stream");
    }

    public static void testReadAllRefusesMoreThanTheCap() {
        try {
            LimitedInputStream.readAll(new ByteArrayInputStream(new byte[101]), 100);
            throw new AssertionError("101 bytes with a cap of 100 must be refused");
        } catch (IOException expected) {
            // too big: the caller gives up instead of reading on
        }
    }

    public static void testReadAllOfAKnownLengthCopiesOnce() throws Exception {
        // an asset tells its length (available()): the 4 MB built-in set is read into one array of that size
        byte[] data = new byte[4 << 20];
        data[data.length - 1] = 7;
        byte[][] out = new byte[1][];
        long bytes = allocated(() -> out[0] = LimitedInputStream.readAll(new ByteArrayInputStream(data), 16 << 20));
        eq(data.length, out[0].length, "every byte");
        eq((byte) 7, out[0][data.length - 1], "the last byte");
        yes(bytes < data.length * 3L / 2, "reading " + data.length + " B allocated " + bytes + " B");
    }

    public static void testReadAllOfAnUnknownLengthGrows() throws IOException {
        // a stream that tells nothing (available() 0) and hands out a few bytes at a time
        byte[] data = new byte[20_000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        java.io.InputStream trickle = new ByteArrayInputStream(data) {
            @Override public int available() { return 0; }
            @Override public synchronized int read(byte[] b, int off, int len) { return super.read(b, off, Math.min(len, 777)); }
        };
        eq(data, LimitedInputStream.readAll(trickle, 20_000), "every byte, in order, up to exactly the cap");
    }
}
