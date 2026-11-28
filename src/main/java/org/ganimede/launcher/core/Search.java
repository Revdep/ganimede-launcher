package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** The app search: how well a name matches what's typed, the A–Z sections, and the web engines. */
public final class Search {
    /** Word start (Launcher3's breaks): after a non-letter/digit, lower to upper case, and letter to digit either way. */
    private static boolean wordStart(String s, int i) {
        if (i == 0) return true;
        char c = s.charAt(i), p = s.charAt(i - 1);
        if (!Character.isLetterOrDigit(c)) return false;
        return !Character.isLetterOrDigit(p) || (Character.isUpperCase(c) && !Character.isUpperCase(p))
                || Character.isDigit(c) != Character.isDigit(p);
    }

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}");

    /** Without accents. Pure ASCII has none and is returned as is: rank runs this on every name at each keystroke. */
    private static String plain(String s) {
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) > 0x7f) return MARKS.matcher(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)).replaceAll("");
        return s;
    }

    /** What's typed, without accents, extra spaces or spaces around it. */
    private static String typed(String query) {
        return plain(query).trim().replaceAll("\\s+", " ");
    }

    /** The name begins with the typed text, ignoring case and accents. */
    public static boolean startsWith(String label, String query) {
        return beginsWith(plain(label), typed(query));
    }

    private static boolean beginsWith(String plainLabel, String typedQuery) {
        return !typedQuery.isEmpty() && plainLabel.regionMatches(true, 0, typedQuery, 0, typedQuery.length());
    }

    /**
     * Search score, the Launcher3 way: every typed word must begin a different word of the name, in any order; no
     * letters mid-word or scattered, case and accents ignored. 2: the name begins with the text; 1: a match; 0: none.
     */
    public static int score(String label, String query) {
        return scoreTyped(label, typed(query));
    }

    /** As score, for a query already run through typed(). */
    private static int scoreTyped(String label, String q) {
        String l = plain(label);
        if (q.isEmpty()) return 0;
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < l.length(); i++) if (wordStart(l, i)) starts.add(i);
        String[] words = q.split(" ");
        // Longest first: two typed words that fit one start are prefixes of each other, and the longer fits fewer
        // starts, so it picks first and any start it takes would serve the shorter one just as well.
        java.util.Arrays.sort(words, java.util.Comparator.comparingInt(String::length).reversed());
        for (String w : words) {
            int j = 0;
            while (j < starts.size() && !l.regionMatches(true, starts.get(j), w, 0, w.length())) j++;
            if (j == starts.size()) return 0;
            starts.remove(j); // each word of the name begins one typed word only
        }
        return beginsWith(l, q) ? 2 : 1;
    }

    /** A contact stays when the typed words each begin a different word of its name and other entries (email, company, nickname) taken together. */
    public static boolean contactMatches(String name, List<String> others, String query) {
        StringBuilder all = new StringBuilder(name);
        for (String o : others) if (o != null) all.append(' ').append(o);
        return score(all.toString(), query) > 0;
    }

    /** The items whose name matches, best score first; ties go to the one opened most. Each scored once. */
    public static <T> List<T> rank(List<T> items, Function<T, String> name, ToIntFunction<T> uses, String query) {
        Map<T, Integer> scores = new HashMap<>();
        List<T> found = new ArrayList<>();
        String q = typed(query); // once, not per name
        for (T it : items) {
            int s = scoreTyped(name.apply(it), q);
            if (s > 0) { found.add(it); scores.put(it, s); }
        }
        found.sort(best(scores, uses));
        return found;
    }

    /** Items found some other way too (another language, their app's name): best name match first, each scored once. */
    public static <T> void sortByScore(List<T> items, Function<T, String> name, String query) {
        Map<T, Integer> scores = new HashMap<>();
        String q = typed(query);
        for (T it : items) scores.put(it, scoreTyped(name.apply(it), q));
        items.sort(best(scores, it -> 0));
    }

    private static <T> java.util.Comparator<T> best(Map<T, Integer> scores, ToIntFunction<T> uses) {
        return (a, b) -> !scores.get(a).equals(scores.get(b)) ? Integer.compare(scores.get(b), scores.get(a))
                : Integer.compare(uses.applyAsInt(b), uses.applyAsInt(a));
    }

    /** Contacts shown in the search: the first 5, or all of them after "Show all", which follows when some are left. */
    public static int shownContacts(int found, boolean all) {
        return all ? found : Math.min(found, 5);
    }

    /** A search for a phone number: only digits, spaces, dashes and plus, at least 3 digits; anything else is a name. */
    public static boolean isNumberQuery(String query) {
        return query.matches("[0-9 +\\-]*") && digits(query).length() >= 3;
    }

    /** The number holds the typed digits, whatever the spaces, dashes or international prefix (+39, 0039) around them. */
    public static boolean numberMatches(String number, String query) {
        if (number == null) return false;
        String saved = digits(number), typed = digits(query), q = query.trim();
        if (saved.contains(typed)) return true;
        if (!q.startsWith("+") && !q.startsWith("00")) return false;
        // 0039 typed, +39 saved (its plus is gone from the digits): the saved number begins with what follows the 00
        if (q.startsWith("00") && typed.length() >= 5 && saved.startsWith(typed.substring(2))) return true;
        // typed with a prefix the saved number may lack: the country code is 1 to 3 digits, the rest must still be 3+
        // and begin the saved number (its leading zeros dropped), not sit anywhere in it
        String local = saved.replaceFirst("^0+", "");
        int from = q.startsWith("+") ? 0 : 2;
        for (int cc = 1; cc <= 3; cc++) {
            String rest = typed.substring(Math.min(typed.length(), from + cc));
            if (rest.length() >= 3 && local.startsWith(rest)) return true;
        }
        return false;
    }

    private static final java.util.regex.Pattern NON_DIGITS = java.util.regex.Pattern.compile("[^0-9]");

    public static String digits(String s) {
        return NON_DIGITS.matcher(s).replaceAll("");
    }

    /** Section letter for the A–Z list. */
    public static String letter(String label) {
        if (label == null || label.isEmpty()) return "#";
        char ch = Character.toUpperCase(java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD).charAt(0));
        return ch >= 'A' && ch <= 'Z' ? String.valueOf(ch) : "#";
    }

    /** The alphabet's letters for these names: each section letter once, "#" first. */
    public static java.util.SortedSet<String> sections(java.util.Collection<String> names) {
        java.util.SortedSet<String> out = new java.util.TreeSet<>();
        for (String n : names) out.add(letter(n));
        return out;
    }

    /** The A–Z list: by section letter ("#" first); with smart sorting the most used first within a letter; then by name. */
    public static <T> List<T> order(List<T> items, Function<T, String> name, ToIntFunction<T> uses, boolean smart) {
        Map<T, String> names = new HashMap<>(), letters = new HashMap<>();
        Map<T, Integer> used = new HashMap<>();
        for (T it : items) { // read once, not on every comparison
            names.put(it, name.apply(it));
            letters.put(it, letter(names.get(it)));
            used.put(it, smart ? uses.applyAsInt(it) : 0);
        }
        List<T> v = new ArrayList<>(items);
        v.sort((a, b) -> {
            int c = letters.get(a).compareTo(letters.get(b));
            if (c != 0) return c;
            if (!used.get(a).equals(used.get(b))) return Integer.compare(used.get(b), used.get(a));
            return names.get(a).compareToIgnoreCase(names.get(b));
        });
        return v;
    }

    /** Web search engines without Google: name, then the address the query is appended to. */
    public static final String[][] ENGINES = {
            {"DuckDuckGo", "https://duckduckgo.com/?q="},
            {"Brave Search", "https://search.brave.com/search?q="},
            {"Startpage", "https://www.startpage.com/do/search?q="},
            {"Kagi", "https://kagi.com/search?q="},
            {"Ecosia", "https://www.ecosia.org/search?q="},
            {"Qwant", "https://www.qwant.com/?q="},
            {"Bing", "https://www.bing.com/search?q="},
            {"Google", "https://www.google.com/search?q="}, // only opens the browser: Ganimede itself never goes online
            {"Perplexity", "https://www.perplexity.ai/search?q="}};

    public static String[] engine(String name) {
        for (String[] e : ENGINES) if (e[0].equals(name)) return e;
        return ENGINES[0];
    }

    private Search() {}
}
