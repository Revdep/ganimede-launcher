package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

public class DrawerSettingsTest {
    public static void testFollowHomeKeepsTheHomeKey() {
        eq("grid_icon", DrawerSettings.key(true, "grid_icon"), "icon size follows home");
        eq("font", DrawerSettings.key(true, "font"), "font follows home");
    }

    public static void testIndependentDrawerUsesItsOwnKeys() {
        eq("drawer_icon", DrawerSettings.key(false, "grid_icon"), "drawer icon size");
        eq("drawer_label_scale", DrawerSettings.key(false, "label_scale"), "drawer label size");
        eq("drawer_font", DrawerSettings.key(false, "font"), "drawer font");
        eq("drawer_icon_shape", DrawerSettings.key(false, "icon_shape"), "drawer icon shape");
    }

    public static void testNamesFollowHomeOrTheirOwnSwitch() {
        yes(!DrawerSettings.labels(true, false, true), "following home: the home's names hidden, so are the drawer's");
        yes(DrawerSettings.labels(true, true, false), "following home: the home's names shown, so are the drawer's");
        yes(DrawerSettings.labels(false, false, true), "on its own: the drawer's switch, whatever the home hides");
        yes(!DrawerSettings.labels(false, true, false), "on its own: names off");
    }
}
