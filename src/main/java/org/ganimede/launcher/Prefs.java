package org.ganimede.launcher;

import org.ganimede.launcher.core.IconLook;
import org.ganimede.launcher.core.Preferences;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Every setting of the app lives in this one file. */
final class Prefs {
    static SharedPreferences of(Context c) {
        SharedPreferences p = c.getSharedPreferences("ganimede", Context.MODE_PRIVATE);
        prepare(p);
        return p;
    }

    /** The "24-hour clock" setting (the phone's own by default): the one rule every clock and time text follows. */
    static boolean clock24(Context c, SharedPreferences p) {
        return p.getBoolean("clock24", android.text.format.DateFormat.is24HourFormat(c));
    }

    /** A time of day in the chosen language and the 12/24-hour rule above. */
    static String time(Context c, long ms) {
        java.util.Locale l = T.locale(c);
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(l, clock24(c, of(c)) ? "Hm" : "hm");
        return new java.text.SimpleDateFormat(pattern, l).format(new java.util.Date(ms));
    }

    /** Applies missing preference additions (and the old icon shapes as the icon model) before either activity reads the layout. */
    static void prepare(SharedPreferences p) {
        boolean legacyIcons = false;
        for (String k : IconLook.LEGACY) legacyIcons |= p.contains(k);
        if (p.getBoolean(Preferences.LINE_MIGRATED, false) && !legacyIcons) return; // the common case: no full copy of the settings
        Map<String, ?> all = p.getAll();
        Map<String, Object> additions = new HashMap<>(Preferences.lineMigration(all));
        additions.putAll(IconLook.migrate(all));
        if (additions.isEmpty()) return;
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, Object> addition : additions.entrySet()) put(e, addition.getKey(), addition.getValue());
        e.apply();
    }

    /** Stores a value under its own type (as read from a backup or a theme); null removes the setting. */
    @SuppressWarnings("unchecked")
    static void put(SharedPreferences.Editor e, String k, Object v) {
        if (v == null) e.remove(k);
        else if (v instanceof Set) e.putStringSet(k, (Set<String>) v);
        else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) e.putInt(k, (Integer) v);
        else if (v instanceof Long) e.putLong(k, (Long) v);
        else if (v instanceof Float) e.putFloat(k, (Float) v);
        else e.putString(k, (String) v);
    }

    private Prefs() {}
}
