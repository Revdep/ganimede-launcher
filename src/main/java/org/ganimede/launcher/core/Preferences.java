package org.ganimede.launcher.core;

import java.util.HashMap;
import java.util.Map;

/** Pure preference migrations applied before a screen reads its settings. */
public final class Preferences {
    public static final String LINE_MIGRATED = "line_positions_migrated";

    /** New line positions inherit the old combined clock position, but only while each new key is missing. */
    public static Map<String, Object> lineMigration(Map<String, ?> current) {
        if (Boolean.TRUE.equals(current.get(LINE_MIGRATED))) return Map.of();
        Map<String, Object> additions = new HashMap<>();
        copyIfMissing(current, additions, "clock_offset", "line_offset");
        copyIfMissing(current, additions, "list_clock_y", "list_line_y");
        additions.put(LINE_MIGRATED, true);
        return additions;
    }

    private static void copyIfMissing(Map<String, ?> current, Map<String, Object> additions, String from, String to) {
        Object value = current.get(from);
        if (!current.containsKey(to) && value instanceof Integer) additions.put(to, (Integer) value);
    }

    private Preferences() {}
}
