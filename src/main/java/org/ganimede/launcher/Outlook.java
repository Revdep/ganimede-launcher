package org.ganimede.launcher;

import org.ganimede.launcher.core.Wx;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.CalendarContract;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** What's ahead: the weather and next-event lines under the clock, the forecast and the agenda. */
final class Outlook {
    private static final int REQ_CAL = 13;

    private final Ui ui;
    private final Handler handler;
    private final TextView weather, event;
    private boolean eventReading, eventAgain;
    /** The calendar is read on one background thread: refreshes queue up there instead of piling up threads. */
    private static final ExecutorService CALENDAR = Executors.newSingleThreadExecutor();

    Outlook(Ui ui, Handler handler, TextView weather, TextView event) {
        this.ui = ui;
        this.handler = handler;
        this.weather = weather;
        this.event = event;
    }

    private boolean fahrenheit() {
        return ui.prefs.getBoolean("fahrenheit", false);
    }

    /** The line under the clock (shared with the settings' preview): the date unless the clock draws it, the battery. */
    static String dateLine(Context c, SharedPreferences p, boolean clockDrawsDate) {
        StringBuilder s = new StringBuilder();
        if (p.getBoolean("show_date", true) && !clockDrawsDate)
            s.append(new SimpleDateFormat("EEE, d MMM", T.locale(c)).format(new Date()));
        int pct = p.getBoolean("show_battery", false) ? battery(c) : 0;
        if (pct > 0) s.append(s.length() > 0 ? "  " : "").append(pct).append('%');
        return s.toString();
    }

    /** The battery charge in percent; 0 when the phone doesn't say. */
    static int battery(Context c) {
        try {
            return c.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (RuntimeException noBattery) {
            return 0;
        }
    }

    /** The weather next to the date (shared with the settings' preview), or a hint to set it up. */
    static String weatherText(Context c, JSONObject w, boolean fahrenheit) {
        return w == null ? "☁ " + T.t(c, "set weather") : Wx.sym(w.optInt("k", 3)) + " " + Wx.temp(w.optDouble("t"), fahrenheit);
    }

    void updateWeather() {
        JSONObject w = WeatherStore.current(ui.prefs);
        weather.setVisibility(ui.prefs.getBoolean("show_weather", true) ? View.VISIBLE : View.GONE);
        String t = weatherText(ui.a, w, fahrenheit());
        // Older than three hours: say when it's from, so an old reading isn't taken for now.
        if (w != null && Wx.stale(w.optLong("time"), System.currentTimeMillis()))
            t += " · " + Prefs.time(ui.a, w.optLong("time"));
        weather.setText(t);
        weather.setContentDescription(w == null ? null : ui.tr(Wx.txt(w.optInt("k", 3))) + ", " + t);
    }

    /** Index of today in the weather's "days" (it may have come days ago). */
    private static int dayShift(JSONObject w) {
        long now = System.currentTimeMillis();
        return Wx.dayShift(w.optLong("time", now), now);
    }

    /** Tap: the forecast (or Breezy Weather, if chosen in settings). */
    void onWeatherTap() {
        Intent b = WeatherStore.breezy(ui.a);
        if (b != null && "breezy".equals(ui.prefs.getString("wx_tap", "forecast"))) ui.open(b);
        else showForecast();
    }

    void showForecast() {
        JSONObject w = WeatherStore.current(ui.prefs);
        if (w == null) {
            ui.settings();
            return;
        }
        boolean f = fahrenheit();
        int k = w.optInt("k", 3);
        LinearLayout box = new LinearLayout(ui.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, ui.dp(8), 0, ui.dp(8)); // the dialog gives the sides
        TextView now = new TextView(ui.a);
        now.setTextSize(30);
        now.setText(Wx.sym(k) + " " + Wx.temp(w.optDouble("t"), f) + "  " + ui.tr(Wx.txt(k)));
        box.addView(now);
        StringBuilder sub = new StringBuilder();
        if (w.optInt("hum", -1) >= 0) sub.append(ui.f("Humidity %d%%", w.optInt("hum"))).append("   ");
        if (w.optDouble("wind", -1) >= 0) sub.append(ui.f("Wind %d km/h", Math.round(w.optDouble("wind"))));
        TextView st = new TextView(ui.a);
        st.setText(sub);
        st.setPaddingRelative(0, ui.dp(4), 0, ui.dp(10));
        box.addView(st);

        JSONArray hours = w.optJSONArray("hours");
        if (hours != null && hours.length() > 0) {
            HorizontalScrollView hs = new HorizontalScrollView(ui.a);
            LinearLayout strip = new LinearLayout(ui.a);
            SimpleDateFormat hf = new SimpleDateFormat(Prefs.clock24(ui.a, ui.prefs) ? "HH" : "ha", T.locale(ui.a));
            long[] stamps = new long[hours.length()];
            for (int i = 0; i < stamps.length; i++) stamps[i] = hours.optJSONObject(i).optLong("ts");
            for (int i : Wx.hoursAhead(stamps, System.currentTimeMillis())) {
                JSONObject h = hours.optJSONObject(i);
                TextView cell = new TextView(ui.a);
                cell.setGravity(Gravity.CENTER);
                cell.setPaddingRelative(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
                int pp = h.optInt("pp", -1);
                cell.setText(hf.format(new Date(h.optLong("ts"))) + "\n" + Wx.sym(h.optInt("k", 3)) + "\n"
                        + Wx.temp(h.optDouble("t"), f) + (pp > 0 ? "\n" + pp + "%" : ""));
                strip.addView(cell);
            }
            hs.addView(strip);
            box.addView(hs);
        }
        JSONArray days = w.optJSONArray("days");
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dayName = new SimpleDateFormat("EEE d", T.locale(ui.a));
        // days[0] is the day the weather was received: days before today are skipped.
        int today = dayShift(w);
        for (int i = today; days != null && i < days.length(); i++) {
            JSONObject d = days.optJSONObject(i);
            if (d == null) { c.add(Calendar.DAY_OF_MONTH, 1); continue; } // a day Breezy sent broken
            TextView t = new TextView(ui.a);
            t.setTextSize(16);
            t.setPaddingRelative(0, ui.dp(5), 0, ui.dp(5));
            t.setText((i == today ? ui.tr("Today") : dayName.format(c.getTime())) + "     " + Wx.sym(d.optInt("k", 3)) + "     "
                    + Wx.temp(d.optDouble("max"), f) + " / " + Wx.temp(d.optDouble("min"), f));
            box.addView(t);
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        TextView src = new TextView(ui.a);
        src.setText(ui.f("Source: %s · updated %s", w.optString("src"), Prefs.time(ui.a, w.optLong("time"))));
        src.setAlpha(0.7f);
        src.setPaddingRelative(0, ui.dp(10), 0, 0);
        box.addView(src);
        ScrollView sv = new ScrollView(ui.a);
        sv.addView(box);
        Ask d = ui.ask()
                .setTitle(w.optString("place", ui.tr("Weather")))
                .setView(sv)
                .setPositiveButton(ui.tr("Close"), null);
        Intent b = WeatherStore.breezy(ui.a);
        if (b != null) d.setNegativeButton(ui.tr("Breezy Weather"), (x, y) -> ui.open(b));
        d.show();
    }

    // ---- calendar

    /** The next event under the clock, read from the calendar on a background thread (the provider can be slow). */
    void updateEvent() {
        if (!ui.prefs.getBoolean("event_preview", true) || !Agenda.allowed(ui.a)) {
            eventAgain = false;
            event.setVisibility(View.GONE);
            return;
        }
        if (eventReading) { eventAgain = true; return; }
        eventReading = true;
        Set<String> hidden = ui.prefs.getStringSet("cal_hidden", Set.of());
        CALENDAR.execute(() -> {
            if (ui.a.isDestroyed()) return;
            List<Agenda.Event> next = Agenda.upcoming(ui.a, 1, hidden);
            handler.post(() -> {
                if (ui.a.isDestroyed()) return;
                eventReading = false;
                if (eventAgain) {
                    eventAgain = false;
                    updateEvent();
                } else if (ui.prefs.getBoolean("event_preview", true) && Agenda.allowed(ui.a)) {
                    showEvent(next);
                }
            });
        });
    }

    private void showEvent(List<Agenda.Event> next) {
        event.setVisibility(View.GONE);
        long now = System.currentTimeMillis();
        for (Agenda.Event e : next) {
            if (!Wx.showsNow(e.allDay, e.begin, now)) continue; // all-day events are in local days (Agenda): this one is tomorrow
            String when = e.allDay ? ui.tr("Today") : e.begin <= now ? ui.tr("Now") : Prefs.time(ui.a, e.begin);
            SpannableString s = new SpannableString("● " + when + "  " + e.title);
            s.setSpan(new ForegroundColorSpan(e.color), 0, 1, 0);
            event.setText(s);
            event.setVisibility(View.VISIBLE);
            return;
        }
    }

    /** Whether a tap on the date shows Ganimede's agenda (else the calendar app). */
    boolean opensAgenda() { return ui.prefs.getBoolean("agenda", true); }

    void onDateTap() {
        if (!opensAgenda()) {
            ui.open(calendarNow());
            return;
        }
        if (!Agenda.allowed(ui.a)) {
            ui.a.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL);
            return;
        }
        showAgenda();
    }

    void onPermissionResult(int req) {
        if (req == REQ_CAL && Agenda.allowed(ui.a)) { updateEvent(); showAgenda(); }
    }

    private static Intent calendarNow() {
        return new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                .appendPath(String.valueOf(System.currentTimeMillis())).build());
    }

    /** The agenda: the next two weeks of events, each day with its forecast. */
    void showAgenda() {
        Set<String> hidden = ui.prefs.getStringSet("cal_hidden", Set.of());
        CALENDAR.execute(() -> { // the calendar provider can be slow: never on the main thread
            if (ui.a.isDestroyed()) return;
            List<Agenda.Event> events = Agenda.upcoming(ui.a, 14, hidden);
            handler.post(() -> { if (!ui.a.isDestroyed()) showAgenda(events); });
        });
    }

    private void showAgenda(List<Agenda.Event> events) {
        LinearLayout box = new LinearLayout(ui.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(0, ui.dp(10), 0, ui.dp(10));
        JSONObject w = WeatherStore.current(ui.prefs);
        JSONArray days = w == null ? null : w.optJSONArray("days");
        int shift = w == null ? 0 : dayShift(w); // days[0] is the day the weather came
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE d MMMM", T.locale(ui.a));
        String lastDay = null;
        long now = System.currentTimeMillis();
        for (Agenda.Event e : events) {
            long start = Math.max(e.begin, now);
            String day = dayFmt.format(new Date(start));
            if (!day.equals(lastDay)) {
                lastDay = day;
                int off = Wx.daysBetween(now, start);
                TextView h = new TextView(ui.a);
                h.setTextSize(16);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setTextColor(ui.accent);
                h.setPaddingRelative(0, ui.dp(16), 0, ui.dp(4));
                String wx = "";
                int at = off + shift;
                if (days != null && off >= 0 && at < days.length() && days.optJSONObject(at) != null) {
                    JSONObject d = days.optJSONObject(at);
                    wx = "    " + Wx.sym(d.optInt("k", 3)) + " " + Wx.temp(d.optDouble("max"), fahrenheit())
                            + " / " + Wx.temp(d.optDouble("min"), fahrenheit());
                }
                h.setText((off == 0 ? ui.tr("Today") : off == 1 ? ui.tr("Tomorrow") : day) + wx);
                box.addView(h);
            }
            TextView t = new TextView(ui.a);
            t.setTextSize(16);
            String when = e.allDay ? ui.tr("All day") : Prefs.time(ui.a, e.begin)
                    + "–" + Prefs.time(ui.a, e.end);
            SpannableString s = new SpannableString("● " + when + "   " + e.title
                    + (e.where == null || e.where.isEmpty() ? "" : "\n    " + e.where));
            s.setSpan(new ForegroundColorSpan(e.color), 0, 1, 0);
            t.setText(s);
            t.setPaddingRelative(0, ui.dp(6), 0, ui.dp(6));
            t.setOnClickListener(v -> ui.open(new Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))));
            box.addView(t);
        }
        if (events.isEmpty()) {
            TextView t = new TextView(ui.a);
            t.setText(ui.tr("No events in the next two weeks."));
            t.setPaddingRelative(0, ui.dp(12), 0, ui.dp(12));
            box.addView(t);
        }
        ScrollView sv = new ScrollView(ui.a);
        sv.addView(box);
        ui.ask()
                .setTitle(ui.tr("Your agenda"))
                .setView(sv)
                .setPositiveButton(ui.tr("New event"), (d, x) -> ui.open(new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)))
                .setNeutralButton(ui.tr("Calendar"), (d, x) -> ui.open(calendarNow()))
                .setNegativeButton(ui.tr("Close"), null)
                .show();
    }
}
