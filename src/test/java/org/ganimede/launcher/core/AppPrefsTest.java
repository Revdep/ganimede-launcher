package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;
import java.util.Set;

public class AppPrefsTest {
    public static void testOnlyTheRemovedPackagesCountAndRenamesAreLeftOfOthers() {
        Set<String> keys = Set.of("use:org.a/org.a.Main", "rn:org.a/org.a.Main", "use:org.a.b/org.a.b.Main", "rn:org.a/org.a.Other",
                "use:org.a/org.a.Main#10", "hidden", "icon:org.a/org.a.Main");
        eq(List.of("rn:org.a/org.a.Main", "rn:org.a/org.a.Other", "use:org.a/org.a.Main"), AppPrefs.of(keys, "org.a"),
                "use: and rn: of this package's own activities; not another package that starts the same, not the work profile's (#), not icon:");
    }
}
