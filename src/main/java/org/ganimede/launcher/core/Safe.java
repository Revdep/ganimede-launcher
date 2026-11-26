package org.ganimede.launcher.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What other apps, backup files and the clipboard hand over: kept only in a form that is safe to store and show. */
public final class Safe {
    private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "tel", "mailto", "geo", "sms", "smsto");

    /** Text for the screen: control characters become spaces, direction overrides go, at most max characters. */
    public static String printable(String s, int max) {
        if (s == null || max <= 0) return "";
        String t = s.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", " ")
                .replaceAll("[\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "").trim();
        if (t.length() <= max) return t;
        return t.substring(0, Character.isHighSurrogate(t.charAt(max - 1)) ? max - 1 : max).trim();
    }

    /**
     * A pinned shortcut as the home stores it ("s:package|id|label", in newline-separated lists), or null when the
     * package or id would break that format.
     */
    public static String shortcutEntry(String pkg, String id, String label) {
        if (pkg == null || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
                || id == null || id.isEmpty() || id.contains("|") || id.contains("\n")) return null;
        String l = printable(label, 64).replace('|', ' ');
        return "s:" + pkg + "|" + id + "|" + (l.isEmpty() ? printable(id, 64) : l);
    }

    /** A link from a backup file: plain web, phone, mail, map and message links only (never an app's private screen). */
    public static boolean restorableLink(String uri) {
        int colon = uri == null ? -1 : uri.indexOf(':');
        return colon > 0 && LINK_SCHEMES.contains(uri.substring(0, colon).toLowerCase(Locale.ROOT));
    }

    /** What a notification card shows. */
    public record Shown(CharSequence title, CharSequence body) {}

    /** Notification.VISIBILITY_SECRET (-1): nothing of the notification may be shown or played, only the app's name. */
    public static boolean secret(int visibility) {
        return visibility == -1;
    }

    /** Secret: the app's name only, no body. No text at all and a hint given: the app's name and the hint. Else as is. */
    public static Shown notice(boolean secret, CharSequence title, CharSequence body, String appName, CharSequence emptyHint) {
        if (secret) return new Shown(appName, null);
        if (title == null && body == null && emptyHint != null) return new Shown(appName, emptyHint);
        return new Shown(title, body);
    }

    /** Matches found in a message -> buttons: only web links; "www." gets "https://"; each once (after that); at most 3. */
    public static List<String> webLinks(List<String> found) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String url : found) {
            if (!url.matches("(?i)^(https?://|www\\.).*")) continue;
            out.add(url.toLowerCase(Locale.ROOT).startsWith("www.") ? "https://" + url : url);
            if (out.size() == 3) break;
        }
        return List.copyOf(out);
    }

    private Safe() {}
}
