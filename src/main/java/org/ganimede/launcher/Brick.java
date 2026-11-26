package org.ganimede.launcher;

import org.ganimede.launcher.core.Gestures;

import android.content.SharedPreferences;
import android.graphics.RectF;

/**
 * A brick of the home screen as Settings sees it: one part that can be picked in the preview, moved and sized.
 * Each names the settings that hold its position and its size; the home screen reads those same settings.
 */
final class Brick {
    /** Picked when no brick is: the controls for the screen as a whole. */
    static final String SCREEN = "screen";
    /** What x counts from: a free shift, or a distance from the screen's start or end edge (mirrored in left-hand use). */
    private static final int FREE = 0, START = 1, END = 2;

    final String id, title, kx, ky, ksize, unit;
    final int xdef, xmin, xmax, ymax, smin, smax, sdef, sstep;
    /** Which edge its size knob sits on and pulls: -1 the top (the brick grows upward), 1 the bottom, 0 no knob. */
    final int grow;
    private final int edge;
    /** Where the preview drew it last (in the preview's full-size pixels), and whether it shows at all. */
    final RectF at = new RectF();
    boolean shown;

    private Brick(String id, String title, String kx, int edge, String ky, int ymax,
                  String ksize, int smin, int smax, int sdef, int sstep, String unit, int grow) {
        this.id = id;
        this.title = title;
        this.kx = kx;
        this.edge = edge;
        this.ky = ky;
        this.ymax = ymax;
        this.ksize = ksize;
        this.smin = smin;
        this.smax = smax;
        this.sdef = sdef;
        this.sstep = sstep;
        this.unit = unit;
        this.grow = grow;
        xdef = edge == START ? 40 : 0;
        xmin = id.equals("apps") ? Gestures.OPPOSITE_EDGE_STRIP_DP : edge == END ? -20 : edge == START ? 4 : -400;
        xmax = edge == END ? 40 : edge == START ? 240 : 400;
    }

    /** The bricks of a mode, top to bottom. List mode stacks from the bottom, so its bricks grow upward. */
    static Brick[] of(boolean classic) {
        if (classic) return new Brick[]{
                new Brick("grid", "App grid", "classic_grid_x", FREE, "classic_grid_y", 600, null, 0, 0, 0, 0, null, 0),
                new Brick("dock", "Dock", "classic_dock_x", FREE, "classic_dock_y", 600, null, 0, 0, 0, 0, null, 0)};
        return new Brick[]{
                new Brick("clock", "Clock", "clock_offset", START, "list_clock_y", 600, "list_clock_size", 40, 160, 100, 5, "%", -1),
                new Brick("line", "Date and weather", "line_offset", START, "list_line_y", 600, null, 0, 0, 0, 0, null, 0),
                new Brick("widget", "Widgets", "list_widget_x", FREE, "list_widget_y", 600, "list_widget_h", 0, 480, 0, 4, " dp", -1),
                new Brick("apps", "Apps", "apps_offset", START, "list_apps_y", 600, "icon_size", 32, 80, 54, 2, " dp", -1),
                new Brick("alpha", "Alphabet", "alpha_offset", END, "alpha_y", 160, "alpha_size", 12, 24, 16, 1, " sp", 1)};
    }

    static Brick find(Brick[] all, String id) {
        for (Brick b : all) if (b.id.equals(id)) return b;
        return null;
    }

    /** How far one unit of x moves the brick to the right on screen: 1 or -1. */
    int right(SharedPreferences p) {
        int s = edge == END ? -1 : 1;
        return edge != FREE && p.getBoolean("left_handed", false) ? -s : s;
    }

    /** The widget area's size is a height in dp; the other sizes scale the whole brick. */
    boolean sizedInDp() {
        return "list_widget_h".equals(ksize);
    }

    /** The app column fills its row up to the alphabet: moving it sideways moves one edge only. */
    boolean stretches() {
        return id.equals("apps");
    }

    /** The alphabet stays centred on the screen's height: a pull on its knob grows it both ways. */
    boolean centred() {
        return id.equals("alpha");
    }

    int x(SharedPreferences p) {
        int stored = p.getInt(kx, xdef);
        return id.equals("apps") ? Gestures.effectiveAppsOffsetDp(stored) : stored;
    }
    int y(SharedPreferences p) { return p.getInt(ky, 0); }
    int size(SharedPreferences p) { return p.getInt(ksize, sdef); }

    int clampX(int v) { return Math.max(xmin, Math.min(xmax, v)); }
    int clampY(int v) { return Math.max(-ymax, Math.min(ymax, v)); }
    int clampSize(int v) { return Math.max(smin, Math.min(smax, v)); }
}
