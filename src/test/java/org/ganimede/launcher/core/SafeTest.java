package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

public class SafeTest {
    public static void testPrintableDropsControlAndDirectionCharacters() {
        eq("Milano  Centro", Safe.printable("Milano\n\u0085Centro", 48), "control characters become spaces");
        eq("Roma", Safe.printable("‮Roma⁦‏", 48), "direction overrides are removed");
        eq("abc", Safe.printable("abcdef", 3), "cut at the limit");
        eq("a", Safe.printable("a😀", 2), "never half an emoji");
        eq("", Safe.printable(null, 8), "nothing");
    }

    public static void testPrintableWithNoRoomIsEmpty() {
        eq("", Safe.printable("abc", 0), "max 0");
        eq("", Safe.printable("abc", -1), "negative max");
    }

    public static void testShortcutEntryOnlyFromStorablePackageAndId() {
        eq("s:com.app|id1|Inbox", Safe.shortcutEntry("com.app", "id1", "Inbox"), "a plain shortcut");
        eq("s:com.app|id1|In box", Safe.shortcutEntry("com.app", "id1", "In|box"), "a bar in the label");
        eq("s:com.app|id1|A B", Safe.shortcutEntry("com.app", "id1", "A\nB"), "a new line in the label");
        eq("s:com.app|id1|id1", Safe.shortcutEntry("com.app", "id1", null), "no label: the id");
        eq(null, Safe.shortcutEntry("com.app\nfav", "id1", "x"), "a new line in the package");
        eq(null, Safe.shortcutEntry("com.app|x", "id1", "x"), "a bar in the package");
        eq(null, Safe.shortcutEntry("noDots", "id1", "x"), "not a package name");
        eq(null, Safe.shortcutEntry("com.app", "a|b", "x"), "a bar in the id");
        eq(null, Safe.shortcutEntry("com.app", "a\nb", "x"), "a new line in the id");
        eq(null, Safe.shortcutEntry("com.app", "", "x"), "an empty id");
    }

    public static void testRestorableLinksArePlainWebPhoneMailMapAndMessages() {
        for (String ok : new String[]{"https://example.org", "http://a.b/%s", "tel:+39021", "mailto:me@x.org", "geo:0,0?q=x",
                "sms:123", "HTTPS://Example.org"})
            yes(Safe.restorableLink(ok), ok);
        for (String bad : new String[]{"intent:#Intent;component=a/.B;end", "content://x", "file:///sdcard", "javascript:x",
                "spotify:track", " https://x", "", null})
            yes(!Safe.restorableLink(bad), String.valueOf(bad));
    }

    private static void shown(String title, String body, Safe.Shown got, String what) {
        eq(title, got.title(), what + ": title");
        eq(body, got.body(), what + ": body");
    }

    public static void testOnlyVisibilitySecretIsSecret() {
        yes(Safe.secret(-1), "Notification.VISIBILITY_SECRET");
        yes(!Safe.secret(0) && !Safe.secret(1), "private and public notifications are not secret");
    }

    public static void testSecretNoticeShowsOnlyTheAppName() {
        shown("Chat", null, Safe.notice(true, "Anna", "pin 1234", "Chat", "Tap"), "secret with text");
        shown("Chat", null, Safe.notice(true, null, null, "Chat", "Tap"), "secret without text");
    }

    public static void testNoticeWithNoTextSaysWhatATapDoes() {
        shown("Clock", "Tap to open", Safe.notice(false, null, null, "Clock", "Tap to open"), "no text, hint");
        shown(null, "hi", Safe.notice(false, null, "hi", "X", "Tap"), "a body is enough");
        shown(null, null, Safe.notice(false, null, null, "X", null), "no text, no hint");
    }

    public static void testOnlyWebLinksGetAButtonAtMostThree() {
        eq(java.util.List.of("https://www.a.org/x"), Safe.webLinks(java.util.List.of("www.a.org/x")), "www gets https");
        eq(java.util.List.of("https://b.org"), Safe.webLinks(java.util.List.of("a.org", "ftp://c.org", "https://b.org")), "web only");
        eq(java.util.List.of("HTTP://A.ORG", "https://WWW.B.org"), Safe.webLinks(java.util.List.of("HTTP://A.ORG", "WWW.B.org")), "case kept");
        eq(java.util.List.of("http://1.org", "https://www.2.org", "http://3.org"), Safe.webLinks(java.util.List.of(
                "http://1.org", "http://1.org", "www.2.org", "https://www.2.org", "http://3.org", "http://4.org")), "once each, three");
        eq(java.util.List.of(), Safe.webLinks(java.util.List.of()), "none");
    }
}
