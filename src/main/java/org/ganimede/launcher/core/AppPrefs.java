package org.ganimede.launcher.core;

import java.util.Collection;
import java.util.List;

/** The preferences kept per app ("use:" counts, "rn:" names; keys "package/class"), for those left behind by an uninstall. */
public final class AppPrefs {
    /** The keys of the owner's own profile that belong to the activities of this package, sorted; another profile's keys end in "#serial". */
    public static List<String> of(Collection<String> keys, String pkg) {
        return keys.stream().filter(k -> (k.startsWith("use:" + pkg + "/") || k.startsWith("rn:" + pkg + "/")) && k.indexOf('#') < 0).sorted().collect(java.util.stream.Collectors.toList());
    }

    private AppPrefs() {}
}
