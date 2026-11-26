package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;
import java.util.function.Function;

public class TargetTest {
    private static final Function<String, List<String>> NO_FOLDERS = f -> List.of();

    public static void testTheOpenedIconWins() {
        List<String> shown = List.of("com.a/.Main", "com.a/.Other");
        eq("com.a/.Other", Target.pick("com.a/.Other", "com.a", "com.a/.Other", shown, NO_FOLDERS), "the opened one");
        eq("com.a/.Other", Target.pick("com.a/.Other", "com.a", null, shown, NO_FOLDERS), "shown, nothing opened");
        eq("com.a/.Other", Target.pick("com.a/.Other", "com.a", "com.a/.Other", List.of(), NO_FOLDERS), "opened and still on screen");
    }

    public static void testAnotherActivityOfTheSamePackage() {
        eq("com.a/.Main", Target.pick("com.a/.Gone", "com.a", null, List.of("com.b/.X", "com.a/.Main"), NO_FOLDERS), "same package");
        eq("", Target.pick("com.a/.Gone", "com.a", null, List.of("com.ab/.X"), NO_FOLDERS), "a longer package name is not it");
    }

    public static void testTheFolderHoldingTheApp() {
        Function<String, List<String>> folders = f -> f.equals("Work") ? List.of("com.z/.Main") : List.of();
        eq("f:Work", Target.pick("com.z/.Main", "com.z", null, List.of("com.b/.X", "f:Play", "f:Work"), folders), "its folder");
        eq("", Target.pick("com.z/.Main", "com.z", null, List.of("f:Play"), folders), "a folder not holding it");
    }

    public static void testNothingShownMeansNoAnswer() {
        eq("", Target.pick("com.a/.Main", "com.a", null, List.of(), NO_FOLDERS), "nothing shown");
    }

    public static void testAWorkProfileIconHasItsOwnKey() {
        eq("com.a/.Main", Target.key("com.a/.Main", -1), "own profile");
        eq("com.a/.Main#10", Target.key("com.a/.Main", 10), "work profile");
        List<String> both = List.of("com.a/.Main", "com.a/.Main#10");
        eq("com.a/.Main#10", Target.pick("com.a/.Main#10", "com.a", null, both, NO_FOLDERS), "the work icon, not the other one");
    }

    public static void testOnlyTheRebuiltDuplicateAtTheLandingPositionIsHidden() {
        String key = "com.a/.Main";
        eq(false, Target.isLandingIcon(key, 0, key, 1), "the page copy when the dock copy lands");
        eq(true, Target.isLandingIcon(key, 1, key, 1), "the dock copy when it lands");
        eq(false, Target.isLandingIcon("com.b/.X", 1, key, 1), "another app");
        eq(false, Target.isLandingIcon(key, 0, null, -1), "nothing landing");
        eq(false, Target.isLandingIcon(key, 0, key, -1), "the landing icon was not tracked");
    }

    public static void testSerialOfAKey() {
        eq(-1L, Target.serial("com.a/.Main"), "our own profile");
        eq(10L, Target.serial("com.a/.Main#10"), "a work profile");
        eq("com.a/.Main", Target.component("com.a/.Main#10"), "the component without the serial");
        eq(-1L, Target.serial("com.a/.Main#x"), "a broken serial is our own profile");
    }
}
