package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.near;
import static org.ganimede.launcher.Expect.yes;

/** The alphabet's wave under the finger. */
public class WaveTest {
    public static void testPullPeaksUnderTheFingerAndFadesAway() {
        eq(0f, Wave.pull(0, 10, 0, 0, 10, 100, 0), "no amplitude, no pull");
        float peak = Wave.pull(4, 10, 45, 0, 10, 100, 60), far = Wave.pull(0, 10, 45, 0, 10, 100, 60);
        near(60, peak, "peak under the finger");
        yes(far < peak && far > 0, "fades away");
    }

    // Phone-like px: density 3, scaled density 3, 16sp letters = 48px, 26 rows.
    public static void testLetterIndexClampsToTheColumn() {
        eq(0, Wave.index(40, 100, 50, 26), "above the column");
        eq(0, Wave.index(125, 100, 50, 26), "first row");
        eq(1, Wave.index(175, 100, 50, 26), "second row");
        eq(25, Wave.index(9999, 100, 50, 26), "below the column");
    }

    public static void testRowsShrinkWhenTheColumnIsShort() {
        near(48, Wave.textSize(26, 48, 2000, 3), "tall column keeps the size");
        near(42, Wave.textSize(26, 48, 1500, 3), "short column: 14/16 of it");
        near(57, Wave.step(48, 48), "row capped at 19/16 of the size");
        near(50.4f, Wave.step(42, 48), "otherwise 1.2 x the text");
    }

    public static void testColumnSitsLowButInsideItsMargins() {
        near(326, Wave.top(2000, 1482, 3), "low, but 64dp above the bottom");
        near(1230, Wave.top(2000, 100, 3), "short column sits at 64% of the height");
        near(144, Wave.top(2000, 1900, 3), "never above 48dp from the top");
    }

    public static void testAmplitudeGrowsWithProgressAndStopsAtTheCap() {
        near(242, Wave.amplitude(50, 1080, 1, 3, 3), "64dp + finger distance");
        near(336, Wave.amplitude(300, 1080, 1, 3, 3), "capped at the half width minus 68sp");
        near(121, Wave.amplitude(50, 1080, 0.5f, 3, 3), "grows with progress");
        near(0, Wave.amplitude(-300, 1080, 1, 3, 3), "never negative");
    }
}
