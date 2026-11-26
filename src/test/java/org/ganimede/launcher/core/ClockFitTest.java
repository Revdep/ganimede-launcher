package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.near;
import static org.ganimede.launcher.Expect.yes;

/** A requested clock size yields when its complete drawing does not fit the space on the home screen. */
public class ClockFitTest {
    public static void testDateHitUsesTheDateBoxMeasuredWhileDrawing() {
        for (String style : new String[]{"dayofweek", "vertical", "horizontal"}) {
            yes(ClockFit.dateHit(style, 40, 30, 20, 10, 80, 50), style + ": a point in the drawn date box is the date");
            yes(!ClockFit.dateHit(style, 10, 30, 20, 10, 80, 50), style + ": a point outside the drawn date box is the time");
        }
    }

    public static void testStylesWithoutADrawnDateOpenTheClockEverywhere() {
        for (String style : new String[]{"overlap", "basic", "words", "flip", "analog", "none"})
            yes(!ClockFit.dateHit(style, 40, 30, 20, 10, 80, 50), style + ": even the date-box coordinates are the clock");
    }

    public static void testScaleNeverEnlargesAndFitsAvailableWidth() {
        near(1f, ClockFit.scale(220, 300), "a clock that fits keeps its requested size");
        near(0.8f, ClockFit.scale(300, 240), "a wider clock shrinks exactly to the available width");
        near(1f, ClockFit.scale(240, 240), "a clock that exactly fills the width is unchanged");
        near(0.75f, ClockFit.scale(200, 200, 320, 240), "a tall primitive such as the vertical date also limits the scale");
        near(0.5f, ClockFit.scale(400, 200, 300, 240), "the tighter axis limits the whole drawing");
    }

    public static void testEveryMeasuredRectangleFitsBothAxes() {
        float[] extents = {1, 60, 96, 104, 110, 140, 150, 230, 320, 600};
        float[] spaces = {0, 40, 160, 240, 400};
        for (float width : extents) for (float height : extents)
            for (float availableWidth : spaces) for (float availableHeight : spaces) {
                float scale = ClockFit.scale(width, availableWidth, height, availableHeight);
                yes(scale <= 1, "fit never enlarges a drawing");
                yes(width * scale <= availableWidth + 0.001f, "fitted width stays available");
                yes(height * scale <= availableHeight + 0.001f, "fitted height stays available");
            }
    }
}
