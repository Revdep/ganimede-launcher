package org.ganimede.launcher.core;

import java.util.Calendar;

/** When the notification summary comes: rolling six-hour batches from the last one. */
public final class Summary {
    /** From the last summary add 6 hours, or 7 if it came at minute 30 or later, then drop minutes and seconds. */
    public static long next(long last) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(last);
        c.add(Calendar.HOUR_OF_DAY, c.get(Calendar.MINUTE) >= 30 ? 7 : 6);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Within a minute of the next summary: what arrives now goes out with it, not into the batch after. */
    public static boolean isDue(long last, long now) {
        return last > 0 && Math.abs(now - next(last)) < 60_000L;
    }

    /** The latest summary time at or before now (now itself for the very first one). */
    public static long roll(long last, long now) {
        if (last == 0) return now;
        for (long n = next(last); n <= now; n = next(last)) last = n;
        return last;
    }

    /** When a notification held back now comes back: the summary after the latest one at or before now. */
    public static long upcoming(long last, long now) {
        return next(roll(last, now));
    }

    /** The settings: summary on, this app chosen, chats left alone, silent ones included. */
    public record Rules(boolean on, boolean chosen, boolean skipChats, boolean silent) {}

    /** What the notification is: flags and category reduced to yes/no. */
    public record Note(boolean clearable, boolean ongoing, boolean urgentCategory, boolean fullScreen, boolean chat, boolean quiet) {}

    /** Whether a new notification is held back for the summary. */
    public static boolean waits(Rules r, Note n) {
        return r.on() && r.chosen() && n.clearable() && !n.ongoing() && !n.urgentCategory() && !n.fullScreen()
                && !(r.skipChats() && n.chat()) && !(!r.silent() && n.quiet());
    }

    /** A snoozed notification posted again (Android does it a few seconds early): this is the summary, let it through. */
    public static boolean cameBack(Long back, long now) {
        return back != null && now >= back - 5_000L;
    }

    /** The due time of a stored "key|due" entry, or null when it is malformed (the key may contain '|'). */
    private static Long dueOf(String e) {
        int i = e.lastIndexOf('|');
        if (i < 0) return null;
        try {
            return Long.valueOf(e.substring(i + 1));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** When key was held until, or null. */
    public static Long due(java.util.Set<String> stored, String key) {
        for (String e : stored)
            if (dueOf(e) != null && e.substring(0, e.lastIndexOf('|')).equals(key)) return dueOf(e);
        return null;
    }

    /** The stored entries that are well formed and came back less than an hour ago. */
    public static java.util.Set<String> expire(java.util.Set<String> stored, long now) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String e : stored) {
            Long d = dueOf(e);
            if (d != null && d >= now - 3_600_000L) out.add(e);
        }
        return out;
    }

    /** The stored set with key held until due (and the old entries gone). */
    public static java.util.Set<String> hold(java.util.Set<String> stored, String key, long due, long now) {
        java.util.Set<String> out = expire(stored, now);
        out.removeIf(e -> e.substring(0, e.lastIndexOf('|')).equals(key));
        out.add(key + "|" + due);
        return out;
    }

    /** The hold count after key is held: one more unless key was held already until the same due (a re-post inside the window). */
    public static int counted(int count, java.util.Set<String> stored, String key, long due) {
        Long held = due(stored, key);
        return held != null && held == due ? count : count + 1;
    }

    private Summary() {}
}
