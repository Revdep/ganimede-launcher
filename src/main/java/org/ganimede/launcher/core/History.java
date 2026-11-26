package org.ganimede.launcher.core;

import java.util.ArrayDeque;

/**
 * Undo steps for an editor that saves as it goes: every state it passes through is reported here, and changes that
 * follow each other closely (one drag, one held arrow) count as a single step back.
 */
public final class History<T> {
    private final ArrayDeque<T> steps = new ArrayDeque<>();
    private final int most;
    private final long pause;
    private T now;
    private long last = Long.MIN_VALUE / 2;

    /** Keeps at most `most` steps; a change more than `pauseMs` after the previous one starts a new step. */
    public History(T start, int most, long pauseMs) {
        now = start;
        this.most = most;
        pause = pauseMs;
    }

    public void changed(T state, long atMs) {
        if (state.equals(now)) return;
        if (atMs - last > pause) {
            steps.push(now);
            if (steps.size() > most) steps.removeLast();
        }
        now = state;
        last = atMs;
    }

    public boolean canUndo() {
        return !steps.isEmpty();
    }

    /** The state before the latest step. What the editor does right after (re-fitting) belongs to this undo. */
    public T undo(long atMs) {
        now = steps.pop();
        last = atMs;
        return now;
    }
}
