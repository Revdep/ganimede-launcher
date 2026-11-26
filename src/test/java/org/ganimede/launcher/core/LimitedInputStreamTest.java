package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

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
}
