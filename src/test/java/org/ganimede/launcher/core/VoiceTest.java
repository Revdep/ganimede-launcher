package org.ganimede.launcher.core;

import java.util.Arrays;

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

    public static void testPlayerClock() {
        eq("0:00", Voice.clock(0), "start");
        eq("0:07", Voice.clock(7_999), "whole seconds, rounded down");
        eq("1:05", Voice.clock(65_000), "a minute and more");
        eq("72:00", Voice.clock(72 * 60_000), "long notes stay in minutes");
        eq("0:00", Voice.clock(-5), "an unknown length");
    }
}
