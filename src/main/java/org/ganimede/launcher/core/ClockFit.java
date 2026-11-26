package org.ganimede.launcher.core;

/** How much a clock drawing may keep of its requested size inside an available span. */
public final class ClockFit {
    public static boolean drawsDate(String style) {
        return style.equals("dayofweek") || style.equals("vertical") || style.equals("horizontal");
    }

    public static boolean dateHit(String style, float x, float y, float left, float top, float right, float bottom) {
        return drawsDate(style) && x >= left && x <= right && y >= top && y <= bottom;
    }

    public static float scale(float content, float available) {
        return content > available ? available / content : 1f;
    }

    public static float scale(float width, float availableWidth, float height, float availableHeight) {
        return Math.min(scale(width, availableWidth), scale(height, availableHeight));
    }

    private ClockFit() {}
}
