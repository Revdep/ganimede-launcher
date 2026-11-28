package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

public class EntryTest {
    public static void testKindFromThePrefix() {
        eq(Entry.Kind.FOLDER, Entry.kind("f:7"), "folder");
        eq(Entry.Kind.SHORTCUT, Entry.kind("s:com.a|x|One"), "shortcut");
        eq(Entry.Kind.APP, Entry.kind("com.a/.Main"), "app");
        eq(Entry.Kind.APP, Entry.kind(""), "empty is not a folder or shortcut");
    }

    public static void testFolderId() {
        eq("7", Entry.folderId("f:7"), "id");
        eq("", Entry.folderId("f:"), "empty id");
        eq(null, Entry.folderId("s:a|b|c"), "not a folder");
    }

    public static void testShortcutWithAllThreeFields() {
        String[] p = Entry.shortcut("s:com.a|x|One");
        eq(3, p.length, "fields");
        eq("com.a", p[0], "pkg");
        eq("x", p[1], "id");
        eq("One", p[2], "name");
        eq("One", Entry.shortcutName("s:com.a|x|One"), "name");
    }

    public static void testShortcutNameMayHoldABar() {
        eq("A|B", Entry.shortcut("s:com.a|x|A|B")[2], "third field keeps the rest");
    }

    public static void testShortcutWithTwoFieldsNamesItselfByTheId() {
        eq(2, Entry.shortcut("s:com.a|x").length, "fields");
        eq("x", Entry.shortcutName("s:com.a|x"), "fallback to the last field");
    }

    public static void testNotAShortcutHasNoFields() {
        eq(0, Entry.shortcut("f:1").length, "folder");
        eq(0, Entry.shortcut("").length, "empty");
        eq("", Entry.shortcutName("com.a/.Main"), "no name");
    }

    public static void testActionPayloadSplitsTheSameWay() {
        String[] p = Entry.split("com.a|x|A|B");
        eq("com.a", p[0], "pkg");
        eq("A|B", p[2], "name");
    }
}
