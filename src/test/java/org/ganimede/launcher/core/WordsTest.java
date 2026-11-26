package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.Arrays;

/** The "Words" clock: the time spelled out in English and Italian. */
public class WordsTest {
    public static void testEnglishHoursAndMinutes() {
        eq("four", Words.hour(4), "an hour");
        eq("twelve", Words.hour(12), "the last hour on a 12 h clock");
        eq("o'clock", Words.minutes(0), "on the hour");
        eq("oh five", Words.minutes(5), "a single digit is spoken with an oh");
        eq("ten", Words.minutes(10), "ten has no oh");
        eq("fifteen", Words.minutes(15), "a quarter past");
        eq("twenty", Words.minutes(20), "a round tens");
        eq("forty-five", Words.minutes(45), "tens and ones are hyphenated");
    }

    public static void testItalianRoundsToFiveMinutePhrases() {
        eq(line("sono le", "quattro", "in punto"), Words.italian(4, 0), "on the hour");
        eq(line("sono le", "quattro", "e cinque"), Words.italian(4, 5), "five past");
        eq(line("sono le", "quattro", "e quindici"), Words.italian(4, 15), "a quarter past");
        eq(line("sono le", "quattro", "e trenta"), Words.italian(4, 30), "half past");
        eq(line("sono le", "quattro", "e quarantacinque"), Words.italian(4, 45), "a quarter to");
    }

    public static void testMidnightNoonAndOneOClock() {
        eq("midnight", Words.hour(0), "hour 0 in English");
        eq("twelve", Words.hour(12), "noon in English");
        eq("one", Words.hour(1), "one o'clock in English");
        eq(line("è", "mezzanotte", "in punto"), Words.italian(0, 0), "midnight in Italian is singular");
        eq(line("è l'", "una", "e dieci"), Words.italian(1, 10), "one o'clock in Italian is singular");
        eq(line("sono le", "dodici", "in punto"), Words.italian(12, 0), "noon in Italian");
    }

    public static void testNumbersUpToFiftyNine() {
        String[] en = {"nineteen", "twenty", "twenty-one", "thirty", "forty-two", "fifty", "fifty-nine"};
        String[] it = {"diciannove", "venti", "ventuno", "trenta", "quarantadue", "cinquanta", "cinquantanove"};
        int[] n = {19, 20, 21, 30, 42, 50, 59};
        for (int i = 0; i < n.length; i++) {
            eq(en[i], Words.number(n[i]), "English " + n[i]);
            eq(it[i], Words.itNumber(n[i]), "Italian " + n[i]);
        }
        eq("ventotto", Words.itNumber(28), "the vowel before otto drops");
        eq("trentuno", Words.itNumber(31), "the vowel before uno drops");
        eq("ventitré", Words.itNumber(23), "tre takes an accent after a tens");
        eq("tre", Words.itNumber(3), "a bare tre has none");
    }

    private static String[] line(String... s) {
        return Arrays.copyOf(s, s.length);
    }
}
