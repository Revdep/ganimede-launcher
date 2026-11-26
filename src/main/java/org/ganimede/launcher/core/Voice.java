package org.ganimede.launcher.core;

import java.util.Locale;
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

    /** m:ss, in whole seconds. */
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private Voice() {}
}
