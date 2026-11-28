package org.ganimede.launcher.core;

import java.util.List;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

public class LatestTest {
    public static void testARepostReplacesItsKeyAndTakesNoNewPlace() {
        Latest<String> l = new Latest<>(2);
        yes(l.offer("a", "a1") && l.offer("b", "b1"), "two keys fit");
        yes(l.offer("a", "a2"), "the same key again fits even when full");
        eq("[a2, b1]", l.drain().toString(), "only the latest of a key is kept");
        eq("[]", l.drain().toString(), "drained: empty");
    }

    public static void testANewKeyBeyondTheRoofIsRefusedUntilDrained() {
        Latest<String> l = new Latest<>(Voice.QUEUE_MAX);
        for (int i = 0; i < Voice.QUEUE_MAX; i++) yes(l.offer("k" + i, "v"), "place " + i);
        yes(!l.offer("extra", "v"), "full: a new key waits no more");
        List<String> all = l.drain();
        eq(Voice.QUEUE_MAX, all.size(), "everything offered was kept");
        yes(l.offer("extra", "v"), "room again once drained");
    }
}
