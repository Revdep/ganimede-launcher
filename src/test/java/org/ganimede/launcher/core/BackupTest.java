package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupTest {
    public static void testTypesByKeyAndPrefix() {
        eq("b", Backup.typeOf("drawer"), "a switch");
        eq("set", Backup.typeOf("hidden"), "a set");
        eq("set", Backup.typeOf("unsuggested"), "apps explicitly removed from favourites travel as a set");
        eq("b", Backup.typeOf("notif_preview"), "the preview under favourites travels with the backup");
        eq("s", Backup.typeOf("rn:com.a/.Main"), "a renamed app");
        eq("s", Backup.typeOf("icon:f:12"), "a picked icon");
        eq("s", Backup.typeOf("folder:12"), "a folder");
        eq("s", Backup.typeOf("blocks"), "portable block geometry");
        eq("s", Backup.typeOf("themed_shape"), "the tinted icon plate travels with the theme");
        eq("b", Backup.typeOf("drawer_follow"), "drawer follows home");
        eq("s", Backup.typeOf("drawer_font"), "independent drawer font");
        eq("i", Backup.typeOf("drawer_icon"), "independent drawer icon size");
        eq("s", Backup.typeOf("drawer_icon_shape"), "an old backup's drawer icon shape still comes back");
        eq("s", Backup.typeOf("icon_shape"), "an old backup's icon shape still comes back");
        for (String k : new String[]{"icon_glyph", "icon_container", "icon_fill", "icon_color"}) {
            eq("s", Backup.typeOf(k), k + " travels with the backup");
            eq("s", Backup.typeOf("drawer_" + k), "drawer_" + k + " travels with the backup");
        }
        for (String k : IconLook.LEGACY) yes(Backup.restorable(k) && !Backup.travels(k), k + " is read from an old backup, never written to a new one");
        eq("i", Backup.typeOf("drawer_label_scale"), "independent drawer label size");
        eq("i", Backup.typeOf("folder_bg"), "default folder background colour");
        eq("i", Backup.typeOf("folder_alpha"), "default folder background opacity");
        eq("i", Backup.typeOf("folder_cols"), "default folder columns");
        eq("i", Backup.typeOf("line_offset"), "line horizontal position");
        eq("i", Backup.typeOf("list_line_y"), "line vertical position");
        eq(null, Backup.typeOf("stack"), "this phone's widgets never come back");
        yes(Backup.restorable("grid") && !Backup.restorable("weather_json"), "only known keys come back");
    }

    public static void testValuesMustHaveTheirType() {
        yes(Backup.fits("drawer", true), "boolean");
        yes(!Backup.fits("drawer", "true"), "text for a switch");
        yes(Backup.fits("fab_y", 0.5f) && !Backup.fits("fab_y", 0.5d), "only a float");
        yes(!Backup.fits("fab_y", Float.NaN) && !Backup.fits("fab_y", Float.POSITIVE_INFINITY), "no NaN or infinity");
        yes(Backup.fits("hidden", Set.of("a")), "a set");
        yes(!Backup.fits("nope", 1), "unknown key");
    }

    public static void testNumbersStayInTheirRange() {
        yes(Backup.fits("grid_cols", 3) && Backup.fits("grid_cols", 8), "the edges");
        yes(!Backup.fits("grid_cols", 2) && !Backup.fits("grid_cols", 9), "past the edges");
        yes(Backup.fits("accent", -4_000_000), "numbers without a range");
        yes(Backup.fits("fav_gap", 0) && Backup.fits("fav_gap", 32) && !Backup.fits("fav_gap", 33), "spacing between favourites");
        yes(Backup.fits("drawer_label_scale", 60) && !Backup.fits("drawer_label_scale", 151), "drawer name size");
        yes(Backup.fits("drawer_icon", 32) && Backup.fits("drawer_icon", 80) && !Backup.fits("drawer_icon", 81), "drawer icon size");
    }

    /** Every number the home screen sizes things by: key, lowest and highest value kept. */
    private static final Object[][] LIMITS = {
        {"grid_cols", 3, 8}, {"grid_rows", 2, 10}, {"dock_count", 0, 8}, {"drawer_cols", 2, 8}, {"drawer_icon", 32, 80},
        {"drawer_label_scale", 60, 150}, {"grid_pages", 1, 100}, {"grid_icon", 16, 160}, {"icon_size", 16, 160},
        {"alpha_size", 8, 40}, {"label_scale", 25, 300}, {"list_clock_size", 20, 300}, {"list_widget_h", 0, 2000},
        {"list_apps_h", 5, 100}, {"dim", 0, 95}, {"dim_home", 0, 95}, {"dock_alpha", 0, 100}, {"alpha_offset", -20, 40},
        {"alpha_y", -160, 160}, {"clock_offset", 4, 240}, {"line_offset", 4, 240}, {"apps_offset", 4, 240},
        {"home_bottom", 4, 420}, {"classic_pad_l", 4, 80}, {"classic_pad_r", 4, 80}, {"classic_pad_t", 4, 200},
        {"classic_pad_b", 4, 200}, {"fav_gap", 0, 32}, {"folder_alpha", 0, 100}, {"folder_cols", 1, 5},
        {"classic_grid_x", -400, 400}, {"classic_dock_x", -400, 400}, {"list_widget_x", -400, 400},
        {"classic_grid_y", -600, 600}, {"classic_dock_y", -600, 600}, {"list_clock_y", -600, 600},
        {"list_line_y", -600, 600}, {"list_widget_y", -600, 600}, {"list_apps_y", -600, 600}};

    /** An Integer for a number setting; a table row of another type would make restore throw ClassCastException. */
    public static void testEveryLimitedSettingIsANumberWithItsOwnLimits() {
        for (Object[] row : LIMITS) {
            String k = (String) row[0];
            int lo = (Integer) row[1], hi = (Integer) row[2];
            eq("i", Backup.typeOf(k), k + " is read as a number");
            yes(Backup.fits(k, lo) && Backup.fits(k, hi), k + " keeps both edges " + lo + ".." + hi);
            yes(!Backup.fits(k, lo - 1) && !Backup.fits(k, hi + 1), k + " refuses just outside " + lo + ".." + hi);
        }
    }

    public static void testFolderLookNumbersStayInTheirRanges() {
        yes(Backup.fits("folder_bg", 0) && Backup.fits("folder_bg", 0xFFE01010),
                "automatic and explicit folder colours are integers");
        yes(!Backup.fits("folder_bg", "red"), "a folder colour is not text");
        yes(Backup.fits("folder_alpha", 0) && Backup.fits("folder_alpha", 100)
                && !Backup.fits("folder_alpha", -1) && !Backup.fits("folder_alpha", 101),
                "folder opacity is from zero to one hundred percent");
        yes(Backup.fits("folder_cols", 1) && Backup.fits("folder_cols", 5)
                && !Backup.fits("folder_cols", 0) && !Backup.fits("folder_cols", 6),
                "a folder has from one to five columns");
    }

    public static void testLaunchCountsStayOnThePhone() {
        yes(!Backup.travels("use:com.a/.Main") && !Backup.restorable("use:com.a/.Main"), "how often each app is opened never leaves the phone");
        yes(Backup.kept(java.util.Map.of("use:com.a/.Main", 7)).get("use:com.a/.Main").equals(7), "a restore keeps this phone's own counts");
    }

    public static void testLayoutNumbersStayInTheRangesSettingsOffer() {
        yes(Backup.fits("classic_grid_x", -400) && Backup.fits("list_widget_x", 400), "free sideways moves");
        yes(!Backup.fits("classic_dock_x", 401) && !Backup.fits("list_apps_y", -601), "past them");
        eq(null, Backup.typeOf("classic_clock_x"), "old clock positions are ignored on restore");
        eq(null, Backup.typeOf("classic_widget_y"), "old widget positions are ignored on restore");
        yes(Backup.fits("alpha_y", 160) && !Backup.fits("alpha_y", 161), "the alphabet moves less");
        yes(Backup.fits("alpha_offset", -20) && !Backup.fits("alpha_offset", 41), "the alphabet's edge");
        yes(Backup.fits("clock_offset", 4) && !Backup.fits("apps_offset", 3) && !Backup.fits("apps_offset", 241), "from the edge");
        yes(Backup.fits("line_offset", 4) && Backup.fits("line_offset", 240)
                && !Backup.fits("line_offset", 3) && !Backup.fits("line_offset", 241), "line from the edge");
        yes(Backup.fits("list_line_y", -600) && Backup.fits("list_line_y", 600)
                && !Backup.fits("list_line_y", -601) && !Backup.fits("list_line_y", 601), "line vertically");
        yes(Backup.fits("classic_pad_l", 80) && !Backup.fits("classic_pad_r", 81) && !Backup.fits("classic_pad_t", 201)
                && !Backup.fits("classic_pad_b", 3), "edges");
        yes(Backup.fits("home_bottom", 420) && !Backup.fits("home_bottom", 421), "the lifted home");
        yes(Backup.fits("alpha_tint", 0xFFE01010) && Backup.fits("alpha_tint", 0), "any colour");
    }

    public static void testTextIsAtMost64KB() {
        yes(Backup.fits("fav", "x".repeat(64 * 1024)), "the limit");
        yes(!Backup.fits("fav", "x".repeat(64 * 1024 + 1)) && !Backup.fits("rn:a/.B", "x".repeat(70_000)), "past it");
    }

    public static void testIconModelKeepsListedValues() {
        yes(!Backup.fits("icon_glyph", "cutout"), "Cutout was removed: a theme from someone else can't bring it");
        yes(Backup.fits("icon_container", "circle") && Backup.fits("icon_fill", "soft") && Backup.fits("icon_color", "text"), "listed values");
        yes(!Backup.fits("icon_color", "soft") && !Backup.fits("drawer_icon_container", "hexagon"), "unlisted values");
    }

    public static void testModeIsListOrClassic() {
        yes(Backup.fits("mode", "list") && Backup.fits("mode", "classic") && !Backup.fits("mode", "grid"), "two modes");
    }

    public static void testFabIconIsOneOfTheFourSupportedValues() {
        yes(Backup.fits("fab_icon", "default") && Backup.fits("fab_icon", "gray")
                && Backup.fits("fab_icon", "colour") && Backup.fits("fab_icon", "custom"),
                "all four button icons can be restored");
        yes(!Backup.fits("fab_icon", "") && !Backup.fits("fab_icon", "grey")
                && !Backup.fits("fab_icon", "something-new"),
                "unsupported button icons are rejected");
    }

    public static void testGridCellsAreNumbersInside100LargestPages() {
        yes(Backup.cellsFit(List.of("0", "7999").iterator()), "first and last cell");
        yes(!Backup.cellsFit(List.of("8000").iterator()), "past 100 pages of 10 x 8");
        yes(!Backup.cellsFit(List.of("-1").iterator()), "negative");
        yes(!Backup.cellsFit(List.of("x").iterator()), "not a number");
    }

    public static void testThisPhonesOwnStateSurvivesARestore() {
        Map<String, Object> old = new HashMap<>();
        old.put("stack", "7,9");
        old.put("wx", "{\"t\":20}");
        old.put("summary_last", 123L);
        old.put("summary_count", 4);
        old.put("asked:notif", true);
        old.put("fav", "a/.A");
        old.put("folder:1", "{}");
        old.put("onboarded", true);
        eq(Map.of("stack", "7,9", "wx", "{\"t\":20}", "summary_last", 123L, "summary_count", 4, "asked:notif", true),
                Backup.kept(old), "List widgets, weather, summary timing and asked permissions stay; the rest comes from the file");
        Map<String, Object> wrong = new HashMap<>();
        wrong.put("stack", 7);
        wrong.put("wx", true);
        wrong.put("summary_last", 1);
        wrong.put("summary_count", 1L);
        wrong.put("asked:notif", "yes");
        eq(Map.of(), Backup.kept(wrong), "a value of the wrong type is not carried over");
    }

    public static void testWidgetIdsToFreeAreFoldersAndBlocksNotTheStack() {
        eq(List.of(11, 12, 30), Backup.widgetsToFree(List.of(11, 12), List.of("clock", "30", "7", "", "x"), "7,9"),
                "folder widgets and classic blocks; not the clock, not the List stack, not a non-number");
        eq(List.of(5), Backup.widgetsToFree(List.of(), List.of("5"), ""), "no List stack");
        eq(List.of(), Backup.widgetsToFree(List.of(), List.of("9"), "7,9"), "a block of the List stack stays bound");
    }

    public static void testListWidgetsAndWeatherStayOffTheBackupAndFoldersAreReplaced() {
        yes(!Backup.travels("stack") && !Backup.travels("wx"), "List widget ids and weather are not written to a backup");
        yes(Backup.travels("folder:3") && Backup.travels("blocks"),
                "folders are written (restore drops their widget ids), blocks too (only the clock)");
        yes(!Backup.kept(Map.of("folder:3", "{\"widgets\":[4]}")).containsKey("folder:3"), "this phone's folder is replaced by the file's");
        eq(List.of(4), Backup.widgetsToFree(List.of(4), List.of(), ""), "and its widgets are freed");
    }

    public static void testHeldNotificationsStayOnThisPhone() {
        yes(!Backup.travels("summary_held"), "held keys are this phone's: not written to a backup");
        eq(Map.of("summary_held", java.util.Set.of("k|5")), Backup.kept(Map.of("summary_held", java.util.Set.of("k|5"))), "and kept through a restore");
        eq(Map.of(), Backup.kept(Map.of("summary_held", "k|5")), "only as a set");
    }

    public static void testFabYOutOfRangeRejected() {
        yes(Backup.fits("fab_y", 0f) && Backup.fits("fab_y", 0.5f) && Backup.fits("fab_y", 1f), "0 to 1 of the screen height");
        yes(!Backup.fits("fab_y", 1e9f) && !Backup.fits("fab_y", -0.1f) && !Backup.fits("fab_y", 1.01f), "off the screen");
    }

    public static void testIconKeyOnlyForIconPacks() {
        yes(Backup.fits("icon:f:12", "com.pack.icons|ic_phone_2"), "pack|drawable");
        yes(!Backup.fits("icon:f:12", "ic_phone") && !Backup.fits("icon:f:12", "|x") && !Backup.fits("icon:f:12", "com.a|"),
                "both halves are needed");
        yes(!Backup.fits("icon:f:12", "../x|y") && !Backup.fits("icon:f:12", "com.a|b|c") && !Backup.fits("icon:f:12", "com.a|b c"),
                "a package and a drawable name, nothing else");
    }

    /** Issue 081: an icon picked from the built-in set ("builtin:arcticons|name") travels in the backup like any other. */
    public static void testBuiltinIconPickRestored() {
        yes(Backup.fits("icon:f:12", "builtin:arcticons|okay"), "built-in set|name");
        yes(!Backup.fits("icon:f:12", "builtin:other|okay") && !Backup.fits("icon:f:12", "builtin:arcticons|a b"),
                "only the built-in set's own id, and a plain name");
    }

    public static void testOnlyRestorableSettingsAreWritten() {
        yes(!Backup.travels("asked:android.permission.X") && !Backup.travels("pending_widget_1") && !Backup.travels("icon_pack_loaded")
                && !Backup.travels("return_to_settings") && !Backup.travels("summary_last") && !Backup.travels("summary_count"),
                "this phone's bookkeeping stays home");
        yes(Backup.travels("fab_y"), "settings travel");
    }

    /** Every name of the real built-in set restores as a picked icon: a name outside \w would be dropped from a backup. */
    public static void testEveryBuiltInIconNameRestores() {
        for (String n : IconSetTest.load("src/main/assets/icons.bin").names())
            yes(Backup.fits("icon:f:12", "builtin:arcticons|" + n), "built-in icon name: " + n);
    }

    /** Issue 087: "Category icons" is a switch that travels with the backup. */
    public static void testCategoryIconsSwitchTravels() {
        eq("b", Backup.typeOf("icon_category"), "a switch");
        yes(Backup.travels("icon_category") && Backup.fits("icon_category", false) && !Backup.fits("icon_category", "x"),
                "false restores, a non-boolean is dropped");
        yes(Preferences.UNDO_KEYS.contains("icon_category"), "Customize's Undo takes it back like icon_pack");
    }
}
