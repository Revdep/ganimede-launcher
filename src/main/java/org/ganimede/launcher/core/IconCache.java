package org.ganimede.launcher.core;

/** How much memory the drawn-icon cache (Icons) may take. */
public final class IconCache {
    private static final int MB = 1024 * 1024;

    /** A quarter of the app's memory class (MB, ActivityManager.getMemoryClass), from 24 MB to 96 MB. */
    public static int bytes(int memoryClassMb) {
        return Math.max(24, Math.min(96, memoryClassMb / 4)) * MB;
    }

    private IconCache() {}
}
