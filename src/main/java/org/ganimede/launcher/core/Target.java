package org.ganimede.launcher.core;

import java.util.List;
import java.util.function.Function;

/** Which home-screen entry an app returning home lands on. */
public final class Target {
    /** "pkg/cls", plus "#serial" for another user's profile (serial -1: our own). */
    public static String key(String component, long serialOrMinus1) {
        return serialOrMinus1 < 0 ? component : component + "#" + serialOrMinus1;
    }

    /** The component of a key, without its "#serial". */
    public static String component(String key) {
        return key.split("#")[0];
    }

    /** The profile serial of a key, -1 for our own profile (or a serial that isn't a number). */
    public static long serial(String key) {
        int at = key.indexOf('#');
        try {
            return at < 0 ? -1 : Long.parseLong(key.substring(at + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The key if opened (openedKey, null when gone) or shown, else the package's other shown entry, else its folder "f:x"; or "". */
    public static String pick(String key, String pkg, String openedKey, List<String> shownKeys,
                              Function<String, List<String>> folderApps) {
        if (key.equals(openedKey) || shownKeys.contains(key)) return key;
        for (String k : shownKeys) if (k.startsWith(pkg + "/")) return k;
        for (String k : shownKeys) if (k.startsWith("f:") && folderApps.apply(k.substring(2)).contains(key)) return k;
        return "";
    }

    /** Whether the icon just rebuilt at this position of its key's duplicates (page, dock) is the one the window lands on. */
    public static boolean isLandingIcon(String entry, int index, String landingKey, int landingIndex) {
        return entry.equals(landingKey) && index == landingIndex;
    }

    private Target() {}
}
