package org.ganimede.launcher;

import org.ganimede.launcher.core.Backup;
import org.ganimede.launcher.core.Safe;
import org.ganimede.launcher.core.Wx;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Keeps the latest weather as one small JSON (temperatures in °C).
 * Ganimede never goes online: the weather arrives from Breezy Weather through Android's internal broadcast
 * (the open Gadgetbridge weather format), each time Breezy refreshes.
 */
final class WeatherStore {
    static final String KEY = Backup.WEATHER;
    static final String BREEZY = "Breezy Weather";

    static JSONObject current(SharedPreferences p) {
        try {
            return new JSONObject(p.getString(KEY, ""));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Breezy Weather's own screen, or null when it isn't installed. */
    static Intent breezy(Context c) {
        for (String p : Wx.BREEZY_PACKAGES) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) return i;
        }
        return null;
    }

    /** Gadgetbridge WeatherSpec JSON: temperatures in Kelvin, OpenWeatherMap condition codes. */
    static void saveBreezy(Context c, String json) throws JSONException {
        JSONObject in = new JSONObject(json);
        JSONObject o = new JSONObject();
        o.put("src", BREEZY);
        // Shown on screen: short, printable text only. Temperatures outside 150–350 K are not weather.
        o.put("place", Safe.printable(in.optString("location", ""), 48));
        double now = in.getDouble("currentTemp");
        if (!Wx.plausibleKelvin(now)) throw new JSONException("temperature out of range");
        o.put("t", Wx.kelvinToC(now));
        o.put("k", Wx.owmKind(in.optInt("currentConditionCode", 800)));
        o.put("hum", in.optInt("currentHumidity", -1));
        o.put("wind", in.optDouble("windSpeed", -1));
        JSONArray days = new JSONArray();
        days.put(day(kelvin(in.optDouble("todayMaxTemp", now)), kelvin(in.optDouble("todayMinTemp", now)), o.getInt("k")));
        JSONArray f = in.optJSONArray("forecasts");
        for (int i = 0; f != null && i < f.length() && i < 10; i++) {
            JSONObject d = f.optJSONObject(i);
            try { // one broken day is left out; the rest of the forecast stays
                days.put(day(kelvin(d.getDouble("maxTemp")), kelvin(d.getDouble("minTemp")), Wx.owmKind(d.optInt("conditionCode", 800))));
            } catch (Exception skip) {
                days.put(JSONObject.NULL);
            }
        }
        o.put("days", days);
        // Newer Breezy/Gadgetbridge payloads also carry an hourly list (timestamp in seconds, temp in Kelvin).
        JSONArray hIn = in.optJSONArray("hourly"), hours = new JSONArray();
        for (int i = 0; hIn != null && i < hIn.length() && hours.length() < 24; i++) {
            JSONObject h = hIn.optJSONObject(i);
            if (h == null || !h.has("temp")) continue;
            long ts = h.optLong("timestamp") * 1000L;
            if (!Wx.hourStillAhead(ts, System.currentTimeMillis())) continue;
            try { // one broken hour is left out
                hours.put(new JSONObject().put("ts", ts).put("t", kelvin(h.getDouble("temp")))
                        .put("k", Wx.owmKind(h.optInt("conditionCode", 800))).put("pp", h.optInt("precipProbability", -1)));
            } catch (Exception skip) {
                // left out
            }
        }
        o.put("hours", hours);
        o.put("time", System.currentTimeMillis());
        Prefs.of(c).edit().putString(KEY, o.toString()).apply();
    }

    /** Kelvin to °C, refusing what can't be weather (the same 150–350 K as the current temperature). */
    private static double kelvin(double k) throws JSONException {
        if (!Wx.plausibleKelvin(k)) throw new JSONException("temperature out of range");
        return Wx.kelvinToC(k);
    }

    private static JSONObject day(double max, double min, int kind) throws JSONException {
        return new JSONObject().put("max", max).put("min", min).put("k", kind);
    }

    private WeatherStore() {}
}
