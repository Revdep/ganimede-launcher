package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Every English text the screens look up through T / ui.tr / ui.f has an Italian entry (read from the sources). */
public class TranslationsTest {
    private static String read(Path p) throws IOException { return new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8); } // Files.readString is not in the android.jar the tests compile against

    private static final Path MAIN = Path.of("src/main/java/org/ganimede/launcher");
    private static final Pattern PART = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** One literal, or several joined by "+", not continued by a computed part. */
    private static final String LIT = "((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+\\s*)*\"(?:[^\"\\\\]|\\\\.)*\")(?!\\s*\\+\\s*[^\"\\s])";
    private static final Pattern USED = Pattern.compile("(?:\\bT\\.[tf]\\([^,()]+(?:\\(\\))?,|\\.(?:tr|f)\\()\\s*" + LIT);
    private static final Pattern CALL = Pattern.compile("\\.(?:tr|f)\\(");
    /** A text that is one branch of a ternary ("c ? \"a\" : c2 ? \"b\" : \"c\""). */
    private static final Pattern BRANCH = Pattern.compile("[?:]\\s*" + LIT + "\\s*(?=[:)])");
    private static final Pattern KEY = Pattern.compile("\\bit\\(\\s*" + LIT + "\\s*,");

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static Set<String> scan(Pattern p, String... files) throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> all = Files.list(MAIN)) {
            for (Path f : (Iterable<Path>) all.filter(x -> x.toString().endsWith(".java"))::iterator) {
                if (files.length > 0 && !Set.of(files).contains(f.getFileName().toString())) continue;
                Matcher m = p.matcher(read(f));
                while (m.find()) {
                    StringBuilder text = new StringBuilder();
                    Matcher part = PART.matcher(m.group(1));
                    while (part.find()) text.append(unescape(part.group(1)));
                    out.add(text.toString());
                }
            }
        }
        return out;
    }

    /** The ternary branches inside the arguments of ui.tr(...) / ui.f(...), whatever the nesting. */
    private static Set<String> branches() throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> all = Files.list(MAIN)) {
            for (Path f : (Iterable<Path>) all.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String src = read(f);
                Matcher call = CALL.matcher(src);
                while (call.find()) {
                    int depth = 1, end = call.end();
                    for (; end < src.length() && depth > 0; end++) depth += src.charAt(end) == '(' ? 1 : src.charAt(end) == ')' ? -1 : 0;
                    Matcher b = BRANCH.matcher(src.substring(call.end() - 1, end));
                    while (b.find()) {
                        StringBuilder text = new StringBuilder();
                        Matcher part = PART.matcher(b.group(1));
                        while (part.find()) text.append(unescape(part.group(1)));
                        out.add(text.toString());
                    }
                }
            }
        }
        return out;
    }

    /** Names Android shows in its own screens (Accessibility, Recents): the product name, or a translated string. */
    public static void testManifestLabelsAreTranslated() throws IOException {
        String manifest = read(Path.of("src/main/AndroidManifest.xml"));
        Set<String> en = resources("values"), it = resources("values-it"), wrong = new TreeSet<>();
        Matcher m = Pattern.compile("android:(?:label|description)=\"([^\"]*)\"").matcher(manifest);
        while (m.find()) {
            String v = m.group(1);
            if (v.startsWith("@string/") ? !en.contains(v.substring(8)) || !it.contains(v.substring(8))
                    : !Set.of("Ganimede", "Ganimede Launcher").contains(v)) wrong.add(v);
        }
        eq(Set.of(), wrong, "manifest labels that are fixed English text or lack an Italian string");
        Set<String> untranslated = new TreeSet<>(en);
        untranslated.removeAll(it);
        eq(Set.of(), untranslated, "strings.xml entries without an Italian one");
    }

    private static Set<String> resources(String dir) throws IOException {
        Set<String> names = new TreeSet<>();
        Matcher m = Pattern.compile("<string name=\"([^\"]+)\"").matcher(read(Path.of("src/main/res", dir, "strings.xml")));
        while (m.find()) names.add(m.group(1));
        return names;
    }

    public static void testEveryUsedEnglishTextHasItalian() throws IOException {
        Set<String> have = scan(KEY, "T.java", "ItSettings.java");
        yes(have.size() > 300, "the translation tables were found");
        Set<String> missing = scan(USED);
        missing.addAll(branches());
        missing.removeAll(have);
        eq(Set.of(), missing, "English texts without an Italian entry");
    }
}
