package org.ganimede.launcher.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Pure preference migrations applied before a screen reads its settings. */
public final class Preferences {
    public static final String LINE_MIGRATED = "line_positions_migrated";

    /** The settings Customize changes: what its Undo takes back. */
    public static final Set<String> UNDO_KEYS = Set.of("mode", "last_moved",
            "clock_offset", "list_clock_y", "list_clock_size", "line_offset", "list_line_y", "clock_style", "clock24", "show_date", "show_weather", "show_battery",
            "event_preview", "media_player", "list_widget_x", "list_widget_y", "list_widget_h", "widget_below",
            "apps_offset", "list_apps_y", "list_apps_h", "icon_size", "fav_gap", "hide_labels", "label_scale",
            "alpha_home", "alpha_offset", "alpha_y", "alpha_size", "alpha_tint", "home_bottom", "left_handed", "fab",
            "classic_grid_x", "classic_grid_y", "classic_dock_x", "classic_dock_y", "classic_pad_l", "classic_pad_r",
            "classic_pad_t", "classic_pad_b", "grid_cols", "grid_rows", "grid_pages", "grid_icon", "blocks", "grid", "dock",
            "dock_count", "dock_alpha", "drawer", "drawer_cols", "drawer_labels", "hide_status_bar", "rotate", "dim", "dim_home",
            "font", "text_color", "accent", "icon_glyph", "icon_container", "icon_fill", "icon_color", "icon_pack", "icon_category",
            FolderLook.BACKGROUND_KEY, FolderLook.ALPHA_KEY, FolderLook.COLUMNS_KEY, "drawer_follow", "drawer_icon",
            "drawer_label_scale", "drawer_font", "drawer_icon_glyph", "drawer_icon_container", "drawer_icon_fill", "drawer_icon_color");

    /** Settings that make up a theme (shared with "copy theme"). */
    public static final String[] THEME_KEYS = {"clock_style", "font", "text_color", "accent",
            "icon_glyph", "icon_container", "icon_fill", "icon_color", "icon_size",
            "hide_labels", "show_battery", "show_date", "alpha_home", "fab", "dim", "dim_home", "hide_status_bar",
            FolderLook.BACKGROUND_KEY, FolderLook.ALPHA_KEY, FolderLook.COLUMNS_KEY};

    /** Written by the app itself, not drawn by the home: a change in these needs no rebuild. */
    private static final Set<String> NOT_DRAWN = Set.of("summary_held", "summary_last", "summary_count", "icon_pack_loaded",
            "return_to_settings", "pending_widget", "pending_widget_for", "fab_left", "fab_y");

    /** Whether a written setting changes what the home draws. Unknown keys do, so a forgotten one costs a rebuild, never a stale screen. */
    public static boolean rebuildsHome(String key, boolean smartSort, boolean suggest) {
        if (key == null) return true; // the whole file was cleared
        if (key.startsWith("use:")) return smartSort || suggest; // the open counts order the list and the suggestions
        return !NOT_DRAWN.contains(key);
    }

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
