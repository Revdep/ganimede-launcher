package org.ganimede.launcher.core;

import java.util.Arrays;
import java.util.Set;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

public class VoiceTest {
    public static void testOnlyAudioSharedByAnApp() {
        yes(Voice.playable("audio/ogg", "content://chat.files/v/1.ogg"), "a voice note");
        yes(Voice.playable("Audio/AAC; codecs=mp4a", "content://chat.files/2"), "any case, with parameters");
        yes(!Voice.playable("image/jpeg", "content://chat.files/p.jpg"), "a photo");
        yes(!Voice.playable("audio/", "content://chat.files/3"), "no audio kind");
        yes(!Voice.playable("audio/ogg", "file:///data/data/org.ganimede.launcher/shared_prefs/p.xml"), "a file path: never");
        yes(!Voice.playable("audio/ogg", "https://example.org/v.ogg"), "the web: never");
        yes(!Voice.playable("audio/ogg", "CONTENT://chat.files/4"), "Android reads only content:// in lower case");
        yes(!Voice.playable(null, "content://chat.files/5") && !Voice.playable("audio/ogg", null), "missing values");
    }

    public static void testListsEveryVoiceNoteInOrder() {
        String[] types = {"audio/ogg", null, "audio/mp4", "image/png", "audio/ogg"};
        String[] uris = {"content://a/1", null, "content://a/2", "content://a/3", "file:///4"};
        eq("[0, 2]", Arrays.toString(Voice.all(types, uris)), "every playable one, skipping text, a photo and a file path");
        // The chat's own order (oldest first), not the times: a note with an equal or missing (0) time keeps its place.
        eq("[0, 1]", Arrays.toString(Voice.all(new String[]{"audio/ogg", "audio/ogg"},
                new String[]{"content://a/1", "content://a/2"})), "two notes: both, the first one first");
        eq("[]", Arrays.toString(Voice.all(new String[]{null}, new String[]{null})), "text only");
        eq("[]", Arrays.toString(Voice.all(new String[0], new String[0])), "no messages");
    }

    public static void testKeptCopiesAreNamedByHashesPerNotificationAndNote() {
        String a = Voice.keptName("0|org.telegram|1|null|10123", "content://chat.files/v/1.ogg");
        eq(a, Voice.keptName("0|org.telegram|1|null|10123", "content://chat.files/v/1.ogg"), "the same note: the same file");
        yes(!a.equals(Voice.keptName("0|org.telegram|2|null|10123", "content://chat.files/v/1.ogg")), "another notification: another file");
        yes(!a.equals(Voice.keptName("0|org.telegram|1|null|10123", "content://chat.files/v/2.ogg")), "another note: another file");
        yes(a.matches("[0-9a-f]{32}-[0-9a-f]{32}"), "only hex reaches the disk, no key nor address: " + a);
    }

    public static void testDropsTheCopiesOfGoneNotesAndStaleHalves() {
        String[] names = {"a", "b", "c.part", "d.part"};
        long now = 1_000_000;
        long[] modified = {now - 500_000, now - 500_000, now - Voice.COPY_MS + 1, now - Voice.COPY_MS - 1};
        // b is no longer in any notification; c is being written now; d was left by a copy cut off or a crash.
        eq("[b, d.part]", Voice.drop(names, modified, Set.of("a", "c", "d"), now, now).toString(), "gone, and a half older than a copy may take");
        eq("[a, b, d.part]", Voice.drop(names, modified, Set.of(), now, now).toString(), "no notification left: all but the half being written");
        eq("[a, b, c.part, d.part]", Voice.drop(names, modified, null, now, now).toString(), "no access: everything, a young half too");
    }

    public static void testSweepSparesACopyMadeAfterItsSnapshot() {
        long snap = 1_000_000;
        String[] names = {"old", "fresh"};
        long[] modified = {snap - 10, snap + 10}; // fresh was copied after the notifications were read: not in the snapshot
        eq("[old]", Voice.drop(names, modified, Set.of(), snap, snap + 20).toString(), "only the copy older than the snapshot goes");
        eq("[old, fresh]", Voice.drop(names, modified, null, snap, snap + 20).toString(), "no access: still everything");
    }

    public static void testSweepSparesACopyMadeInTheSnapshotSecondWhenTimesAreWholeSeconds() {
        long snap = 1_000_700; // File.lastModified() on Android is whole seconds: a copy at 1_000_900 reads as 1_000_000
        eq("[]", Voice.drop(new String[]{"fresh"}, new long[]{1_000_000}, Set.of(), snap, snap + 20).toString(), "same second: spared");
        eq("[old]", Voice.drop(new String[]{"old"}, new long[]{999_000}, Set.of(), snap, snap + 20).toString(), "an earlier second goes");
    }

    public static void testQueueOfCopiesHasARoof() {
        yes(Voice.admits(0), "empty");
        yes(Voice.admits(Voice.QUEUE_MAX - 1), "one place left");
        yes(!Voice.admits(Voice.QUEUE_MAX), "full: no more copies queued");
    }

    public static void testKeepsANoteOnlyIfAWholeOneStillFits() {
        yes(Voice.room(0), "empty");
        yes(Voice.room(Voice.KEPT_BYTES - Voice.NOTE_BYTES), "a largest note fills it exactly");
        yes(!Voice.room(Voice.KEPT_BYTES - Voice.NOTE_BYTES + 1), "one byte more: no copy, nothing written past the cap");
    }

    public static void testPlayerClock() {
        eq("0:00", Voice.clock(0), "start");
        eq("0:07", Voice.clock(7_999), "whole seconds, rounded down");
        eq("1:05", Voice.clock(65_000), "a minute and more");
        eq("72:00", Voice.clock(72 * 60_000), "long notes stay in minutes");
        eq("0:00", Voice.clock(-5), "an unknown length");
    }
}
