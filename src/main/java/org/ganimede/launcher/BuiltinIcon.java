package org.ganimede.launcher;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.ganimede.launcher.core.IconSet;

import java.util.List;

/**
 * One icon of the built-in set (core/IconSet) drawn as its SVG paints it, on the 48-unit grid scaled to the bounds:
 * white until tinted (Icons.apply tints it with the accent, issue 077).
 */
final class BuiltinIcon extends Drawable {
    private record Shape(Path path, boolean stroke, boolean fill, Paint.Cap cap, Paint.Join join, float width, float alpha) {}

    private final Shape[] shapes;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = Color.WHITE, alpha = 0xFF;

    BuiltinIcon(List<IconSet.PathData> data) {
        this(data.stream().map(p -> new Shape(path(p), p.stroke(), p.fill(),
                Paint.Cap.valueOf(p.cap().name()), Paint.Join.valueOf(p.join().name()), p.width(), p.alpha())).toArray(Shape[]::new));
    }

    /** Copies share the paths, which are never changed after they are built. */
    private BuiltinIcon(Shape[] shapes) {
        this.shapes = shapes;
    }

    private static Path path(IconSet.PathData p) {
        Path path = new Path();
        path.setFillType(p.evenOdd() ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        float[] q = p.points();
        int k = 0;
        for (IconSet.Op op : p.ops()) {
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
        c.scale(b.width() / 48f, b.height() / 48f);
        paint.setColor(color);
        for (Shape s : shapes) {
            int a = Math.round(Color.alpha(color) * s.alpha() * alpha / 255f);
            if (s.fill()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(a);
                c.drawPath(s.path(), paint);
            }
            if (s.stroke()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s.width());
                paint.setStrokeCap(s.cap());
                paint.setStrokeJoin(s.join());
                paint.setAlpha(a);
                c.drawPath(s.path(), paint);
            }
        }
        c.restoreToCount(save);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        color = tint == null ? Color.WHITE : tint.getDefaultColor();
        invalidateSelf();
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 192; } // 48 units at 4 px: other code sizes icons by their bounds
    @Override public int getIntrinsicHeight() { return 192; }

    /** Icons.copy hands out copies: each has its own tint. */
    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override public Drawable newDrawable() { return new BuiltinIcon(shapes); }
            @Override public int getChangingConfigurations() { return 0; }
        };
    }
}
