package org.ganimede.launcher.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** The kept copies to delete: those of notes no longer in any notification, then the oldest until the rest fit in cap bytes. */
    public static List<String> drop(String[] names, long[] sizes, long[] times, Set<String> wanted, long cap) {
        boolean[] gone = new boolean[names.length];
        long total = 0;
        for (int i : IntStream.range(0, names.length).boxed().sorted(Comparator.comparingLong(i -> -times[i])).mapToInt(i -> i).toArray()) {
            gone[i] = !wanted.contains(names[i]) || (total += sizes[i]) > cap;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.length; i++) if (gone[i]) out.add(names[i]);
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
