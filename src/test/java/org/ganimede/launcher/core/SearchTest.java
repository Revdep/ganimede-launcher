package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.List;
import java.util.Map;

public class SearchTest {
    public static void testScore() {
        yes(Search.score("Calculator", "zz") == 0 && Search.score("Camera", "cmr") > 0, "letters in order");
        yes(Search.score("Calculator", "calc") > Search.score("Contacts Calendar", "calc"), "the first word wins");
        yes(Search.score("Organic Maps", "om") > Search.score("Zoom", "om"), "word starts win");
        eq(16, Search.score("Calculator", "c"), "the first letter");
        yes(Search.score("Météo", "meteo") > 0, "accents ignored");
    }

    public static void testLetterAndEngine() {
        yes(Search.letter("Éclair").equals("E") && Search.letter("7zip").equals("#") && Search.letter("files").equals("F"), "sections");
        eq("DuckDuckGo", Search.engine("nope")[0], "an unknown engine falls back to the first");
    }

    public static void testSortByScoreKeepsEveryItemBestNameFirst() {
        java.util.ArrayList<String> hits = new java.util.ArrayList<>(List.of("Zoom", "Inbox", "Camera", "Calendar"));
        Search.sortByScore(hits, s -> s, "ca");
        eq(List.of("Camera", "Calendar", "Zoom", "Inbox"), hits, "chosen for another reason: kept, after the matches, in order");
    }

    public static void testRankByScoreThenMostUsed() {
        Map<String, Integer> uses = Map.of("Camera", 1, "Calendar", 9, "Calculator", 5);
        List<String> apps = List.of("Zoom", "Camera", "Calendar", "Calculator", "Clock");
        eq(List.of("Calendar", "Calculator", "Camera"), Search.rank(apps, s -> s, s -> uses.getOrDefault(s, 0), "ca"),
                "same score: most used first; no match left out");
        eq(List.of("Clock", "Calendar", "Calculator"), Search.rank(apps, s -> s, s -> uses.getOrDefault(s, 0), "cl"),
                "a better score first, however little used");
    }

    public static void testRankHandlesExtremeUseCounts() {
        Map<String, Integer> uses = Map.of("Low", Integer.MIN_VALUE, "High", Integer.MAX_VALUE);
        eq(List.of("High", "Low"), Search.rank(List.of("Low", "High"), s -> "App", uses::get, "a"),
                "integer extremes stay in comparator order");
    }

    public static void testEmptyQueryScoresNothing() {
        eq(0, Search.score("Calculator", ""), "an empty query");
        eq(0, Search.score("Calculator", "   "), "a query of spaces only");
        eq(0, Search.score("", "a"), "an empty name");
        eq(0, Search.score("Ca", "cal"), "a query longer than the name");
        eq(List.of(), Search.rank(List.of("Camera"), s -> s, s -> 0, ""), "nothing is found for an empty query");
    }

    public static void testAccentsAndCaseAreIgnored() {
        eq(Search.score("Calculator", "calc"), Search.score("CALCULATOR", "CALC"), "case on both sides");
        eq(Search.score("Meteo", "meteo"), Search.score("Météo", "meteo"), "accent in the name");
        eq(Search.score("Meteo", "meteo"), Search.score("Meteo", "méteo"), "accent in the query");
        yes(Search.score("Caffè", "caffe") > 0, "a grave accent");
        eq(Search.score("Calc ulator", "calc"), Search.score("Calc ulator", "c a l c"), "spaces in the query are ignored");
        eq("E", Search.letter("élan"), "a lower-case accented first letter sorts under its base letter");
        eq("#", Search.letter(""), "an empty name sorts under #");
    }

    public static void testOrderIsLetterThenMostUsedThenName() {
        Map<String, Integer> uses = Map.of("Bank", 1, "Camera", 9, "Calendar", 2, "apple", 0, "7zip", 50, "Éclair", 3);
        List<String> apps = List.of("Camera", "Bank", "7zip", "Calendar", "Éclair", "apple");
        eq(List.of("7zip", "apple", "Bank", "Calendar", "Camera", "Éclair"), Search.order(apps, s -> s, uses::get, false),
                "not smart: by section letter (# first), then name ignoring case, use counts ignored");
        eq(List.of("7zip", "apple", "Bank", "Camera", "Calendar", "Éclair"), Search.order(apps, s -> s, uses::get, true),
                "smart: a letter's most used first (Camera before Calendar)");
        eq(List.of("Cab", "Cob"), Search.order(List.of("Cob", "Cab"), s -> s, s -> 4, true), "equal use: by name");
        eq(List.of("Cab", "Cob"), Search.order(List.of("Cob", "Cab"), s -> s, s -> 0, true), "unused: by name");
    }

    public static void testContactsShowAllAfterFive() {
        eq(5, Search.shownContacts(5, false), "5 found: all 5 shown, no Show all row");
        eq(5, Search.shownContacts(6, false), "6 found: 5 shown, then Show all (6)");
        eq(6, Search.shownContacts(6, true), "after Show all: every one");
        eq(2, Search.shownContacts(2, false), "fewer than 5: all");
    }

    public static void testScoresAreExact() {
        eq(16, Search.score("Calculator", "c"), "first letter");
        eq(8, Search.score("Calculator", "a"), "inside the first word");
        eq(80, Search.score("Calculator", "calc"), "a run of four doubles: 2 x (16 + 8 + 8 + 8)");
        eq(31, Search.score("Organic Maps", "om"), "first letter 16, second word start 15");
        eq(16, Search.score("Zoom", "om"), "inside a word: 8 + 8");
        eq(31, Search.score("Contacts Calendar", "cal"), "16 + 8 + 7: the l is inside the second word");
        eq(15, Search.score("One Two", "t"), "word start after one word passed");
        eq(8, Search.score("a b c d e f g h i j k", "k"), "word-start points never fall under 8");
        eq(1, Search.score("a b c d e f g h i j kz", "z"), "inside-word points never fall under 1");
    }
}
