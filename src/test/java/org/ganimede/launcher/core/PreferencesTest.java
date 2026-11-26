package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.Map;

public class PreferencesTest {
    public static void testFreshInstallOnlyRecordsTheMigration() {
        eq(Map.of(Preferences.LINE_MIGRATED, true), Preferences.lineMigration(Map.of()),
                "a fresh install has no clock positions to copy");
    }

    public static void testLinePositionStartsWhereTheClockWas() {
        eq(Map.of("line_offset", 76, "list_line_y", -28, Preferences.LINE_MIGRATED, true),
                Preferences.lineMigration(Map.of("clock_offset", 76, "list_clock_y", -28)),
                "an existing List layout keeps its clock and line together after the update");
    }

    public static void testLineMigrationNeverOverwritesItsOwnPosition() {
        eq(Map.of(Preferences.LINE_MIGRATED, true), Preferences.lineMigration(Map.of(
                        "clock_offset", 76, "list_clock_y", -28,
                        "line_offset", 120, "list_line_y", 44)),
                "a line already moved independently stays where it was");
        eq(Map.of("list_line_y", -28, Preferences.LINE_MIGRATED, true), Preferences.lineMigration(Map.of(
                        "clock_offset", 76, "list_clock_y", -28, "line_offset", 120)),
                "each missing line axis is migrated independently");
    }

    public static void testLineMigrationRunsOnlyOnce() {
        eq(Map.of(), Preferences.lineMigration(Map.of(
                        "clock_offset", 76, "list_clock_y", -28, Preferences.LINE_MIGRATED, true)),
                "resetting either line axis later is not mistaken for an old installation");
    }
}
