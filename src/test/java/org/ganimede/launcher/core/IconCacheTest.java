package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

/** Fable c3 P2-2: the drawn-icon cache follows the app's memory class, not a fixed 24 MB. */
public class IconCacheTest {
    private static final int MB = 1024 * 1024;

    public static void testQuarterOfTheMemoryClass() {
        eq(64 * MB, IconCache.bytes(256), "a 256 MB class: 64 MB, room for a big drawer at 80 dp on 3.5x (313 KB an icon)");
    }

    public static void testFloorAndCeiling() {
        eq(24 * MB, IconCache.bytes(48), "a small class keeps today's 24 MB");
        eq(96 * MB, IconCache.bytes(1024), "a huge class stops at 96 MB");
    }
}
