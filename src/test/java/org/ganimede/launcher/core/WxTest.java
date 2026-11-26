package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

/** Weather codes, words and dates. */
public class WxTest {
    public static void testRules() {
        eq("13°", Wx.temp(Wx.kelvinToC(285.65), false), "celsius");
        eq("32°", Wx.temp(0, true), "fahrenheit");
        eq("four", Words.hour(4), "hour");
        eq("fifteen", Words.minutes(15), "minutes");
        eq("oh five", Words.minutes(5), "minutes below ten");
        eq("forty-two", Words.number(42), "number");
        eq("o'clock", Words.minutes(0), "full hour");
        eq("midnight", Words.hour(0), "hour 0");
        eq("ventuno", Words.itNumber(21), "Italian 21");
        eq("trentotto", Words.itNumber(38), "Italian 38");
        eq("quarantatré", Words.itNumber(43), "Italian 43");
        eq("quattro", Words.italian(4, 15)[1], "Italian hour");
        eq("e quindici", Words.italian(4, 15)[2], "Italian minutes");
        eq("è l'", Words.italian(1, 0)[0], "Italian one o'clock");
    }

    public static void testOwmCodesMapToTheirKind() {
        int[][] cases = {{199, 3}, {200, 8}, {299, 8}, {300, 5}, {399, 5}, {400, 3}, {500, 6}, {501, 6}, {510, 6}, {511, 7}, {512, 6},
                {599, 6}, {600, 7}, {699, 7}, {700, 4}, {799, 4}, {800, 0}, {801, 1}, {802, 2}, {803, 3}, {804, 3}};
        for (int[] c : cases) eq(c[1], Wx.owmKind(c[0]), "OpenWeatherMap " + c[0]);
    }

    /** Runs a body with the default time zone changed, and puts it back even when the body throws. */
    private static void in(String zone, Runnable body) {
        java.util.TimeZone was = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
            body.run();
        } finally {
            java.util.TimeZone.setDefault(was);
        }
    }

    public static void testAllDayEventStartsAtLocalMidnightInAnyZone() {
        // Stored at UTC midnight of 21 Nov 2026: local midnight of 21 Nov, in any time zone.
        for (String tz : new String[]{"America/Los_Angeles", "Europe/Rome", "Pacific/Auckland"})
            in(tz, () -> {
                java.util.Calendar l = java.util.Calendar.getInstance();
                l.setTimeInMillis(Wx.allDayToLocal(1795219200000L)); // 2026-11-21T00:00Z
                eq(21, l.get(java.util.Calendar.DAY_OF_MONTH), tz + " day");
                eq(0, l.get(java.util.Calendar.HOUR_OF_DAY), tz + " hour");
            });
    }

    private static long rome(int month, int day, int h) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(2026, month, day, h, 0, 0);
        return c.getTimeInMillis();
    }

    public static void testDaysBetweenAcrossDst() {
        in("Europe/Rome", () -> {
            // Clocks go forward on 2026-03-29 (a 23-hour day) and back on 2026-10-25 (25 hours).
            eq(2, Wx.daysBetween(rome(java.util.Calendar.MARCH, 28, 0), rome(java.util.Calendar.MARCH, 30, 0)), "over the spring change");
            eq(2, Wx.daysBetween(rome(java.util.Calendar.OCTOBER, 24, 0), rome(java.util.Calendar.OCTOBER, 26, 0)), "over the autumn change");
            eq(1, Wx.daysBetween(rome(java.util.Calendar.MARCH, 29, 0), rome(java.util.Calendar.MARCH, 29, 23) + 3_600_000L), "the 23-hour day");
            eq(2, Wx.dayShift(rome(java.util.Calendar.OCTOBER, 24, 20), rome(java.util.Calendar.OCTOBER, 26, 8)), "forecast shift over the change");
        });
    }

    private static long at(int day, int h) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(2026, java.util.Calendar.MARCH, day, h, 0, 0);
        return c.getTimeInMillis();
    }

    public static void testDaysCountCalendarDaysNotHours() {
        org.ganimede.launcher.Expect.eq(1, Wx.daysBetween(at(5, 23), at(6, 1)), "two hours apart, next day");
        org.ganimede.launcher.Expect.eq(0, Wx.daysBetween(at(5, 1), at(5, 23)), "same day");
        org.ganimede.launcher.Expect.eq(-2, Wx.daysBetween(at(7, 9), at(5, 9)), "backwards");
    }

    public static void testWeatherDayShiftIsTodaysIndexInItsDays() {
        org.ganimede.launcher.Expect.eq(2, Wx.dayShift(at(3, 20), at(5, 8)), "received two days ago");
        org.ganimede.launcher.Expect.eq(0, Wx.dayShift(at(6, 8), at(5, 8)), "a clock set back never goes negative");
    }

    public static void testOnlyBreezyItselfMaySendWeather() {
        org.ganimede.launcher.Expect.yes(Wx.isBreezy("org.breezyweather") && Wx.isBreezy("org.breezyweather.fdroid"), "both builds");
        org.ganimede.launcher.Expect.yes(!Wx.isBreezy("org.breezyweather.evil") && !Wx.isBreezy("org.breezyweatherx")
                && !Wx.isBreezy(null), "a look-alike name");
    }

    public static void testWeatherKindOutOfRangeIsClamped() {
        org.ganimede.launcher.Expect.eq(Wx.SYM[0], Wx.sym(-4), "below the table");
        org.ganimede.launcher.Expect.eq(Wx.SYM[Wx.SYM.length - 1], Wx.sym(99), "above the table");
        org.ganimede.launcher.Expect.eq(Wx.TXT[3], Wx.txt(3), "a valid kind");
    }

    public static void testTemperaturesOutside150To350KAreNotWeather() {
        org.ganimede.launcher.Expect.yes(Wx.plausibleKelvin(150) && Wx.plausibleKelvin(293) && Wx.plausibleKelvin(350), "150 to 350 K");
        org.ganimede.launcher.Expect.yes(!Wx.plausibleKelvin(149.9) && !Wx.plausibleKelvin(350.1) && !Wx.plausibleKelvin(Double.NaN), "outside");
    }

    public static void testHoursOlderThanOneHourAreLeftOut() {
        long now = at(5, 12), hour = 3_600_000L;
        org.ganimede.launcher.Expect.yes(Wx.hourStillAhead(now - hour, now) && Wx.hourStillAhead(now + hour, now), "an hour ago is kept");
        org.ganimede.launcher.Expect.yes(!Wx.hourStillAhead(now - hour - 1, now), "just over an hour ago");
    }

    public static void testStaleAfterThreeHours() {
        long now = at(5, 12), hour = 3_600_000L;
        org.ganimede.launcher.Expect.yes(!Wx.stale(now - 3 * hour, now), "exactly three hours is still fresh");
        org.ganimede.launcher.Expect.yes(Wx.stale(now - 3 * hour - 1, now), "just over three hours");
    }

    public static void testAllDayEventShowsOnlyOnItsOwnDay() {
        long now = at(5, 12), hour = 3_600_000L;
        org.ganimede.launcher.Expect.yes(!Wx.showsNow(true, now + 2 * hour, now), "an all-day event of tomorrow");
        org.ganimede.launcher.Expect.yes(Wx.showsNow(true, now - 3 * hour, now), "an all-day event of today");
        org.ganimede.launcher.Expect.yes(Wx.showsNow(false, now + 3 * hour, now), "a timed event later today");
    }

    public static void testForecastShowsOnlyHoursStillAhead() {
        long now = at(5, 15), hour = 3_600_000L;
        org.ganimede.launcher.Expect.eq(java.util.List.of(2, 3, 4),
                Wx.hoursAhead(new long[]{now - 7 * hour, now - 2 * hour, now - hour, now, now + hour}, now),
                "data from eight, opened at three: seven and two hours ago are out, one hour ago stays");
    }
}
