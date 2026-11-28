package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.Optional;

/** The Play Store categories and their built-in glyph (issue 087). */
public class AppCategoryTest {
    public static void testFortyNineCategoriesEachWithAGlyphOfTheRealSet() {
        eq(49, AppCategory.values().length, "the Play Store's categories today");
        IconSet set = IconSetTest.load("src/main/assets/icons.bin");
        for (AppCategory c : AppCategory.values())
            yes(set.id(c.glyph()).isPresent(), c + " glyph is in the built-in set: " + c.glyph());
    }

    public static void testGlyphChoices() {
        eq("emoji_classical_building", AppCategory.FINANCE.glyph(), "decided by the user");
        eq("emoji_dress", AppCategory.BEAUTY.glyph(), "decided by the user");
        eq("emoji_newspaper", AppCategory.NEWS_AND_MAGAZINES.glyph(), "news");
        eq("games", AppCategory.GAME_PUZZLE.glyph(), "the 17 games share one glyph");
        eq("games", AppCategory.GAME_WORD.glyph(), "the 17 games share one glyph");
    }

    public static void testManifestCategory() {
        eq(Optional.of(AppCategory.NEWS_AND_MAGAZINES), AppCategory.fromManifest(5), "CATEGORY_NEWS");
        eq("games", AppCategory.fromManifest(0).orElseThrow().glyph(), "CATEGORY_GAME");
        eq(Optional.of(AppCategory.TOOLS), AppCategory.fromManifest(8), "CATEGORY_ACCESSIBILITY");
        eq(Optional.empty(), AppCategory.fromManifest(-1), "CATEGORY_UNDEFINED");
        eq(Optional.empty(), AppCategory.fromManifest(99), "a category from a newer Android");
    }
}
