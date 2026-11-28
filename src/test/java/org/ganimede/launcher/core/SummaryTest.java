package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.Calendar;

public class SummaryTest {
    private static long at(int day, int h, int m) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.JANUARY, day, h, m, 0);
        return c.getTimeInMillis();
    }

    public static void testNextIsSixHoursOnOrSevenFromMinuteThirty() {
        eq(at(5, 15, 0), Summary.next(at(5, 9, 10)), "9:10 → 15:00");
        eq(at(5, 16, 0), Summary.next(at(5, 9, 45)), "9:45 → 16:00");
    }

    public static void testSixHoursAreSixElapsedHoursAcrossDst() {
        java.util.TimeZone was = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Rome"));
            for (int[] day : new int[][]{{Calendar.MARCH, 28}, {Calendar.OCTOBER, 24}}) { // clocks change the night after
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(2026, day[0], day[1], 21, 0, 0);
                long last = c.getTimeInMillis();
                eq(last + 6 * 3_600_000L, Summary.next(last), "a batch every six real hours, month " + day[0]);
            }
        } finally {
            java.util.TimeZone.setDefault(was);
        }
    }

    public static void testDueWithinAMinuteOfTheNextSummary() {
        yes(Summary.isDue(at(5, 9, 0), at(5, 15, 0) - 59_000), "just before");
        yes(Summary.isDue(at(5, 9, 0), at(5, 15, 0) + 59_000), "just after");
        yes(!Summary.isDue(at(5, 9, 0), at(5, 15, 1)), "a minute later");
        yes(!Summary.isDue(0, at(5, 15, 0)), "never had one");
    }

    public static void testRollMovesTheLastSummaryUpToNow() {
        eq(at(5, 9, 0), Summary.roll(0, at(5, 9, 0)), "the first one starts now");
        eq(at(5, 9, 0), Summary.roll(at(5, 9, 0), at(5, 14, 59)), "the next one isn't due yet");
        eq(at(5, 21, 0), Summary.roll(at(5, 9, 0), at(5, 22, 0)), "two summaries passed meanwhile");
    }

    public static void testUpcomingIsWhenAHeldBackNotificationComesBack() {
        eq(at(5, 15, 0), Summary.upcoming(at(5, 9, 0), at(5, 10, 0)), "the next one after the last");
        eq(at(5, 18, 0), Summary.upcoming(at(5, 0, 0), at(5, 14, 0)), "two summaries passed meanwhile");
        eq(at(5, 20, 0), Summary.upcoming(0, at(5, 14, 10)), "the first one starts now");
    }

    // Rules: on, chosen app, skip chats, include silent. Note: clearable, ongoing, urgent category, full screen, chat, quiet.
    private static final Summary.Rules ON = new Summary.Rules(true, true, true, false);
    private static final Summary.Note PLAIN = new Summary.Note(true, false, false, false, false, false);

    public static void testUrgentOngoingAndFullScreenNeverWait() {
        yes(Summary.waits(ON, PLAIN), "a plain one waits");
        yes(!Summary.waits(ON, new Summary.Note(false, false, false, false, false, false)), "not clearable");
        yes(!Summary.waits(ON, new Summary.Note(true, true, false, false, false, false)), "ongoing or foreground service");
        yes(!Summary.waits(ON, new Summary.Note(true, false, true, false, false, false)), "call, alarm, navigation...");
        yes(!Summary.waits(ON, new Summary.Note(true, false, false, true, false, false)), "full screen intent");
    }

    public static void testChatsWaitOnlyWhenNotSkipped() {
        Summary.Note chat = new Summary.Note(true, false, false, false, true, false);
        yes(!Summary.waits(ON, chat), "chats skipped by default");
        yes(Summary.waits(new Summary.Rules(true, true, false, false), chat), "chats allowed");
    }

    public static void testSilentOnesWaitOnlyWhenAsked() {
        Summary.Note quiet = new Summary.Note(true, false, false, false, false, true);
        yes(!Summary.waits(ON, quiet), "silent ones already don't disturb");
        yes(Summary.waits(new Summary.Rules(true, true, true, true), quiet), "silent asked for");
    }

    public static void testOnlyChosenAppsAndOnlyWhileOn() {
        yes(!Summary.waits(new Summary.Rules(false, true, true, false), PLAIN), "summary off");
        yes(!Summary.waits(new Summary.Rules(true, false, true, false), PLAIN), "app not chosen");
    }

    public static void testASnoozedOneComingBackGoesThrough() {
        yes(!Summary.cameBack(null, 1_000_000), "never snoozed");
        yes(!Summary.cameBack(1_000_000L, 1_000_000 - 5_001), "not yet");
        yes(Summary.cameBack(1_000_000L, 1_000_000 - 5_000), "Android posts it a few seconds early");
        yes(Summary.cameBack(1_000_000L, 2_000_000), "late");
    }

    public static void testNotificationNotSnoozedTwice() {
        // process restarted: only the stored set remembers that k was held until 1_000_000
        java.util.Set<String> stored = Summary.hold(java.util.Set.of(), "k", 1_000_000L, 0);
        yes(Summary.cameBack(Summary.due(stored, "k"), 999_000), "the stored key comes back through");
        yes(Summary.due(stored, "other") == null, "another key is new");
        yes(Summary.hold(stored, "j", 6_000_000L, 5_000_000L).size() == 1, "expired entries are forgotten");
    }

    public static void testReleaseDropsOnlyThatKey() {
        java.util.Set<String> held = java.util.Set.of("0|a|b|9000000", "0|a|9000000", "x|9000000");
        eq(java.util.Set.of("0|a|9000000", "x|9000000"), Summary.release(held, "0|a|b"), "a key with '|' inside is matched whole");
        eq(held, Summary.release(held, "0|a|"), "a prefix of a key is not the key");
    }

    public static void testMalformedHeldEntriesAreSkipped() {
        java.util.Set<String> bad = java.util.Set.of("nobar", "k|abc", "ok|9000000");
        yes(Summary.due(bad, "nobar") == null && Summary.due(bad, "k") == null, "malformed entries are not found");
        eq(9_000_000L, Summary.due(bad, "ok"), "a good one still is");
        eq(java.util.Set.of("ok|9000000", "n|9000000"), Summary.hold(bad, "n", 9_000_000L, 9_000_000L), "and hold drops the bad ones");
        eq(java.util.Set.of("ok|9000000"), Summary.expire(bad, 9_000_000L), "expire alone clears stale and bad entries");
    }

    public static void testCountsAHoldOnceNotOnEachRepost() {
        java.util.Set<String> held = Summary.hold(java.util.Set.of(), "k", 9_000_000L, 0);
        eq(1, Summary.counted(0, java.util.Set.of(), "k", 9_000_000L), "first hold counts");
        eq(1, Summary.counted(1, held, "k", 9_000_000L), "the same key already held does not");
        eq(2, Summary.counted(1, held, "j", 9_000_000L), "another key does");
        eq(2, Summary.counted(1, held, "k", 9_500_000L), "the same key held again for a later summary (the first was cancelled) does");
    }
}
