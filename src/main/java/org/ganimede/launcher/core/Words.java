package org.ganimede.launcher.core;

/** The time spelled out for the "Words" clock ("it's / four / fifteen"). Plain Java so it can be checked off-device. */
public final class Words {
    private static final String[] ONES = {"o'clock", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty"};

    public static String hour(int h) {
        return h == 0 ? "midnight" : number(h);
    }

    public static String minutes(int m) {
        if (m == 0) return "o'clock";
        if (m < 10) return "oh " + ONES[m];
        return number(m);
    }

    public static String number(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : "-" + ONES[n % 10]);
    }

    private static final String[] IT_ONES = {"zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove",
            "dieci", "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove"};
    private static final String[] IT_TENS = {"", "", "venti", "trenta", "quaranta", "cinquanta"};

    /** Italian number words: "ventuno", "trentotto", "quarantatré". */
    public static String itNumber(int n) {
        if (n < 20) return IT_ONES[n];
        String tens = IT_TENS[n / 10];
        int u = n % 10;
        if (u == 0) return tens;
        if (u == 1 || u == 8) tens = tens.substring(0, tens.length() - 1);
        return tens + (u == 3 ? "tré" : IT_ONES[u]);
    }

    /** The three lines of the Words clock in Italian: "sono le" / "quattro" / "e quindici". */
    public static String[] italian(int h, int m) {
        String first = h == 1 ? "è l'" : h == 0 ? "è" : "sono le";
        String hour = h == 0 ? "mezzanotte" : h == 1 ? "una" : itNumber(h);
        return new String[]{first, hour, m == 0 ? "in punto" : "e " + itNumber(m)};
    }

    private Words() {}
}
