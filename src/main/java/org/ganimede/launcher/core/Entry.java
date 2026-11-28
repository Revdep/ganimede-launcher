package org.ganimede.launcher.core;

/** What a home-screen entry string is: an app ("pkg/.Cls"), a folder ("f:id") or a pinned shortcut ("s:pkg|id|name"). */
public final class Entry {
    public enum Kind { APP, FOLDER, SHORTCUT }

    public static Kind kind(String entry) {
        return entry.startsWith("f:") ? Kind.FOLDER : entry.startsWith("s:") ? Kind.SHORTCUT : Kind.APP;
    }

    /** The folder's id, or null when the entry is not a folder. */
    public static String folderId(String entry) {
        return kind(entry) == Kind.FOLDER ? entry.substring(2) : null;
    }

    /** The fields of a shortcut entry (pkg, id, name; fewer in old data); empty when it is not a shortcut. */
    public static String[] shortcut(String entry) {
        return kind(entry) == Kind.SHORTCUT ? split(entry.substring(2)) : new String[0];
    }

    /** A shortcut's name, falling back to its last field; "" when the entry is not a shortcut. */
    public static String shortcutName(String entry) {
        String[] p = shortcut(entry);
        return p.length == 0 ? "" : p.length > 2 ? p[2] : p[p.length - 1];
    }

    /** The "pkg|id|name" payload, shared by "s:" entries and "sc:" actions. */
    public static String[] split(String payload) {
        return payload.split("\\|", 3);
    }

    private Entry() {}
}
