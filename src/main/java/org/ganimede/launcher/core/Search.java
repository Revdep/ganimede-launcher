package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** The app search: how well a name matches what's typed, the A–Z sections, and the web engines. */
public final class Search {
    /** Word start: a letter/digit after a non-letter/digit, or an uppercase after a lowercase. */
    private static boolean wordStart(String s, int i) {
        if (i == 0) return true;
        char c = s.charAt(i), p = s.charAt(i - 1);
        if (!Character.isLetterOrDigit(c)) return false;
        return !Character.isLetterOrDigit(p) || (Character.isUpperCase(c) && !Character.isUpperCase(p));
    }

    private static String plain(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Search score. Every typed letter must
     * appear in order in the name, word starts preferred. Each match scores 16 at the first position; at a later word
     * start 16 minus the words passed (at least 8); inside a word 8 minus the words passed (at least 1). The longest run
     * of consecutive matches counts double. 0 means no match.
     */
    public static int score(String label, String query) {
        String l = plain(label);
        String q = plain(query).toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        String low = l.toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty() || low.length() < q.length()) return 0;
        int[] pos = new int[q.length()];
        int from = 0;
        for (int k = 0; k < q.length(); k++) {
            char c = q.charAt(k);
            int any = -1, start = -1;
            for (int i = from; i < low.length(); i++) {
                if (low.charAt(i) != c) continue;
                if (any < 0) any = i;
                if (wordStart(l, i)) { start = i; break; }
            }
            // Keep a run going when the letter follows the previous match; otherwise prefer the next word start.
            int p = k > 0 && any == pos[k - 1] + 1 ? any : start >= 0 ? start : any;
            if (p < 0) return 0;
            pos[k] = p;
            from = p + 1;
        }
        int[] pts = new int[q.length()];
        int words = 0;
        for (int i = 0, k = 0; i < l.length() && k < q.length(); i++) {
            if (i > 0 && wordStart(l, i)) words++;
            if (i != pos[k]) continue;
            if (i == 0) pts[k] = 16;
            else if (wordStart(l, i)) pts[k] = Math.max(8, 16 - words);
            else pts[k] = Math.max(1, 8 - words);
            k++;
        }
        int bestStart = 0, bestLen = 1;
        for (int k = 1, run = 1; k < pos.length; k++) {
            run = pos[k] == pos[k - 1] + 1 ? run + 1 : 1;
            if (run > bestLen) { bestLen = run; bestStart = k - run + 1; }
        }
        int sum = 0;
        for (int k = 0; k < pts.length; k++) sum += bestLen > 1 && k >= bestStart && k < bestStart + bestLen ? pts[k] * 2 : pts[k];
        return sum;
    }

    /** The items whose name matches, best score first; ties go to the one opened most. Each scored once. */
    public static <T> List<T> rank(List<T> items, Function<T, String> name, ToIntFunction<T> uses, String query) {
        Map<T, Integer> scores = new HashMap<>();
        List<T> found = new ArrayList<>();
        for (T it : items) {
            int s = score(name.apply(it), query);
            if (s > 0) { found.add(it); scores.put(it, s); }
        }
        found.sort(best(scores, uses));
        return found;
    }

    /** Items found some other way too (another language, their app's name): best name match first, each scored once. */
    public static <T> void sortByScore(List<T> items, Function<T, String> name, String query) {
        Map<T, Integer> scores = new HashMap<>();
        for (T it : items) scores.put(it, score(name.apply(it), query));
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

    /** Section letter for the A–Z list. */
    public static String letter(String label) {
        if (label == null || label.isEmpty()) return "#";
        char ch = Character.toUpperCase(java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD).charAt(0));
        return ch >= 'A' && ch <= 'Z' ? String.valueOf(ch) : "#";
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
