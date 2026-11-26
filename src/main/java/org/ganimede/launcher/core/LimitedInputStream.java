package org.ganimede.launcher.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** A provider cannot make a decoder read or skip an unbounded stream: every byte counts against one budget. */
public final class LimitedInputStream extends FilterInputStream {
    private long remaining;

    public LimitedInputStream(InputStream in, long limit) { super(in); remaining = limit; }

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
