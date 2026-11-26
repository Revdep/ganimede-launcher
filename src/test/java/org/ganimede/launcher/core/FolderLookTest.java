package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

public class FolderLookTest {
    public static void testFolderValuesWinAndMissingValuesUseDefaults() {
        eq(new FolderLook(0xCC112233, 4, Colors.on(0xFF112233)), FolderLook.resolve(0xFF112233, null, 4, 0xFF445566, 80, 2, true),
                "folder colour and columns win while missing opacity uses its default");
        eq(new FolderLook(0x66445566, 2, Colors.on(0xFF445566)), FolderLook.resolve(null, 40, null, 0xFF445566, 80, 2, true),
                "folder opacity wins while missing colour and columns use their defaults");
    }

    public static void testZeroDefaultColourUsesTheAutomaticLightOrDarkCard() {
        eq(new FolderLook(0xFF201A33, 3, Colors.WHITE), FolderLook.resolve(null, null, null, 0, 100, 3, true),
                "light text gets today's dark automatic card");
        eq(new FolderLook(0xFFFFF8F0, 3, Colors.INK), FolderLook.resolve(null, null, null, 0, 100, 3, false),
                "dark text gets today's light automatic card");
    }

    public static void testUntouchedFolderKeepsLegacyDarkCardWithLightText() {
        eq(new FolderLook(0xF0201A33, FolderLook.DEFAULT_COLUMNS, Colors.WHITE),
                FolderLook.resolve(null, null, null, FolderLook.DEFAULT_BACKGROUND, FolderLook.defaultAlpha(true),
                        FolderLook.DEFAULT_COLUMNS, true),
                "an untouched folder with light text keeps its exact old background");
    }

    public static void testUntouchedFolderKeepsLegacyLightCardWithDarkText() {
        eq(new FolderLook(0xF5FFF8F0, FolderLook.DEFAULT_COLUMNS, Colors.INK),
                FolderLook.resolve(null, null, null, FolderLook.DEFAULT_BACKGROUND, FolderLook.defaultAlpha(false),
                        FolderLook.DEFAULT_COLUMNS, false),
                "an untouched folder with dark text keeps its exact old background");
    }

    public static void testOpacityIsAppliedToTheResolvedColour() {
        eq(new FolderLook(0x66123456, 2, Colors.on(0xAA123456)), FolderLook.resolve(null, 40, null, 0xAA123456, 80, 2, true),
                "40 percent replaces the colour's old alpha");
        eq(new FolderLook(0x00123456, 2, Colors.on(0xFF123456)), FolderLook.resolve(null, 0, null, 0xFF123456, 80, 2, true),
                "zero percent is transparent");
    }

    public static void testColumnsAreClampedFromOneToFive() {
        eq(1, FolderLook.resolve(null, null, 0, 0, 95, 3, true).columns(), "a folder cannot have fewer than one column");
        eq(5, FolderLook.resolve(null, null, 6, 0, 95, 3, true).columns(), "a folder cannot have more than five columns");
        eq(1, FolderLook.resolve(null, null, null, 0, 95, -2, true).columns(), "the default is clamped at the lower edge too");
        eq(5, FolderLook.resolve(null, null, null, 0, 95, 8, true).columns(), "the default is clamped at the upper edge too");
    }

    public static void testWhiteBackgroundGetsDarkText() {
        eq(Colors.INK, FolderLook.resolve(0xFFF0F0F0, null, null, 0, 100, 1, true).text(),
                "a light folder background gets dark text even when the home text is light");
    }

    public static void testDarkAutomaticCardGetsLightText() {
        eq(Colors.WHITE, FolderLook.resolve(null, null, null, 0, 100, 1, true).text(),
                "the dark automatic card (light home text) gets light text");
    }

    public static void testLightAutomaticCardGetsDarkText() {
        eq(Colors.INK, FolderLook.resolve(null, null, null, 0, 100, 1, false).text(),
                "the light automatic card (dark home text) gets dark text");
    }

    // popupWidth(columns, cell, padding, screenMax) = min(columns * cell + 2 * padding, screenMax), all in px
    public static void testWidthIsColumnsTimesCellPlusBothPaddings() {
        eq(104, FolderLook.popupWidth(1, 80, 12, 0, 1000), "1 column: 1 x 80 + 2 x 12");
        eq(184, FolderLook.popupWidth(2, 80, 12, 0, 1000), "2 columns: 2 x 80 + 2 x 12");
        eq(424, FolderLook.popupWidth(5, 80, 12, 0, 1000), "5 columns: 5 x 80 + 2 x 12");
    }

    public static void testWidthNeverExceedsTheScreenLimit() {
        eq(300, FolderLook.popupWidth(5, 80, 12, 0, 300), "5 columns would be 424, capped to the 300 the screen allows");
        eq(184, FolderLook.popupWidth(2, 80, 12, 0, 184), "exactly the limit stays as is");
    }

    public static void testWidgetMinimumWidensANarrowCardButNeverPastTheScreen() {
        eq(240, FolderLook.popupWidth(2, 80, 12, 240, 1000), "2 columns (184) with a widget: the 240 minimum wins");
        eq(200, FolderLook.popupWidth(2, 80, 12, 240, 200), "a screen below the minimum wins");
    }

    public static void testNoScreenWidthYieldsZero() {
        eq(0, FolderLook.popupWidth(2, 80, 12, 0, 0), "a screen not laid out (width 0) gives width 0, never negative");
    }
}
