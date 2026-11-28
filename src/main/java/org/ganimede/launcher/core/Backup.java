package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which settings a backup file or a pasted theme may bring back, and the values that are safe to keep. */
public final class Backup {
    public static final String WEATHER = "wx"; // the stored weather, never backed up
    /** Numbers the home screen sizes things by: a value outside these could crash it or exhaust memory. */
    private static final Map<String, int[]> RANGES = new HashMap<>();
    /** The type each restorable setting is read as ("s" text, "b" switch, "i" number, "f", "l", "set"). */
    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        int[][] r = {{3, 8}, {2, 10}, {0, 8}, {2, 8}, {32, 80}, {60, 150}, {1, 100}, {16, 160}, {16, 160}, {8, 40}, {25, 300}, {20, 300},
                {0, 2000}, {5, 100}, {0, 95}, {0, 95}, {0, 100}, {-20, 40}, {-160, 160}, {4, 240}, {4, 240}, {4, 240}, {4, 420},
                {4, 80}, {4, 80}, {4, 200}, {4, 200}, {0, 32}};
        String[] k = {"grid_cols", "grid_rows", "dock_count", "drawer_cols", "drawer_icon", "drawer_label_scale",
                "grid_pages", "grid_icon", "icon_size", "alpha_size",
                "label_scale", "list_clock_size", "list_widget_h", "list_apps_h",
                "dim", "dim_home", "dock_alpha", "alpha_offset", "alpha_y", "clock_offset", "line_offset", "apps_offset", "home_bottom",
                "classic_pad_l", "classic_pad_r", "classic_pad_t", "classic_pad_b", "fav_gap"};
        for (int i = 0; i < k.length; i++) RANGES.put(k[i], r[i]);
        RANGES.put(FolderLook.ALPHA_KEY, new int[]{0, 100});
        RANGES.put(FolderLook.COLUMNS_KEY, new int[]{1, 5});
        // The parts moved in Settings (Position): as far as its arrows and the preview let them go.
        for (String part : new String[]{"classic_grid", "classic_dock", "list_widget"})
            RANGES.put(part + "_x", new int[]{-400, 400});
        for (String part : new String[]{"classic_grid", "classic_dock", "list_clock", "list_line",
                "list_widget", "list_apps"})
            RANGES.put(part + "_y", new int[]{-600, 600});
        String[] t = {
            "fav", "s", "dock", "s", "drawer", "b", "widget_below", "b", "hidden", "set", "unsuggested", "set", "custom_shortcuts", "s",
            "clock_style", "s", "clock24", "b", "font", "s", "text_color", "s", "accent", "i", "icon_shape", "s", "themed_shape", "s",
            FolderLook.BACKGROUND_KEY, "i", FolderLook.ALPHA_KEY, "i", FolderLook.COLUMNS_KEY, "i",
            "icon_size", "i", "icon_pack", "s", "icon_category", "b", "hide_labels", "b", "drawer_follow", "b", "drawer_labels", "b",
            "drawer_font", "s", "drawer_icon_shape", "s", "drawer_icon", "i", "drawer_label_scale", "i",
            "icon_glyph", "s", "icon_container", "s", "icon_fill", "s", "icon_color", "s",
            "drawer_icon_glyph", "s", "drawer_icon_container", "s", "drawer_icon_fill", "s", "drawer_icon_color", "s",
            "label_scale", "i", "show_date", "b",
            "show_battery", "b", "show_weather", "b", "alpha_home", "b", "alpha_tint", "i", "alpha_size", "i",
            "alpha_offset", "i", "apps_offset", "i", "clock_offset", "i", "line_offset", "i", "home_bottom", "i", "left_handed", "b",
            "fab", "b", "fab_icon", "s", "fab_tap", "s", "fab_double", "s", "fab_triple", "s", "fab_long", "s", "fab_left", "b",
            "fab_y", "f", "dim", "i", "dim_home", "i", "hide_status_bar", "b", "rotate", "b", "mode", "s",
            "grid_cols", "i", "grid_rows", "i", "dock_count", "i", "drawer_cols", "i", "grid_icon", "i",
            "dock_bg", "b", "fahrenheit", "b", "wx_tap", "s", "agenda", "b",
            "event_preview", "b", "cal_hidden", "set", "media_player", "b", "music_on_headset", "b",
            "music_hearing_aids", "b", "music_apps", "set", "suggest", "b", "smart_sort", "b", "swipe_search", "b",
            "contacts_search", "b", "search_shortcuts", "b", "search_settings", "b", "engine", "s",
            "quick_lock", "b", "haptics", "b", "notif_preview", "b", "summary", "b", "summary_skip_chats", "b", "summary_silent", "b",
            "summary_apps", "set", "breaker_min", "i", "breaker_apps", "set", "lang", "s", "onboarded", "b",
            "grid", "s", "blocks", "s", "grid_pages", "i", "alpha_y", "i", "dock_alpha", "i", "last_moved", "s", "list_apps_h", "i",
            "classic_grid_x", "i", "classic_grid_y", "i", "classic_dock_x", "i", "classic_dock_y", "i",
            "classic_pad_l", "i", "classic_pad_r", "i", "classic_pad_t", "i", "classic_pad_b", "i",
            "list_clock_y", "i", "list_line_y", "i", "list_widget_x", "i",
            "list_widget_y", "i", "list_apps_y", "i", "list_clock_size", "i", "list_widget_h", "i", "fav_gap", "i"};
        for (int i = 0; i < t.length; i += 2) TYPES.put(t[i], t[i + 1]);
    }

    /** The type a setting must have; per-app and per-folder keys by their prefix; null for one we don't know. */
    public static String typeOf(String k) {
        if (k.startsWith("rn:") || k.startsWith("icon:") || k.startsWith("folder:")) return "s";
        return TYPES.get(k);
    }

    /** Only Ganimede's own settings come back from a file. */
    public static boolean restorable(String k) {
        return typeOf(k) != null;
    }

    /** Whether a value is safe to keep: the type the app reads it as, inside the limits above. */
    public static boolean fits(String k, Object v) {
        String t = typeOf(k);
        boolean typed = t != null && (t.equals("s") ? v instanceof String : t.equals("b") ? v instanceof Boolean
                : t.equals("i") ? v instanceof Integer : t.equals("f") ? v instanceof Float
                : t.equals("l") ? v instanceof Long : v instanceof Set);
        if (!typed) return false; // unknown key or wrong type: the screen that reads it would crash
        int[] r = RANGES.get(k);
        if (r != null && ((Integer) v < r[0] || (Integer) v > r[1])) return false;
        if (v instanceof Float && !(((Float) v) >= 0 && ((Float) v) <= 1)) return false; // fab_y, a fraction of the height; rejects NaN too
        if (k.startsWith("icon:") && !((String) v).matches("([\\w.]+|" + IconSet.PACK + ")\\|\\w+")) return false; // "pack|drawable", as the picker saves it
        if (v instanceof String && ((String) v).length() > 64 * 1024) return false; // a real setting is far smaller
        if (k.equals("mode")) return v.equals("list") || v.equals("classic");
        if (v instanceof String && !IconLook.allows(k, (String) v)) return false; // e.g. the removed "cutout"
        return !k.equals("fab_icon") || v.equals("default") || v.equals("gray") || v.equals("colour") || v.equals("custom");
    }

    /** A home grid's cell keys: numbers only, inside 100 pages of the largest grid. */
    public static boolean cellsFit(Iterator<String> cells) {
        try {
            while (cells.hasNext()) {
                int cell = Integer.parseInt(cells.next());
                if (cell < 0 || cell >= 100 * 10 * 8) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** This phone's own state, kept through a restore: List widget ids, weather, summary timing, permissions asked. */
    public static Map<String, Object> kept(Map<String, ?> old) {
        Map<String, Object> keep = new HashMap<>();
        for (String k : old.keySet()) {
            Object v = old.get(k);
            if ((k.equals("stack") || k.equals(WEATHER)) && v instanceof String || k.equals("summary_last") && v instanceof Long
                    || k.equals("summary_count") && v instanceof Integer || k.startsWith("asked:") && v instanceof Boolean
                    || k.equals("summary_held") && v instanceof Set
                    || k.startsWith("use:") && v instanceof Integer) // how often each app is opened stays on this phone
                keep.put(k, v);
        }
        return keep;
    }

    /** Widget ids to free once a restore is saved: folder widgets and classic blocks, not the clock nor the List stack. */
    public static List<Integer> widgetsToFree(List<Integer> folderWidgetIds, List<String> blockIds, String stack) {
        List<Integer> free = new ArrayList<>(folderWidgetIds);
        List<String> stackIds = List.of(stack.split(","));
        for (String id : blockIds)
            if (!id.equals("clock") && !stackIds.contains(id))
                try { free.add(Integer.parseInt(id)); } catch (NumberFormatException notAWidget) { }
        return free;
    }

    /** Written to a backup file: only what a restore would read back, so this phone's bookkeeping stays home. */
    public static boolean travels(String k) { return restorable(k); }

    private Backup() {}
}
