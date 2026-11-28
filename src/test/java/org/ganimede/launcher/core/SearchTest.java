package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.List;
import java.util.Map;

public class SearchTest {
    public static void testScore() {
        eq(0, Search.score("Calculator", "zz"), "letters not in the name");
        yes(Search.score("Calculator", "cal") > Search.score("Contacts Calendar", "cal"), "the name's start wins over a later word");
        eq(2, Search.score("Calculator", "c"), "the first letter is the name's start");
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
        eq(List.of("Calendar", "Calculator", "My Calendar"), Search.rank(List.of("My Calendar", "Calendar", "Calculator"), s -> s,
                s -> s.equals("My Calendar") ? 20 : uses.getOrDefault(s, 0), "cal"), "a better score first, however little used");
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
        eq(Search.score("Cafe", "cafe"), Search.score("Cafe\u0301", "cafe"), "an accent already split off the letter");
        yes(Search.score("Ølstue", "øl") > 0 && Search.score("日本語", "日") > 0, "letters with no accent to strip still match");
        eq(0, Search.score("Ølstue", "ol"), "a letter that is not an accented o is not an o");
        eq(Search.score("Calc ulator", "calc ul"), Search.score("Calc ulator", " calc   ul "), "extra spaces in the query are ignored");
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
        eq(2, Search.score("Calculator", "calc"), "the name begins with the text");
        eq(2, Search.score("Posta Italiana", "posta ita"), "the name begins with the whole text, spaces included");
        eq(1, Search.score("Organic Maps", "maps"), "a later word begins with the text");
        eq(1, Search.score("Posta Italiana", "ita pos"), "every typed word begins a word, not the name's start");
        eq(1, Search.score("a b c d e f g h i j k", "k"), "the same however many words come before");
        eq(0, Search.score("Calculator", "a"), "inside a word");
    }

    public static void testOnlyWordStartsMatch() {
        eq(List.of("Teams"), Search.rank(List.of("Teams", "Streamflix", "Steam", "Telepass", "Fotocamera"), s -> s, s -> 0, "tea"),
                "tea: only the name with a word starting tea");
    }

    public static void testScatteredLettersAndMidWordDoNotMatch() {
        eq(0, Search.score("Ente Auth", "tea"), "te inside Ente, a starting Auth");
        eq(0, Search.score("Steam", "tea"), "mid-word");
        eq(0, Search.score("Calendar", "cl"), "scattered letters");
        eq(0, Search.score("Organic Maps", "om"), "initials");
        eq(0, Search.score("Camera", "cmr"), "letters in order, not together");
    }

    public static void testTeamFindsTeamsAndTeamViewer() {
        Map<String, Integer> uses = Map.of("TeamViewer", 3, "Teams", 1);
        eq(List.of("TeamViewer", "Teams"), Search.rank(List.of("Teams", "TeamViewer", "Steam", "Telepass"), s -> s,
                s -> uses.getOrDefault(s, 0), "team"), "both start with team: most used first; Steam left out");
    }

    public static void testCamelCaseStartsAWord() {
        yes(Search.score("TeamViewer", "viewer") > 0, "Viewer starts after the lower-to-upper change");
    }

    public static void testEveryTypedWordStartsADifferentWord() {
        yes(Search.score("Posta Italiana", "pos ita") > 0, "in the name's order");
        yes(Search.score("Posta Italiana", "ita pos") > 0, "in any order");
        eq(0, Search.score("Posta Italiana", "pos pos"), "two typed words cannot share one word of the name");
        yes(Search.score("Poste Pay", "p pos") > 0, "the short p takes Pay, leaving Poste to pos");
    }

    public static void testAccentsAndCaseIgnored() {
        yes(Search.score("Café", "cafe") > 0, "accent in the name");
        yes(Search.score("CAFÉ", "café") > 0, "case and accent on both sides");
    }

    public static void testDigitBoundaryStartsAWord() {
        yes(Search.score("App2FA", "2fa") > 0, "a digit after a letter starts a word");
        yes(Search.score("App2FA", "fa") > 0, "a letter after a digit starts a word");
        eq(0, Search.score("App2FA", "p2"), "p is inside App");
    }

    public static void testNameStartFirstThenUsage() {
        Map<String, Integer> uses = Map.of("A B C Tea", 5, "Mio Tea", 1, "Tea Shop", 0);
        eq(List.of("Tea Shop", "A B C Tea", "Mio Tea"), Search.rank(List.of("A B C Tea", "Mio Tea", "Tea Shop"), s -> s, uses::get, "tea"),
                "the name starting with tea first; the others by use, however many words come before");
    }

    public static void testAlphabetLettersAreTheSectionsOfTheNamesInOrder() {
        eq(List.of("#", "A", "Z"), new java.util.ArrayList<>(Search.sections(List.of("Zz Prova", "alfa", "Àlpha", "9 Nove"))), "sorted, once each, # first");
    }

    public static void testContactStaysOnAnyEntryWithAWordStartingWithTheText() {
        java.util.List<String> other = java.util.List.of("anna@acme.com", "Acme Srl", "Boss");
        yes(Search.contactMatches("Zz Prova", other, "acme"), "company or email word");
        yes(Search.contactMatches("Zz Prova", other, "boss"), "nickname");
        yes(Search.contactMatches("Zz Prova", other, "pro"), "the name");
        yes(!Search.contactMatches("Zz Prova", other, "cme"), "letters mid-word stay out");
        yes(!Search.contactMatches("Zz Prova", java.util.List.of(), "rova"), "no other entries, mid-word name");
    }

    public static void testNumberQueryNeedsThreeDigitsAndNothingElse() {
        yes(Search.isNumberQuery("347 12"), "digits and a space");
        yes(Search.isNumberQuery("+39 347-1"), "plus and dash are allowed");
        yes(!Search.isNumberQuery("34"), "two digits are too few");
        yes(!Search.isNumberQuery("rossi 347"), "a name keeps the name search");
        yes(!Search.isNumberQuery(""), "empty");
    }

    public static void testNumberMatchesOnDigitsOnly() {
        yes(Search.numberMatches("+39 347 1234567", "347 12"), "spaces ignored");
        yes(Search.numberMatches("+39 347 1234567", "34712"), "same digits");
        yes(Search.numberMatches("0039 347-1234567", "39 347"), "international prefix");
        yes(Search.numberMatches("347 1234567", "+39 347 1234567"), "whole number typed with +39, saved without prefix");
        yes(Search.numberMatches("347 1234567", "0039 347-1234567"), "typed with 0039, saved without prefix");
        yes(Search.numberMatches("347 1234567", "+39 347"), "piece typed with the prefix, saved without");
        yes(Search.numberMatches("+39 347 1234567", "+39 347 1234567"), "both with the prefix");
        yes(Search.numberMatches("0039 347-1234567", "347 1234567"), "typed without, saved with 0039");
        yes(Search.numberMatches("+1 347 1234567", "+1 347 12"), "one-digit prefix");
        yes(Search.numberMatches("347 1234567", "+353 347 1234567"), "three-digit prefix");
        yes(!Search.numberMatches("347 1234567", "+39 348 1234567"), "prefix does not make other digits match");
        yes(Search.numberMatches("+39 347 1234567", "0039 347"), "typed with 0039, saved with +39");
        yes(Search.numberMatches("+39 347 1234567", "0039 347 1234567"), "whole number typed with 0039, saved with +39");
        yes(!Search.numberMatches("+39 347 1234567", "0039 348"), "0039 against +39: other digits stay out");
        yes(!Search.numberMatches("347 1234567", "+39 99"), "what is left of the prefix needs 3 digits");
        yes(!Search.numberMatches("02 5471 000", "+39 3471"), "after a prefix the rest starts the number, not any place in it");
        yes(!Search.numberMatches("02 5471 000", "0039 471"), "same with 0039");
        yes(Search.numberMatches("02 5471 000", "+39 254"), "the rest at the start of the number");
        yes(!Search.numberMatches("+39 347 1234567", "348"), "other digits");
        yes(!Search.numberMatches(null, "347"), "no number");
    }
}
