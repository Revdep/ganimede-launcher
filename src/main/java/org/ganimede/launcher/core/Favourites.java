package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** What the List home column lists: the chosen entries still there, then the often-used suggestions. */
public final class Favourites {
    /** Where, in the saved list, the favourites to draw sit: hidden entries and entries that no longer exist drop out. */
    public static List<Integer> positions(List<String> favs, Set<String> hidden, Predicate<String> exists) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < favs.size(); i++) if (exists.test(favs.get(i)) && !hidden.contains(favs.get(i))) out.add(i);
        return out;
    }

    /** The favourites to draw, in order. */
    public static List<String> shown(List<String> favs, Set<String> hidden, Predicate<String> exists) {
        List<String> out = new ArrayList<>();
        for (int i : positions(favs, hidden, exists)) out.add(favs.get(i));
        return out;
    }

    /** Up to two visible apps used at least 3 times, most used first, not favourites and not refused. */
    public static List<String> suggested(List<String> visible, List<String> favs, Set<String> unsuggested, ToIntFunction<String> uses) {
        List<String> often = new ArrayList<>(visible);
        often.removeIf(k -> favs.contains(k) || unsuggested.contains(k) || uses.applyAsInt(k) < 3);
        often.sort((a, b) -> Integer.compare(uses.applyAsInt(b), uses.applyAsInt(a)));
        return often.subList(0, Math.min(2, often.size()));
    }

    /** What the classic favourites migration reads; the same key means the same (already saved) result. */
    public static String migrationKey(Map<String, ?> prefs) {
        Map<String, Object> read = new java.util.TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("folder:") || k.startsWith("grid") || k.equals("fav") || k.equals("dock") || k.equals("blocks")) read.put(k, e.getValue());
        }
        return read.toString();
    }

    private Favourites() {}
}
