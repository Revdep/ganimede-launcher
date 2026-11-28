package org.ganimede.launcher.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/** Which chat messages in a notification are voice notes the home can play, and the player's clock. */
public final class Voice {
    /**
     * Audio shared through a content provider only: Android hands notification readers access to those, while a file
     * path or a web address would be read with this app's own rights (or need the internet), so they never play.
     */
    public static boolean playable(String mime, String uri) {
        return mime != null && mime.length() > 6 && mime.toLowerCase(Locale.ROOT).startsWith("audio/")
                && uri != null && uri.startsWith("content://");
    }

    /**
     * Indexes of every playable message, in the chat's own order (oldest first). Not by time: a message without one
     * reads as 0 and would jump ahead of older notes.
     */
    public static int[] all(String[] mimes, String[] uris) {
        return IntStream.range(0, mimes.length).filter(i -> playable(mimes[i], uris[i])).toArray();
    }

    /** The file name of a note's kept copy: one per notification and note, hashes only, so no key nor address is on disk. */
    public static String keptName(String key, String uri) {
        return hash(key) + "-" + hash(uri);
    }

    /** The largest note copied, all kept notes together, and the longest a copy may take before it is cut off. */
    public static final long NOTE_BYTES = 25L << 20, KEPT_BYTES = 50L << 20, COPY_MS = 10_000;

    /** The most notifications waiting to be copied at once: a hostile flood must not grow the queue without end. */
    public static final int QUEUE_MAX = 32;

    /** Whether one more note, at its largest, still fits with the kept ones: nothing is ever written past the cap. */
    public static boolean room(long used) {
        return used + NOTE_BYTES <= KEPT_BYTES;
    }

    /**
     * The kept copies to delete: those of notes no longer in any notification, and halves (".part") older than a copy
     * may take, left by a copy cut off or a crash; a younger half is being written now. No wanted set (no notification
     * access): every one, halves too. A copy modified after snapshot (when the notifications were read) is spared: its
     * note may have arrived since, so the set cannot know it. Compared in whole seconds: File.lastModified() is truncated
     * to the second on Android, so a copy made later in the snapshot's own second reads as before it.
     */
    public static List<String> drop(String[] names, long[] modified, Set<String> wanted, long snapshot, long now) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (wanted == null || (names[i].endsWith(".part") ? now - modified[i] > COPY_MS : !wanted.contains(names[i]) && modified[i] / 1000 < snapshot / 1000))
                out.add(names[i]);
        }
        return out;
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) hex.append(Character.forDigit(d[i] >> 4 & 15, 16)).append(Character.forDigit(d[i] & 15, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java and Android has SHA-256
        }
    }

    /** m:ss, in whole seconds. */
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private Voice() {}
}
