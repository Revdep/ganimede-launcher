package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

/** The List column's paddings and start margins, one rule for the home and the Settings preview. */
public class HomeColumnTest {
    public static void testListColumnFromTheStoredOffsets() {
        // alpha_offset, home_bottom, clock_offset, line_offset, apps_offset -> end padding, bottom, clock, line, apps start (dp)
        eq(new int[]{72, 64, 40, 40, 40}, HomeColumn.list(0, 64, 40, 40, 40), "defaults");
        eq(new int[]{84, 112, 8, 12, 32}, HomeColumn.list(12, 112, 8, 12, 4), "alphabet moved in, apps clear of the edge strip");
    }
}
