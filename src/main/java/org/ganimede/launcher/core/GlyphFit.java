package org.ganimede.launcher.core;

import java.util.Arrays;
import java.util.List;

import org.ganimede.launcher.core.IconSet.Op;
import org.ganimede.launcher.core.IconSet.PathData;

/**
 * Where a built-in glyph (48x48 grid) goes in an icon of px: on a container it is centred on its drawn bounds with
 * its longer side 26/48 of the container; the stroke keeps its px width (width x px/48): the geometry scales, not the
 * stroke. Without a container it stays on its own grid.
 */
public record GlyphFit(float scale, float dx, float dy, float strokePx) {
    private static final float GRID = 48, SHARE = 26 / GRID;

    /** {@code bounds} is left, top, right, bottom of the paths in grid units; {@code stroke} the width in grid units. */
    public static GlyphFit onContainer(float[] bounds, float stroke, int px) {
        float strokePx = stroke * px / GRID;
        float scale = (px * SHARE - strokePx) / Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]);
        return new GlyphFit(scale, px / 2f - (bounds[0] + bounds[2]) / 2 * scale,
                px / 2f - (bounds[1] + bounds[3]) / 2 * scale, strokePx);
    }

    public static GlyphFit grid(float stroke, int px) {
        return new GlyphFit(px / GRID, 0, 0, stroke * px / GRID);
    }

    /** The box around every point of the paths (control points included, ovals by their box), as Path.computeBounds. */
    public static float[] bounds(List<PathData> paths) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (PathData p : paths)
            for (int i = 0; i + 1 < p.points().length; i += 2) {
                b[0] = Math.min(b[0], p.points()[i]);
                b[1] = Math.min(b[1], p.points()[i + 1]);
                b[2] = Math.max(b[2], p.points()[i]);
                b[3] = Math.max(b[3], p.points()[i + 1]);
            }
        return b;
    }

    /** x, y of each subpath made only of lines back to its start ("M p L p"): a dot, drawn by the cap alone. */
    public static float[] dots(PathData p) {
        float[] pts = p.points(), out = new float[pts.length];
        int n = 0, at = 0, start = -1;
        boolean dot = false;
        for (Op op : p.ops()) {
            if (op == Op.MOVE || op == Op.MOVE_CLOSED || op == Op.OVAL) {
                if (dot) { out[n++] = pts[start]; out[n++] = pts[start + 1]; }
                start = op == Op.OVAL ? -1 : at;
                dot = false;
            } else if (op == Op.LINE && start >= 0) {
                dot = (dot || at == start + 2) && pts[at] == pts[start] && pts[at + 1] == pts[start + 1];
                if (!dot) start = -1;
            } else if (op == Op.CUBIC) {
                start = -1; // a curve: not a dot
                dot = false;
            }
            at += 2 * IconSet.points(op);
        }
        if (dot) { out[n++] = pts[start]; out[n++] = pts[start + 1]; }
        return Arrays.copyOf(out, n);
    }
}
