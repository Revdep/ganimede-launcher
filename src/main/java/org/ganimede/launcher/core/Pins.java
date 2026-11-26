package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The shortcuts ("s:pkg|id|name") among home-screen entries, as Android's pinned set wants them: ids by package. */
public final class Pins {
    /** The ids of the shortcuts of `pkg`, in entry order. */
    public static List<String> ids(List<String> entries, String pkg) {
        List<String> out = new ArrayList<>();
        for (String e : entries) {
            String[] p = parts(e);
            if (p.length == 3 && p[0].equals(pkg)) out.add(p[1]);
        }
        return out;
    }

    /** The packages that have a shortcut among the entries, each once. */
    public static List<String> packages(List<String> entries) {
        Set<String> out = new LinkedHashSet<>();
        for (String e : entries) {
            String[] p = parts(e);
            if (p.length == 3) out.add(p[0]);
        }
        return new ArrayList<>(out);
    }

    /** Gesture and button actions ("sc:pkg|id|name") as shortcut entries; other actions are dropped. */
    public static List<String> fromActions(Collection<String> actions) {
        List<String> out = new ArrayList<>();
        for (String a : actions) if (a.startsWith("sc:")) out.add("s:" + a.substring(3));
        return out;
    }

    /** The packages whose shortcut ids differ between the two entry lists, including those that dropped out. */
    public static List<String> changed(List<String> before, List<String> after) {
        Set<String> pkgs = new LinkedHashSet<>(packages(before));
        pkgs.addAll(packages(after));
        List<String> out = new ArrayList<>();
        for (String pkg : pkgs) if (!ids(before, pkg).equals(ids(after, pkg))) out.add(pkg);
        return out;
    }

    private static String[] parts(String entry) {
        return entry.startsWith("s:") ? entry.substring(2).split("\\|", 3) : new String[0];
    }

    private Pins() {}
}
