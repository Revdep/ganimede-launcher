package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.OptionalInt;

/** Lookup by app, on the index the packer builds from the fixture appfilter.xml (src/test/resources/icons). */
public class IconIndexTest {
    private static String found(String pkg, String cls) {
        IconSet set = IconSetTest.fixture();
        OptionalInt id = set.index().find(pkg, cls);
        return id.isPresent() ? set.name(id.getAsInt()) : "none";
    }

    public static void testFnv48KnownVector() {
        // FNV-1a 64 of "a" is the published 0xaf63dc4c8601ec8c; the index keeps its low 48 bits
        eq(0xdc4c8601ec8cL, IconIndex.fnv48("a"), "low 48 bits of FNV-1a 64");
    }

    public static void testPackageHit() {
        eq("square", found("pkgA", "pkgA.X"), "a listed component of a one-icon package");
        eq("square", found("pkgA", "pkgA.Z"), "a component the appfilter never saw still gets its package's icon");
    }

    public static void testComponentOverride() {
        eq("arc", found("pkgB", "pkgB.X"), "pkgB/.X");
        eq("oval", found("pkgB", "pkgB.Y"), "pkgB/.Y");
    }

    public static void testMiss() {
        eq("none", found("pkgB", "pkgB.Z"), "a package split by component has no package-wide icon");
        eq("none", found("pkgC", "pkgC.X"), "an appfilter line whose drawable does not exist");
        eq("none", found("pkgZ", "pkgZ.Main"), "an app Arcticons does not know");
    }

    public static void testRealAssetFindsGoogleCalendar() {
        IconSet set = IconSetTest.load("src/main/assets/icons.bin");
        OptionalInt id = set.index().find("com.google.android.calendar", "com.android.calendar.AllInOneActivity");
        yes(id.isPresent(), "Google Calendar is in Arcticons' appfilter");
        eq("calendar_google_31", set.name(id.getAsInt()), "its static icon (appfilter.xml <item>)");
    }
}
