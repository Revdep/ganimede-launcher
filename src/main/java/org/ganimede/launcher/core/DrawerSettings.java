package org.ganimede.launcher.core;

/** The appearance of the app pages (Classic, drawer off): the home's own settings, or ones of their own. */
public final class DrawerSettings {
    /** The preference to read for this home setting: itself while following home, else the drawer's own. */
    public static String key(boolean followHome, String homeKey) {
        if (followHome) return homeKey;
        return homeKey.equals("grid_icon") ? "drawer_icon" : "drawer_" + homeKey;
    }

    /** Whether app names show: the home's choice while following home, else the drawer's own switch. */
    public static boolean labels(boolean followHome, boolean homeLabels, boolean ownLabels) {
        return followHome ? homeLabels : ownLabels;
    }

    private DrawerSettings() {}
}
