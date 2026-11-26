package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

/** Undo in the home-screen editor: one step per gesture, however many small changes the gesture made. */
public class HistoryTest {
    public static void testNothingToUndoAtFirst() {
        yes(!new History<>("a", 10, 700).canUndo(), "a fresh history has no steps");
    }

    public static void testChangesCloseTogetherAreOneStep() {
        History<String> h = new History<>("a", 10, 700);
        h.changed("b", 1000);
        h.changed("c", 1060); // the same drag, a moment later
        h.changed("d", 1500);
        eq("a", h.undo(2000), "one undo takes the whole drag back");
        yes(!h.canUndo(), "and nothing is left");
    }

    public static void testAPauseStartsANewStep() {
        History<String> h = new History<>("a", 10, 700);
        h.changed("b", 1000);
        h.changed("c", 3000);
        eq("b", h.undo(4000), "the second gesture first");
        eq("a", h.undo(4100), "then the first");
    }

    public static void testTheSameStateIsNotAStep() {
        History<String> h = new History<>("a", 10, 700);
        h.changed("a", 1000);
        yes(!h.canUndo(), "nothing changed");
    }

    public static void testWhatFollowsAnUndoBelongsToIt() {
        History<String> h = new History<>("a", 10, 700);
        h.changed("b", 1000);
        eq("a", h.undo(5000), "back to the start");
        h.changed("a2", 5010); // the screen re-fitting the restored values
        yes(!h.canUndo(), "re-fitting right after an undo is not a new step");
    }

    public static void testOnlyTheLatestStepsAreKept() {
        History<Integer> h = new History<>(0, 2, 700);
        for (int i = 1; i <= 3; i++) h.changed(i, i * 1000);
        eq(2, h.undo(9000), "newest step");
        eq(1, h.undo(9100), "the one before");
        yes(!h.canUndo(), "the oldest was dropped");
    }
}
