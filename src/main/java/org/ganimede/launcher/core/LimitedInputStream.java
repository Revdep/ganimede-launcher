package org.ganimede.launcher.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** A provider cannot make a decoder read or skip an unbounded stream: every byte counts against one budget. */
public final class LimitedInputStream extends FilterInputStream {
    private long remaining;

    public LimitedInputStream(InputStream in, long limit) { super(in); remaining = limit; }

    /**
     * Every byte of the stream; a stream longer than max is refused, not read on. Read into an array of the length the
     * stream tells (an asset's whole length): one copy, no growing buffer.
     */
    public static byte[] readAll(InputStream in, int max) throws IOException {
        byte[] b = new byte[Math.max(1, Math.min(in.available(), max))];
        int n = 0;
        while (true) {
            if (n == b.length) { // full: the end, or more than the stream told
                int next = in.read();
                if (next < 0) return b;
                if (n >= max) throw new IOException("more than " + max + " bytes");
                b = java.util.Arrays.copyOf(b, (int) Math.min(2L * b.length, max));
                b[n++] = (byte) next;
            }
            int r = in.read(b, n, b.length - n);
            if (r < 0) return java.util.Arrays.copyOf(b, n);
            n += r;
        }
    }

    @Override public int read() throws IOException {
        if (remaining == 0) return -1;
        int value = super.read();
        if (value >= 0) remaining--;
        return value;
    }

    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        if (remaining == 0) return -1;
        int read = super.read(bytes, offset, (int) Math.min(length, remaining));
        if (read > 0) remaining -= read;
        return read;
    }

    @Override public long skip(long count) throws IOException {
        if (remaining == 0 || count <= 0) return 0;
        long skipped = super.skip(Math.min(count, remaining));
        if (skipped > 0) remaining -= skipped;
        return skipped;
    }
}
