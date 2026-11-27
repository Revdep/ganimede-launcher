package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.List;

/** Reordering the icons of an open folder: which slot a finger is over, and the list after moving one icon. */
public final class FolderOrder {
    /**
     * The slot whose top-left corner (xs[i], ys[i]) is nearest (x, y), the dragged icon's own top-left corner; an icon
     * dragged outside the folder lands on an edge slot.
     */
    public static int nearest(float x, float y, float[] xs, float[] ys) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            double d = Math.hypot(xs[i] - x, ys[i] - y);
            if (d < bestDistance) { best = i; bestDistance = d; }
        }
        return best;
    }

    /** A copy of the list with the entry at `from` moved to place `to` (clamped into the list), the others shifting over. */
    public static <T> List<T> move(List<T> list, int from, int to) {
        List<T> out = new ArrayList<>(list);
        out.add(Math.max(0, Math.min(out.size() - 1, to)), out.remove(from));
        return out;
    }

    /**
     * The stored entries with the shown ones rearranged as `reordered`; entries that are not shown keep their place.
     * If `reordered` names an entry that is no longer stored (the folder changed meanwhile) nothing changes.
     */
    public static List<String> reorderShown(List<String> stored, List<String> reordered) {
        List<String> out = new ArrayList<>(stored);
        if (stored.stream().filter(reordered::contains).count() != reordered.size()) return out;
        int next = 0;
        for (int i = 0; i < out.size(); i++) if (reordered.contains(out.get(i))) out.set(i, reordered.get(next++));
        return out;
    }

    private FolderOrder() { }
}
