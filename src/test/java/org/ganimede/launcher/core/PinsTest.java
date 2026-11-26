package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;

public class PinsTest {
    public static void testIdsOfOnePackageFromTheEntries() {
        List<String> entries = List.of("com.a/.Main", "s:com.a|x|One", "s:com.b|y|Two", "f:1", "s:com.a|z|Three", "s:bad");
        eq(List.of("x", "z"), Pins.ids(entries, "com.a"), "both of com.a");
        eq(List.of(), Pins.ids(entries, "com.c"), "none");
    }

    public static void testPackagesOfTheEntries() {
        eq(List.of("com.a", "com.b"), Pins.packages(List.of("s:com.a|x|One", "com.q/.M", "s:com.b|y|T", "s:com.a|z|U")), "once each");
    }

    public static void testActionsCountAsShortcutEntries() {
        eq(List.of("s:com.a|x|One", "s:com.b|y|Two"),
                Pins.fromActions(List.of("sc:com.a|x|One", "torch", "app:com.q/.M", "sc:com.b|y|Two")), "only sc: values");
    }

    public static void testOnlyPackagesWhoseIdsChangedOrDroppedOut() {
        List<String> before = List.of("s:com.a|x|A", "s:com.b|y|B", "s:com.c|z|C");
        List<String> after = List.of("s:com.a|x|A", "s:com.b|y2|B", "s:com.d|w|D");
        eq(List.of("com.b", "com.c", "com.d"), Pins.changed(before, after), "b changed, c dropped, d new, a same");
    }
}
