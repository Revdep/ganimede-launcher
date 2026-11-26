package org.ganimede.launcher.core;

/** Weather codes and units, shared by both weather sources. Plain Java so it can be checked off-device. */
public final class Wx {
    public static final String[] SYM = {"☀", "🌤", "⛅", "☁", "🌫", "🌦", "🌧", "❄", "⛈"};
    public static final String[] TXT = {"Clear", "Mostly clear", "Partly cloudy", "Cloudy", "Fog", "Drizzle", "Rain", "Snow", "Thunderstorm"};
    /** Breezy Weather's two builds, the only apps whose weather is taken. */
    public static final java.util.List<String> BREEZY_PACKAGES = java.util.List.of("org.breezyweather", "org.breezyweather.fdroid");

    public static boolean isBreezy(String pkg) {
        return pkg != null && BREEZY_PACKAGES.contains(pkg);
    }

    /** The symbol and the words for a weather kind; an unknown kind (bad stored data) shows the nearest one. */
    public static String sym(int kind) {
        return SYM[Math.max(0, Math.min(SYM.length - 1, kind))];
    }

    public static String txt(int kind) {
        return TXT[Math.max(0, Math.min(TXT.length - 1, kind))];
    }

    /** OpenWeatherMap-style code (what Breezy Weather sends through Gadgetbridge) to a kind. */
    public static int owmKind(int c) {
        if (c >= 200 && c < 300) return 8;
        if (c >= 300 && c < 400) return 5;
        if (c == 511) return 7;
        if (c >= 500 && c < 600) return 6;
        if (c >= 600 && c < 700) return 7;
        if (c >= 700 && c < 800) return 4;
        if (c == 800) return 0;
        if (c == 801) return 1;
        if (c == 802) return 2;
        return 3;
    }

    public static double kelvinToC(double k) {
        return k - 273.15;
    }

    /** Anything outside 150-350 K is not weather. */
    public static boolean plausibleKelvin(double k) {
        return k >= 150 && k <= 350;
    }

    /** An hourly entry (ms) is kept unless it is more than an hour old. */
    public static boolean hourStillAhead(long ts, long now) {
        return ts >= now - 3_600_000L;
    }

    /** The positions of the hourly entries (ms) worth showing now. */
    public static java.util.List<Integer> hoursAhead(long[] ts, long now) {
        java.util.List<Integer> ahead = new java.util.ArrayList<>();
        for (int i = 0; i < ts.length; i++) if (hourStillAhead(ts[i], now)) ahead.add(i);
        return ahead;
    }

    /** A reading received (ms) more than three hours ago is old. */
    public static boolean stale(long received, long now) {
        return now - received > 3 * 3_600_000L;
    }

    /** Celsius in, rounded display text out. */
    public static String temp(double c, boolean fahrenheit) {
        return Math.round(fahrenheit ? c * 9 / 5 + 32 : c) + "°";
    }

    /**
     * Calendars store all-day events at midnight UTC of their date: the same date's midnight here, so "today" and
     * "tomorrow" follow the phone's own days whatever its time zone.
     */
    public static long allDayToLocal(long utcMidnight) {
        java.util.Calendar u = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utcMidnight);
        java.util.Calendar l = java.util.Calendar.getInstance();
        l.clear();
        l.set(u.get(java.util.Calendar.YEAR), u.get(java.util.Calendar.MONTH), u.get(java.util.Calendar.DAY_OF_MONTH));
        return l.getTimeInMillis();
    }

    /** Calendar days from one moment to another, in the phone's time zone (rounded: a DST day has 23 or 25 hours). */
    public static int daysBetween(long from, long to) {
        return (int) Math.round((startOfDay(to) - startOfDay(from)) / 86_400_000.0);
    }

    /** Index of today in a forecast's days, received at that moment (days[0] is the day it came). */
    public static int dayShift(long received, long now) {
        return Math.max(0, daysBetween(received, now));
    }

    private static long startOfDay(long t) {
        java.util.Calendar d = java.util.Calendar.getInstance();
        d.setTimeInMillis(t);
        d.set(java.util.Calendar.HOUR_OF_DAY, 0);
        d.set(java.util.Calendar.MINUTE, 0);
        d.set(java.util.Calendar.SECOND, 0);
        d.set(java.util.Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    /** An all-day event is in local days: before its day starts it is tomorrow's, not the next thing to show. */
    public static boolean showsNow(boolean allDay, long begin, long now) {
        return !allDay || begin <= now;
    }

    private Wx() {}
}
