package org.ganimede.launcher.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.OptionalInt;

/**
 * Which built-in icon an app gets. Keys are hashed with {@link #fnv48}: the package when every component of it in
 * Arcticons' appfilter draws one icon, else "package/full.ClassName" (tools/icons/pack.py builds and checks them).
 */
public final class IconIndex {
    private final long[] hashes;
    private final char[] ids;

    IconIndex(ByteBuffer data, int at, int count) {
        hashes = new long[count];
        ids = new char[count];
        for (int i = 0; i < count; i++, at += 8) {
            hashes[i] = (data.getInt(at) & 0xFFFFFFFFL) | (long) (data.getShort(at + 4) & 0xFFFF) << 32;
            ids[i] = data.getChar(at + 6);
        }
    }

    /** Low 48 bits of FNV-1a 64 over the UTF-8 bytes. */
    public static long fnv48(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) h = (h ^ (b & 0xFF)) * 0x100000001b3L;
        return h & 0xFFFFFFFFFFFFL;
    }

    /** The icon id for an activity; {@code cls} may be short (".Main"), as in appfilter.xml. */
    public OptionalInt find(String pkg, String cls) {
        int i = Arrays.binarySearch(hashes, fnv48(pkg + "/" + (cls.startsWith(".") ? pkg + cls : cls)));
        if (i < 0) i = Arrays.binarySearch(hashes, fnv48(pkg));
        return i < 0 ? OptionalInt.empty() : OptionalInt.of(ids[i]);
    }
}
