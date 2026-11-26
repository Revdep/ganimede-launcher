package org.ganimede.launcher.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rules for apps explicitly kept out of the often-used suggestions. */
public final class Suggestions {
    /** Removed favourites stay excluded; choosing them again makes them suggestible. */
    public static Set<String> unsuggested(Set<String> current, List<String> before, List<String> after) {
        Set<String> out = new HashSet<>(current);
        out.addAll(before);
        out.removeAll(after);
        return out;
    }

    private Suggestions() {}
}
