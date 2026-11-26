package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** What the List home column lists: the chosen entries still there, then the often-used suggestions. */
public final class Favourites {
    /** The favourites to draw: hidden entries and entries that no longer exist drop out, the order stays. */
    public static List<String> shown(List<String> favs, Set<String> hidden, Predicate<String> exists) {
        List<String> out = new ArrayList<>();
        for (String k : favs) if (exists.test(k) && !hidden.contains(k)) out.add(k);
        return out;
    }

    /** Up to two visible apps used at least 3 times, most used first, not favourites and not refused. */
    public static List<String> suggested(List<String> visible, List<String> favs, Set<String> unsuggested, ToIntFunction<String> uses) {
        List<String> often = new ArrayList<>(visible);
        often.removeIf(k -> favs.contains(k) || unsuggested.contains(k) || uses.applyAsInt(k) < 3);
        often.sort((a, b) -> Integer.compare(uses.applyAsInt(b), uses.applyAsInt(a)));
        return often.subList(0, Math.min(2, often.size()));
    }

    private Favourites() {}
}
