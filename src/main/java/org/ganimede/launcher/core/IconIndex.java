package org.ganimede.launcher.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.OptionalInt;

/**
 * Which built-in icon an app gets. Keys are hashed with {@link #fnv48}: "package/full.ClassName" of each component
 * Arcticons' appfilter lists, and no more (tools/icons/pack.py builds and checks them).
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

    /** The icon id for a listed activity; {@code cls} is the full class name, as ComponentName.getClassName gives it. */
    public OptionalInt find(String pkg, String cls) {
        return find(pkg + "/" + cls);
    }

    /** The id stored under exactly this key (the category index's keys are plain packages). */
    public OptionalInt find(String key) {
        int i = Arrays.binarySearch(hashes, fnv48(key));
        return i < 0 ? OptionalInt.empty() : OptionalInt.of(ids[i]);
    }
}
