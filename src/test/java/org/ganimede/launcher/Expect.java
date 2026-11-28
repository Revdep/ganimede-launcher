package org.ganimede.launcher;

import java.util.Objects;

/** The one check the tests need: equal values, or a message saying what was expected. */
public final class Expect {
    public static void eq(Object expected, Object actual, String what) {
        if (!Objects.deepEquals(expected, actual))
            throw new AssertionError(what + ": expected " + show(expected) + ", got " + show(actual));
    }

    public static void yes(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    /** Equal within 0.01: float results of the morph maths are never exact. */
    public static void near(float expected, float actual, String what) {
        if (!(Math.abs(expected - actual) < 0.01f))
            throw new AssertionError(what + ": expected " + show(expected) + ", got " + show(actual));
    }

    public interface Work { void run() throws Exception; }

    /** The bytes the current thread allocates while {@code w} runs (HotSpot's count): a test of a memory peak. */
    public static long allocated(Work w) throws Exception {
        com.sun.management.ThreadMXBean m = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = m.getCurrentThreadAllocatedBytes();
        w.run();
        return m.getCurrentThreadAllocatedBytes() - before;
    }

    private static String show(Object o) {
        if (o instanceof int[]) return java.util.Arrays.toString((int[]) o);
        if (o instanceof Object[]) return java.util.Arrays.toString((Object[]) o);
        return String.valueOf(o);
    }

    private Expect() {}
}
