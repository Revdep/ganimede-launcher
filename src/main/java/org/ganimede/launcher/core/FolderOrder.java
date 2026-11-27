package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.List;

/** The order of a folder's entries: which slot a finger is over, moving one icon, and the list after choosing apps. */
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

    /** The entries after "Choose apps": the stored ones still checked keep their order, newly checked ones go last. */
    public static List<String> chosen(List<String> stored, List<String> checked) {
        List<String> out = new ArrayList<>(stored);
        out.retainAll(checked);
        for (String k : checked) if (!out.contains(k)) out.add(k);
        return out;
    }

    private FolderOrder() { }
}
