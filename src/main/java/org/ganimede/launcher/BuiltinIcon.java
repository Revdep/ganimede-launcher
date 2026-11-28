package org.ganimede.launcher;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.ganimede.launcher.core.GlyphFit;
import org.ganimede.launcher.core.IconLook;
import org.ganimede.launcher.core.IconSet;

import java.util.List;

/**
 * One icon of the built-in set (core/IconSet), drawn with an icon-model treatment (issue 078): Line until told otherwise,
 * white until tinted. Icons.apply draws it into the icon cache with the chosen look (folder thumbnails too); the drawable
 * itself, the placeholder until then, draws by the same rule.
 */
final class BuiltinIcon extends Drawable {
    private static final IconLook LINE = IconLook.of("line", "none", "solid", "accent");

    final List<IconSet.PathData> data;
    private final Path[] paths; // built once on the 48-unit grid, shared by copies, never changed
    private final Path[] closed; // the closed subpaths of each path (Duotone's fill), built on first use, shared like paths
    private final float[] bounds; // of every path: where the glyph sits on a container
    // what a draw works with, kept: a drawn icon allocates nothing (a copy of its own, each thread has its copy)
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), edge = new Path(), shut = new Path();
    private IconLook look = LINE;
    private int color = Color.WHITE, alpha = 0xFF;
    private ColorFilter filter;

    BuiltinIcon(List<IconSet.PathData> data) {
        this(data, data.stream().map(BuiltinIcon::path).toArray(Path[]::new), new Path[data.size()], GlyphFit.bounds(data));
    }

    private BuiltinIcon(List<IconSet.PathData> data, Path[] paths, Path[] closed, float[] bounds) {
        this.data = data;
        this.paths = paths;
        this.closed = closed;
        this.bounds = bounds;
    }

    private Path closed(int i) {
        synchronized (closed) { // copies on the render thread and the main thread share it
            if (closed[i] == null) closed[i] = path(data.get(i), true);
            return closed[i];
        }
    }

    /** The treatment and container this drawable draws with (a placeholder matches the icon drawn for it). */
    BuiltinIcon look(IconLook l) {
        look = l;
        invalidateSelf();
        return this;
    }

    /**
     * The glyph on a px square with a treatment: Line, Bold, Sharp, Duotone or Hollow set width, cap and join of every
     * stroked path (the set's own are not used); fitted to the container by GlyphFit when there is one. {@code fill}:
     * Duotone's colour for the closed shapes (0: none).
     */
    void render(Canvas c, IconLook look, int color, int fill, int px) {
        IconLook.Stroke s = look.stroke();
        Path[] edges = s.outline() > 0 ? outlines(s) : paths;
        if (edges == null) { // no outline to be had: Bold
            s = s.fallback();
            edges = paths;
        }
        paint(c, look.plated(), s, edges, color, fill, px);
    }

    /** {@code edges}: what each path's stroke follows (Hollow's contours, else the path itself); fills always follow the path. */
    private void paint(Canvas c, boolean plated, IconLook.Stroke s, Path[] edges, int color, int fill, int px) {
        GlyphFit fit = plated ? GlyphFit.onContainer(bounds, s.extent(), px) : GlyphFit.grid(px);
        Matrix m = matrix;
        m.setScale(fit.scale(), fit.scale());
        m.postTranslate(fit.dx(), fit.dy());
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColorFilter(filter);
        for (int i = 0; i < paths.length; i++) {
            IconSet.PathData p = data.get(i);
            path.reset();
            path.setFillType(paths[i].getFillType());
            path.addPath(paths[i], m);
            Path stroked = path;
            if (edges[i] != paths[i]) {
                edge.reset();
                edge.setFillType(edges[i].getFillType());
                edge.addPath(edges[i], m);
                stroked = edge;
            }
            float a = p.alpha() * alpha / 255f; // the path's own alpha and the drawable's, for fill and stroke alike
            paint.setColor(color);
            paint.setAlpha(Math.round(Color.alpha(color) * a));
            if (p.fill()) {
                paint.setStyle(Paint.Style.FILL);
                c.drawPath(path, paint);
            }
            if (!p.stroke()) continue;
            if (s.fillsClosed() && !p.fill()) {
                shut.reset();
                shut.setFillType(closed(i).getFillType());
                shut.addPath(closed(i), m);
                paint.setColor(fill);
                paint.setAlpha(Math.round(Color.alpha(fill) * a));
                paint.setStyle(Paint.Style.FILL);
                c.drawPath(shut, paint);
                paint.setColor(color);
                paint.setAlpha(Math.round(Color.alpha(color) * a));
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s.width() * px / 48f);
            paint.setStrokeCap(Paint.Cap.valueOf(s.cap().name()));
            paint.setStrokeJoin(Paint.Join.valueOf(s.join().name()));
            c.drawPath(stroked, paint);
            if (s.dotCap() == s.cap()) continue;
            float[] dots = GlyphFit.dots(p); // zero-length subpaths vanish under this cap: drawn as round points
            m.mapPoints(dots);
            paint.setStrokeCap(Paint.Cap.valueOf(s.dotCap().name()));
            c.drawPoints(dots, paint);
        }
    }

    /** Each stroked path as the outline of a stroke {@code s.outline()} wide, in grid units; null when one can't be had. */
    private Path[] outlines(IconLook.Stroke s) {
        Paint wide = new Paint();
        wide.setStyle(Paint.Style.STROKE);
        wide.setStrokeWidth(s.outline());
        wide.setStrokeCap(Paint.Cap.valueOf(s.cap().name()));
        wide.setStrokeJoin(Paint.Join.valueOf(s.join().name()));
        Path[] out = new Path[paths.length];
        for (int i = 0; i < paths.length; i++) {
            if (!data.get(i).stroke()) { out[i] = paths[i]; continue; }
            Path edge = new Path(), united = new Path();
            wide.getFillPath(paths[i], edge);
            if (!united.op(edge, edge, Path.Op.UNION) || united.isEmpty()) return null;
            out[i] = united;
        }
        return out;
    }

    private static Path path(IconSet.PathData p) {
        return path(p, false);
    }

    /** {@code closedOnly}: just the closed subpaths (a Z, or back to the start; ovals), the ones Duotone fills. */
    private static Path path(IconSet.PathData p, boolean closedOnly) {
        Path path = new Path();
        path.setFillType(p.evenOdd() ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        float[] q = p.points();
        int k = 0;
        boolean keep = true;
        for (IconSet.Op op : p.ops()) {
            if (op == IconSet.Op.MOVE || op == IconSet.Op.MOVE_CLOSED) keep = !closedOnly || op == IconSet.Op.MOVE_CLOSED;
            else if (op == IconSet.Op.OVAL) keep = true;
            if (!keep) { k += 2 * IconSet.points(op); continue; }
            switch (op) {
                case MOVE, MOVE_CLOSED -> path.moveTo(q[k++], q[k++]); // MOVE_CLOSED only marks a closed subpath
                case LINE -> path.lineTo(q[k++], q[k++]);
                case CUBIC -> path.cubicTo(q[k++], q[k++], q[k++], q[k++], q[k++], q[k++]);
                case CLOSE -> path.close();
                case OVAL -> path.addOval(new RectF(q[k++], q[k++], q[k++], q[k++]), Path.Direction.CW);
            }
        }
        return path;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        int save = c.save();
        c.translate(b.left, b.top);
        // Hollow's contours are worked out in the cached render only: the placeholder draws it as Bold
        paint(c, look.plated(), look.stroke().fallback(), paths, color, 0, Math.min(b.width(), b.height()));
        c.restoreToCount(save);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        color = tint == null ? Color.WHITE : tint.getDefaultColor();
        invalidateSelf();
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { filter = cf; invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 192; } // 48 units at 4 px: other code sizes icons by their bounds
    @Override public int getIntrinsicHeight() { return 192; }

    /** Icons.copy hands out copies: each has its own tint and look. */
    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override public Drawable newDrawable() { return new BuiltinIcon(data, paths, closed, bounds); }
            @Override public int getChangingConfigurations() { return 0; }
        };
    }
}
