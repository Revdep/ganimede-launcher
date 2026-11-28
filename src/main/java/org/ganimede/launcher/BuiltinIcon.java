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
    private IconLook look = LINE;
    private int color = Color.WHITE, alpha = 0xFF;
    private ColorFilter filter;

    BuiltinIcon(List<IconSet.PathData> data) {
        this(data, data.stream().map(BuiltinIcon::path).toArray(Path[]::new));
    }

    private BuiltinIcon(List<IconSet.PathData> data, Path[] paths) {
        this.data = data;
        this.paths = paths;
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
        Path[] shapes = paths;
        if (s.outline() > 0) {
            Path[] o = outlines(s);
            if (o == null) s = s.fallback(); // no outline to be had: Bold
            else shapes = o;
        }
        GlyphFit fit = look.plated() ? GlyphFit.onContainer(GlyphFit.bounds(data), s.extent(), px) : GlyphFit.grid(px);
        Matrix m = new Matrix();
        m.setScale(fit.scale(), fit.scale());
        m.postTranslate(fit.dx(), fit.dy());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(filter);
        for (int i = 0; i < paths.length; i++) {
            IconSet.PathData p = data.get(i);
            Path path = new Path(shapes[i]);
            path.transform(m);
            float a = p.alpha() * alpha / 255f; // the path's own alpha and the drawable's, for fill and stroke alike
            paint.setColor(color);
            paint.setAlpha(Math.round(Color.alpha(color) * a));
            if (p.fill()) {
                paint.setStyle(Paint.Style.FILL);
                c.drawPath(path, paint);
            }
            if (!p.stroke()) continue;
            if (s.fillsClosed() && !p.fill()) {
                Path closed = path(p, true);
                closed.transform(m);
                paint.setColor(fill);
                paint.setAlpha(Math.round(Color.alpha(fill) * a));
                paint.setStyle(Paint.Style.FILL);
                c.drawPath(closed, paint);
                paint.setColor(color);
                paint.setAlpha(Math.round(Color.alpha(color) * a));
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s.width() * px / 48f);
            paint.setStrokeCap(Paint.Cap.valueOf(s.cap().name()));
            paint.setStrokeJoin(Paint.Join.valueOf(s.join().name()));
            c.drawPath(path, paint);
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
        render(c, look, color, 0, Math.min(b.width(), b.height()));
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
            @Override public Drawable newDrawable() { return new BuiltinIcon(data, paths); }
            @Override public int getChangingConfigurations() { return 0; }
        };
    }
}
