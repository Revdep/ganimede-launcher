package org.ganimede.launcher;

import org.ganimede.launcher.core.Wx;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads upcoming events from the phone's own calendars (any account, no Google needed). */
final class Agenda {
    static final class Event {
        long id, begin, end;
        boolean allDay;
        int color;
        String title, where;
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Calendars on the phone: id → name. */
    static Map<Long, String> calendars(Context c) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (!allowed(c)) return out;
        try (Cursor k = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME},
                CalendarContract.Calendars.VISIBLE + "=1", null, null)) {
            while (k != null && k.moveToNext()) out.put(k.getLong(0), k.getString(1));
        } catch (Exception ignored) {
            // No calendar provider on this phone.
        }
        return out;
    }

    /** Events from now for the next `days`, skipping calendars the person filtered out. */
    static List<Event> upcoming(Context c, int days, Set<String> hiddenCalendars) {
        List<Event> out = new ArrayList<>();
        if (!allowed(c)) return out;
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 86_400_000L); // all-day events start at UTC midnight
        ContentUris.appendId(b, now + days * 86_400_000L);
        try (Cursor k = c.getContentResolver().query(b.build(), new String[]{
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_ID}, CalendarContract.Instances.VISIBLE + "=1", null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            while (k != null && k.moveToNext()) {
                if (hiddenCalendars.contains(String.valueOf(k.getLong(7)))) continue;
                Event e = new Event();
                e.id = k.getLong(0);
                e.begin = k.getLong(1);
                e.end = k.getLong(2);
                e.allDay = k.getInt(3) == 1;
                if (e.allDay) { e.begin = Wx.allDayToLocal(e.begin); e.end = Wx.allDayToLocal(e.end); } // stored at UTC midnight
                if (e.end <= now) continue;
                e.color = k.getInt(4) | 0xFF000000;
                e.title = k.getString(5) == null || k.getString(5).isEmpty() ? T.t(c, "(No title)") : k.getString(5);
                e.where = k.getString(6);
                out.add(e);
            }
        } catch (Exception ignored) {
            // Provider refused: show nothing rather than crash the home screen.
        }
        return out;
    }

    private Agenda() {}
}
