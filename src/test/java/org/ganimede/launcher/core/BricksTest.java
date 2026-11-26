package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.yes;

/** Customize's Undo. */
public class BricksTest {
    public static void testCancelRestoresEveryThemeKey() {
        for (String k : Preferences.THEME_KEYS) yes(Preferences.UNDO_KEYS.contains(k), "undo covers theme key " + k);
    }
}
