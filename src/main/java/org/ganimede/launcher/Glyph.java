package org.ganimede.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Thin line icons drawn in code on a 24-unit grid (round caps, 1.8 stroke), in the spirit of open line-icon sets:
 * no emoji, no image files, crisp at any size and tinted with the theme colour.
 */
final class Glyph extends Drawable {
    static final String[] NAMES = {"palette", "home", "button", "cloud", "calendar", "search", "gesture", "bell", "shield",
            "eyeoff", "backup", "globe", "info", "folder", "widget", "close", "image", "gear", "star", "edit", "trash", "up",
            "down", "music", "pause", "pin", "shortcut", "plus", "grid", "list", "check", "lock", "contrast", "type", "clock",
            "camera", "flash", "play", "apps", "layers", "arrow", "hand", "alert", "link", "reset"};

    private final String name;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(String name, int color) {
        this.name = name;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    static boolean has(String name) {
        if (name == null) return false;
        for (String n : NAMES) if (n.equals(name)) return true;
        return false;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        int save = c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2, b.top + (b.height() - 24 * s) / 2);
        c.scale(s, s);
        paint.setStrokeWidth(1.8f);
        Path p = new Path();
        switch (name) {
            case "palette":
                p.addCircle(12, 12, 9, Path.Direction.CW);
                c.drawPath(p, paint);
                dot(c, 8, 9);
                dot(c, 12, 7);
                dot(c, 16, 9);
                dot(c, 8.5f, 13.5f);
                c.drawCircle(15, 15, 2, paint);
                break;
            case "home":
                p.moveTo(3, 11);
                p.lineTo(12, 3.5f);
                p.lineTo(21, 11);
                p.moveTo(5.5f, 9.5f);
                p.lineTo(5.5f, 20);
                p.lineTo(18.5f, 20);
                p.lineTo(18.5f, 9.5f);
                p.moveTo(10, 20);
                p.lineTo(10, 14);
                p.lineTo(14, 14);
                p.lineTo(14, 20);
                c.drawPath(p, paint);
                break;
            case "button":
                c.drawCircle(12, 12, 9, paint);
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI * i / 4;
                    c.drawLine(12 - 4 * (float) Math.cos(a), 12 - 4 * (float) Math.sin(a), 12 + 4 * (float) Math.cos(a), 12 + 4 * (float) Math.sin(a), paint);
                }
                break;
            case "cloud": {
                Path a = new Path();
                a.addCircle(9, 13, 4.5f, Path.Direction.CW);
                Path q = new Path();
                q.addCircle(15, 11.5f, 5, Path.Direction.CW);
                Path r = new Path();
                r.addRoundRect(new RectF(4.5f, 13, 20.5f, 18.5f), 2.8f, 2.8f, Path.Direction.CW);
                a.op(q, Path.Op.UNION);
                a.op(r, Path.Op.UNION);
                c.drawPath(a, paint);
                break;
            }
            case "calendar":
                c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 20.5f), 3, 3, paint);
                c.drawLine(3.5f, 10, 20.5f, 10, paint);
                c.drawLine(8, 3, 8, 7, paint);
                c.drawLine(16, 3, 16, 7, paint);
                dot(c, 8, 14);
                dot(c, 12, 14);
                dot(c, 16, 14);
                break;
            case "search":
                c.drawCircle(10.5f, 10.5f, 6.5f, paint);
                c.drawLine(15.5f, 15.5f, 20.5f, 20.5f, paint);
                break;
            case "gesture":
            case "hand":
                c.drawCircle(12, 9, 2.5f, paint);
                c.drawArc(new RectF(6, 3, 18, 15), 200, 140, false, paint);
                p.moveTo(12, 11.5f);
                p.lineTo(12, 20.5f);
                p.moveTo(12, 15);
                p.lineTo(16, 16.5f);
                p.lineTo(16, 20.5f);
                c.drawPath(p, paint);
                break;
            case "bell":
                p.moveTo(6, 16.5f);
                p.lineTo(6, 11);
                p.cubicTo(6, 7.5f, 8.7f, 4.5f, 12, 4.5f);
                p.cubicTo(15.3f, 4.5f, 18, 7.5f, 18, 11);
                p.lineTo(18, 16.5f);
                p.lineTo(19.5f, 18);
                p.lineTo(4.5f, 18);
                p.close();
                c.drawPath(p, paint);
                c.drawLine(10, 21, 14, 21, paint);
                break;
            case "shield":
                p.moveTo(12, 3);
                p.lineTo(19.5f, 6);
                p.lineTo(19.5f, 11.5f);
                p.cubicTo(19.5f, 16, 16.5f, 19.5f, 12, 21);
                p.cubicTo(7.5f, 19.5f, 4.5f, 16, 4.5f, 11.5f);
                p.lineTo(4.5f, 6);
                p.close();
                c.drawPath(p, paint);
                c.drawLine(9, 12, 11.2f, 14.2f, paint);
                c.drawLine(11.2f, 14.2f, 15.5f, 9.8f, paint);
                break;
            case "eyeoff":
                p.moveTo(2.5f, 12);
                p.cubicTo(5, 7.5f, 8.5f, 5.5f, 12, 5.5f);
                p.cubicTo(15.5f, 5.5f, 19, 7.5f, 21.5f, 12);
                p.cubicTo(19, 16.5f, 15.5f, 18.5f, 12, 18.5f);
                p.cubicTo(8.5f, 18.5f, 5, 16.5f, 2.5f, 12);
                c.drawPath(p, paint);
                c.drawCircle(12, 12, 3, paint);
                c.drawLine(4, 4, 20, 20, paint);
                break;
            case "backup":
                c.drawLine(12, 3.5f, 12, 14.5f, paint);
                p.moveTo(7.5f, 10);
                p.lineTo(12, 14.5f);
                p.lineTo(16.5f, 10);
                p.moveTo(4, 16);
                p.lineTo(4, 20);
                p.lineTo(20, 20);
                p.lineTo(20, 16);
                c.drawPath(p, paint);
                break;
            case "globe":
                c.drawCircle(12, 12, 9, paint);
                c.drawLine(3, 12, 21, 12, paint);
                c.drawOval(new RectF(8, 3, 16, 21), paint);
                break;
            case "info":
            case "alert":
                c.drawCircle(12, 12, 9, paint);
                if (name.equals("info")) {
                    c.drawLine(12, 11, 12, 16.5f, paint);
                    dot(c, 12, 7.8f);
                } else {
                    c.drawLine(12, 7, 12, 13, paint);
                    dot(c, 12, 16.5f);
                }
                break;
            case "folder":
                p.moveTo(3.5f, 7);
                p.lineTo(3.5f, 18.5f);
                p.lineTo(20.5f, 18.5f);
                p.lineTo(20.5f, 8.5f);
                p.lineTo(12, 8.5f);
                p.lineTo(10, 5.5f);
                p.lineTo(5, 5.5f);
                p.close();
                c.drawPath(p, paint);
                break;
            case "widget":
            case "grid":
            case "apps": {
                float g = name.equals("apps") ? 5.5f : 7;
                for (int i = 0; i < (name.equals("apps") ? 3 : 2); i++)
                    for (int j = 0; j < (name.equals("apps") ? 3 : 2); j++) {
                        float x = (name.equals("apps") ? 3.5f : 4) + i * (g + 1.5f), y = (name.equals("apps") ? 3.5f : 4) + j * (g + 1.5f);
                        c.drawRoundRect(new RectF(x, y, x + g, y + g), 1.8f, 1.8f, paint);
                    }
                break;
            }
            case "close":
                c.drawLine(6, 6, 18, 18, paint);
                c.drawLine(18, 6, 6, 18, paint);
                break;
            case "image":
                c.drawRoundRect(new RectF(3.5f, 4.5f, 20.5f, 19.5f), 2.5f, 2.5f, paint);
                c.drawCircle(9, 9.5f, 1.8f, paint);
                p.moveTo(3.5f, 17);
                p.lineTo(9, 12.5f);
                p.lineTo(13, 16);
                p.lineTo(16, 13.5f);
                p.lineTo(20.5f, 17.5f);
                c.drawPath(p, paint);
                break;
            case "gear":
                c.drawCircle(12, 12, 3, paint);
                c.drawCircle(12, 12, 6.8f, paint);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    c.drawLine(12 + 6.8f * (float) Math.cos(a), 12 + 6.8f * (float) Math.sin(a), 12 + 9 * (float) Math.cos(a), 12 + 9 * (float) Math.sin(a), paint);
                }
                break;
            case "star":
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + Math.PI * i / 5;
                    float r = i % 2 == 0 ? 9 : 3.9f;
                    float x = 12 + r * (float) Math.cos(a), y = 12.8f + r * (float) Math.sin(a);
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                c.drawPath(p, paint);
                break;
            case "edit":
                p.moveTo(4, 20);
                p.lineTo(5, 15.5f);
                p.lineTo(15.5f, 5);
                p.lineTo(19, 8.5f);
                p.lineTo(8.5f, 19);
                p.close();
                c.drawPath(p, paint);
                c.drawLine(13.5f, 7, 17, 10.5f, paint);
                break;
            case "trash":
                c.drawLine(4, 6.5f, 20, 6.5f, paint);
                p.moveTo(9, 6.5f);
                p.lineTo(9.5f, 4);
                p.lineTo(14.5f, 4);
                p.lineTo(15, 6.5f);
                p.moveTo(6, 6.5f);
                p.lineTo(7, 20);
                p.lineTo(17, 20);
                p.lineTo(18, 6.5f);
                c.drawPath(p, paint);
                c.drawLine(10, 10, 10, 16.5f, paint);
                c.drawLine(14, 10, 14, 16.5f, paint);
                break;
            case "reset": { // a circular arrow: back to the start
                // Clockwise from the top right round to the top left; the head points the way it travels.
                c.drawArc(new android.graphics.RectF(5, 5, 19, 19), -60, 290, false, paint);
                p.moveTo(5.9f, 5.3f);
                p.lineTo(9.4f, 5f);
                p.lineTo(8.5f, 8.4f);
                c.drawPath(p, paint);
                break;
            }
            case "up":
            case "down": {
                boolean up = name.equals("up");
                c.drawLine(12, 5, 12, 19, paint);
                p.moveTo(6.5f, up ? 10.5f : 13.5f);
                p.lineTo(12, up ? 5 : 19);
                p.lineTo(17.5f, up ? 10.5f : 13.5f);
                c.drawPath(p, paint);
                break;
            }
            case "music":
                c.drawCircle(7.5f, 17, 2.5f, paint);
                c.drawCircle(17, 15, 2.5f, paint);
                p.moveTo(10, 17);
                p.lineTo(10, 5.5f);
                p.lineTo(19.5f, 3.5f);
                p.lineTo(19.5f, 15);
                c.drawPath(p, paint);
                break;
            case "pause":
                c.drawCircle(12, 12, 9, paint);
                c.drawLine(10, 8.5f, 10, 15.5f, paint);
                c.drawLine(14, 8.5f, 14, 15.5f, paint);
                break;
            case "play":
                c.drawCircle(12, 12, 9, paint);
                p.moveTo(10, 8.5f);
                p.lineTo(15.5f, 12);
                p.lineTo(10, 15.5f);
                p.close();
                c.drawPath(p, paint);
                break;
            case "pin":
                p.moveTo(12, 21);
                p.cubicTo(12, 21, 5.5f, 14.5f, 5.5f, 9.5f);
                p.cubicTo(5.5f, 5.9f, 8.4f, 3, 12, 3);
                p.cubicTo(15.6f, 3, 18.5f, 5.9f, 18.5f, 9.5f);
                p.cubicTo(18.5f, 14.5f, 12, 21, 12, 21);
                c.drawPath(p, paint);
                c.drawCircle(12, 9.5f, 2.3f, paint);
                break;
            case "shortcut":
            case "arrow":
            case "link":
                p.moveTo(9, 6);
                p.lineTo(18, 6);
                p.lineTo(18, 15);
                c.drawPath(p, paint);
                c.drawLine(18, 6, 6, 18, paint);
                break;
            case "plus":
                c.drawLine(12, 5, 12, 19, paint);
                c.drawLine(5, 12, 19, 12, paint);
                break;
            case "list":
                for (int i = 0; i < 3; i++) {
                    dot(c, 5, 7 + i * 5);
                    c.drawLine(9, 7 + i * 5, 20, 7 + i * 5, paint);
                }
                break;
            case "check":
                p.moveTo(5, 12.5f);
                p.lineTo(10, 17.5f);
                p.lineTo(19, 7);
                c.drawPath(p, paint);
                break;
            case "lock":
                c.drawRoundRect(new RectF(5, 10.5f, 19, 20.5f), 2.5f, 2.5f, paint);
                c.drawArc(new RectF(8, 3.5f, 16, 13.5f), 180, 180, false, paint);
                c.drawLine(8, 8.5f, 8, 10.5f, paint);
                c.drawLine(16, 8.5f, 16, 10.5f, paint);
                break;
            case "contrast": {
                c.drawCircle(12, 12, 9, paint);
                Paint f = new Paint(paint);
                f.setStyle(Paint.Style.FILL);
                c.drawArc(new RectF(3, 3, 21, 21), 90, 180, true, f);
                break;
            }
            case "type":
                c.drawLine(5, 6, 19, 6, paint);
                c.drawLine(12, 6, 12, 19.5f, paint);
                c.drawLine(9, 19.5f, 15, 19.5f, paint);
                break;
            case "clock":
                c.drawCircle(12, 12, 9, paint);
                c.drawLine(12, 12, 12, 7, paint);
                c.drawLine(12, 12, 15.5f, 14, paint);
                break;
            case "camera":
                p.moveTo(3.5f, 8);
                p.lineTo(7.5f, 8);
                p.lineTo(9, 5.5f);
                p.lineTo(15, 5.5f);
                p.lineTo(16.5f, 8);
                p.lineTo(20.5f, 8);
                p.lineTo(20.5f, 19);
                p.lineTo(3.5f, 19);
                p.close();
                c.drawPath(p, paint);
                c.drawCircle(12, 13, 3.5f, paint);
                break;
            case "flash":
                p.moveTo(13, 3);
                p.lineTo(6, 13.5f);
                p.lineTo(11.5f, 13.5f);
                p.lineTo(10.5f, 21);
                p.lineTo(18, 10);
                p.lineTo(12.5f, 10);
                p.close();
                c.drawPath(p, paint);
                break;
            case "layers":
                p.moveTo(12, 4);
                p.lineTo(21, 9);
                p.lineTo(12, 14);
                p.lineTo(3, 9);
                p.close();
                p.moveTo(3, 13.5f);
                p.lineTo(12, 18.5f);
                p.lineTo(21, 13.5f);
                c.drawPath(p, paint);
                break;
            default:
                c.drawCircle(12, 12, 8, paint);
        }
        c.restoreToCount(save);
    }

    private void dot(Canvas c, float x, float y) {
        Paint f = new Paint(paint);
        f.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, 1.3f, f);
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 72; }
    @Override public int getIntrinsicHeight() { return 72; }
}
