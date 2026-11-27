package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.ClockFit;
import org.ganimede.launcher.core.Words;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The home clock, drawn by hand in one of the styles listed in {@link #STYLES}.
 */
public class ClockView extends View {
    private static final int EDGE_DP = 4;
    static final String[] STYLES = {"overlap", "basic", "dayofweek", "vertical", "horizontal", "words", "flip", "analog", "none"};
    static final String[] NAMES = {"Overlap", "Basic", "Day of week", "Vertical", "Horizontal", "Words", "Flip", "Analog", "No clock"};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dateBox = new RectF();
    private final float d;
    private float tapX = Float.NaN, tapY = Float.NaN;
    private static final int ACTION_OPEN_DATE = R.id.action_open_date; // custom accessibility actions need an app resource id
    private Outlook outlook;
    String style = "overlap";
    boolean h24;
    int color = 0xFFFFFFFF, accent = Colors.ACCENT;
    Typeface face = Typeface.DEFAULT_BOLD;
    Locale locale = Locale.getDefault();
    private final Map<String, SimpleDateFormat> formats = new HashMap<>(); // by pattern, for formatsLocale
    private Locale formatsLocale;
    /** Size setting: the whole clock drawn larger or smaller (1 = the built-in size). */
    float scale = 1;

    public ClockView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
    }

    void look(String style, boolean h24, int color, int accent, Typeface face) {
        this.style = style;
        this.h24 = h24;
        this.color = color;
        this.accent = accent;
        this.face = face;
    }

    /** Styles that already show the date, so the line under the clock leaves it out. */
    boolean drawsDate() {
        return ClockFit.drawsDate(style);
    }

    /** One tap rule for both home modes: the drawn date opens the calendar, everything else the alarms. */
    void setClockTapListener(Ui ui, Outlook outlook) {
        this.outlook = outlook;
        setOnClickListener(v -> {
            float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            boolean date = ClockFit.dateHit(style, tapX, tapY,
                    dateBox.left - slop, dateBox.top - slop, dateBox.right + slop, dateBox.bottom + slop);
            tapX = tapY = Float.NaN; // a later accessibility click has no touch point
            if (date) outlook.onDateTap();
            else ui.open(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        });
    }

    //noinspection ClickableViewAccessibility: super.onTouchEvent performs the click; this only records where
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // View posts performClick after ACTION_UP, so the point stays until the listener reads it or the next DOWN.
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) tapX = tapY = Float.NaN;
        else if (action == MotionEvent.ACTION_UP && e.getEventTime() - e.getDownTime() < ViewConfiguration.getLongPressTimeout()) {
            // A long press goes to the home's menu and no click follows (seen on the emulator), so it must leave no point.
            tapX = e.getX();
            tapY = e.getY();
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int height = Math.round(baseHeight() * scale);
        setMeasuredDimension(MeasureSpec.getSize(w), resolveSize(height, h));
    }

    private float baseHeight() {
        float sp = getResources().getDisplayMetrics().scaledDensity;
        switch (style) {
            case "none": return 0;
            case "basic": return 70 * sp;
            case "horizontal": return 60 * sp;
            case "words": return 110 * sp;
            case "dayofweek": return 140 * sp;
            case "vertical": return 230 * sp;
            case "analog": return 150 * d;
            case "flip": return 96 * d;
            default: return 104 * sp;
        }
    }

    private void text(float sp, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setXfermode(null);
        p.setTextScaleX(1);
        p.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        p.setTypeface(Typeface.create(face, bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setColor(color);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShadowLayer(4 * d, 0, d, 0x55000000);
    }

    @Override
    protected void onDraw(Canvas c) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY), min = now.get(Calendar.MINUTE);
        int hh = h24 ? hour : (hour % 12 == 0 ? 12 : hour % 12);
        String hs = h24 ? String.format(Locale.ROOT, "%02d", hh) : String.valueOf(hh);
        String ms = String.format(Locale.ROOT, "%02d", min);
        float w = getWidth() / scale, h = baseHeight();
        RectF bounds = drawingBounds(hs, ms, now, hh, hour, min, w, h);
        float fit = fitScale(bounds);
        float edge = EDGE_DP * d;
        // Left-hand mode: the clock hugs the right edge, so shift the drawing by the free room on its left.
        boolean mirror = getLayoutDirection() == LAYOUT_DIRECTION_RTL && !style.equals("dayofweek");
        float tx = style.equals("dayofweek") ? (w - fit * (bounds.left + bounds.right)) / 2
                : mirror ? w - edge - bounds.right * fit : edge - bounds.left * fit;
        float ty = getHeight() / scale - edge - bounds.bottom * fit;
        int saved = c.save();
        c.scale(scale, scale);
        c.translate(tx, ty);
        c.scale(fit, fit);
        RectF drawnDate = new RectF();
        drawStyle(c, null, drawnDate, now, hour, min, hh, hs, ms, w, h);
        c.restoreToCount(saved);
        if (drawnDate.isEmpty()) dateBox.setEmpty();
        else dateBox.set(scale * (tx + fit * drawnDate.left), scale * (ty + fit * drawnDate.top),
                scale * (tx + fit * drawnDate.right), scale * (ty + fit * drawnDate.bottom));
    }

    /** How wide the clock looks on screen now (to keep it inside the screen when it's moved). */
    float drawnWidth() {
        if (style.equals("dayofweek")) return getWidth(); // drawn across the middle
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY), min = now.get(Calendar.MINUTE);
        int hh = h24 ? hour : (hour % 12 == 0 ? 12 : hour % 12);
        String hs = h24 ? String.format(Locale.ROOT, "%02d", hh) : String.valueOf(hh);
        RectF bounds = drawingBounds(hs, String.format(Locale.ROOT, "%02d", min), now, hh, hour, min,
                getWidth() / scale, baseHeight());
        if (bounds.isEmpty()) return 0;
        float fit = fitScale(bounds);
        return Math.min(getWidth(), (bounds.width() * fit + 2 * EDGE_DP * d) * scale);
    }

    private float fitScale(RectF bounds) {
        if (bounds.isEmpty()) return 1;
        float edge = 2 * EDGE_DP * d;
        return ClockFit.scale(bounds.width(), Math.max(0, getWidth() / scale - edge),
                bounds.height(), Math.max(0, getHeight() / scale - edge));
    }

    /** Bounds of every primitive before Canvas clips it; fitting this rectangle keeps all information visible. */
    private RectF drawingBounds(String hs, String ms, Calendar now, int hh, int hour, int min, float w, float h) {
        RectF out = new RectF();
        drawStyle(null, out, null, now, hour, min, hh, hs, ms, w, h);
        return out;
    }

    private RectF textBounds(String value, float x, float baseline) {
        float width = p.measureText(value);
        if (p.getTextAlign() == Paint.Align.CENTER) x -= width / 2;
        else if (p.getTextAlign() == Paint.Align.RIGHT) x -= width;
        Paint.FontMetrics fm = p.getFontMetrics();
        return expanded(new RectF(x, baseline + fm.top, x + width, baseline + fm.bottom), paintInk());
    }

    private float paintInk() {
        return 5 * d + (p.getStyle() == Paint.Style.STROKE ? p.getStrokeWidth() / 2 : 0);
    }

    private static RectF expanded(RectF bounds, float by) {
        bounds.inset(-by, -by);
        return bounds;
    }

    private void clockText(Canvas c, RectF bounds, String value, float x, float y) {
        if (bounds != null) add(bounds, textBounds(value, x, y));
        if (c != null) c.drawText(value, x, y, p);
    }

    private void clockDateText(Canvas c, RectF bounds, RectF dateBounds, String value, float x, float y) {
        if (dateBounds != null) add(dateBounds, textBounds(value, x, y));
        clockText(c, bounds, value, x, y);
    }

    private void clockRotatedText(Canvas c, RectF bounds, String value, float x, float y, float degrees) {
        if (bounds != null) {
            RectF rotated = textBounds(value, x, y);
            Matrix matrix = new Matrix();
            matrix.setRotate(degrees, x, y);
            matrix.mapRect(rotated);
            add(bounds, rotated);
        }
        if (c != null) {
            c.save();
            c.rotate(degrees, x, y);
            c.drawText(value, x, y, p);
            c.restore();
        }
    }

    private void clockDateRotatedText(Canvas c, RectF bounds, RectF dateBounds,
                                      String value, float x, float y, float degrees) {
        if (dateBounds != null) {
            RectF rotated = textBounds(value, x, y);
            Matrix matrix = new Matrix();
            matrix.setRotate(degrees, x, y);
            matrix.mapRect(rotated);
            add(dateBounds, rotated);
        }
        clockRotatedText(c, bounds, value, x, y, degrees);
    }

    private void clockRect(Canvas c, RectF bounds, float left, float top, float right, float bottom) {
        if (bounds != null) add(bounds, expanded(new RectF(left, top, right, bottom), paintInk()));
        if (c != null) c.drawRect(left, top, right, bottom, p);
    }

    private void clockRoundRect(Canvas c, RectF bounds, RectF rect, float radius) {
        if (bounds != null) add(bounds, expanded(new RectF(rect), paintInk()));
        if (c != null) c.drawRoundRect(rect, radius, radius, p);
    }

    private void clockCircle(Canvas c, RectF bounds, float x, float y, float radius) {
        if (bounds != null) add(bounds, expanded(new RectF(x - radius, y - radius, x + radius, y + radius), paintInk()));
        if (c != null) c.drawCircle(x, y, radius, p);
    }

    private void clockLine(Canvas c, RectF bounds, float x1, float y1, float x2, float y2) {
        if (bounds != null) add(bounds, expanded(new RectF(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2)), paintInk()));
        if (c != null) c.drawLine(x1, y1, x2, y2, p);
    }

    private static void add(RectF all, RectF part) {
        if (part.isEmpty()) return;
        if (all.isEmpty()) all.set(part); else all.union(part);
    }

    /** The custom-drawn clock still exposes its complete value to accessibility and UI automation. */
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int hh = h24 ? hour : (hour % 12 == 0 ? 12 : hour % 12);
        String hs = h24 ? String.format(Locale.ROOT, "%02d", hh) : String.valueOf(hh);
        info.setText(hs + ":" + String.format(Locale.ROOT, "%02d", now.get(Calendar.MINUTE))
                + " · " + fmt("EEEE, MMMM d", now));
        if (outlook != null && drawsDate()) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_OPEN_DATE, T.t(getContext(), outlook.opensAgenda() ? "Open agenda" : "Open calendar")));
        }
    }

    /** The same thing a tap on the drawn date does, for people who cannot aim at it. */
    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (action != ACTION_OPEN_DATE || outlook == null || !drawsDate()) return super.performAccessibilityAction(action, args);
        outlook.onDateTap();
        return true;
    }

    private void drawStyle(Canvas c, RectF bounds, RectF dateBounds, Calendar now, int hour, int min, int hh,
                           String hs, String ms, float w, float h) {
        switch (style) {
            case "basic": {
                text(64, true);
                clockText(c, bounds, hs + ":" + ms, 0, h - p.descent() / 2);
                break;
            }
            case "horizontal": {
                text(48, true);
                String t = hs + ":" + ms;
                float base = h - p.descent() / 2;
                clockText(c, bounds, t, 0, base);
                float x = p.measureText(t) + 14 * d;
                p.setColor(accent);
                clockRect(c, bounds, x, base - 40 * d, x + 2 * d, base);
                text(16, true);
                clockDateText(c, bounds, dateBounds, fmt("EEEE", now), x + 12 * d, base - 22 * d);
                clockDateText(c, bounds, dateBounds, fmt("d MMMM", now), x + 12 * d, base - 2 * d);
                break;
            }
            case "words": {
                text(30, false);
                String[] lines = words(hh, hour, min);
                float y = p.getTextSize();
                for (String l : lines) {
                    if (l.isEmpty()) continue;
                    clockText(c, bounds, l, 0, y);
                    y += p.getTextSize() * 1.1f;
                }
                break;
            }
            case "dayofweek": {
                // Huge outlined weekday with the time and date set into a cut band across its middle.
                int layer = c == null ? 0 : c.saveLayer(0, 0, w, h, null);
                text(120, false);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * d);
                p.setTextAlign(Paint.Align.CENTER);
                p.setShadowLayer(0, 0, 0, 0);
                String day = fmt("EEE", now).toUpperCase(locale).replace(".", "");
                float base = h / 2 + (p.getTextSize() * 0.36f);
                clockText(c, bounds, day, w / 2, base);
                if (c != null) {
                    p.setStyle(Paint.Style.FILL);
                    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    c.drawRect(0, h / 2 - 16 * d, w, h / 2 + 16 * d, p);
                    c.restoreToCount(layer);
                }
                text(20, false);
                p.setLetterSpacing(0.15f);
                float mid = h / 2 + p.getTextSize() * 0.35f;
                float dayW;
                text(120, false);
                dayW = p.measureText(day);
                text(20, false);
                p.setLetterSpacing(0.15f);
                p.setTextAlign(Paint.Align.LEFT);
                clockText(c, bounds, hs + ":" + ms, w / 2 - dayW / 2, mid);
                p.setTextAlign(Paint.Align.RIGHT);
                clockDateText(c, bounds, dateBounds, fmt("MMM d", now).toUpperCase(locale), w / 2 + dayW / 2, mid);
                p.setLetterSpacing(0);
                break;
            }
            case "vertical": {
                // Tall narrow outlined digits side by side, the date running up the left edge.
                text(200, false);
                p.setTextScaleX(0.42f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5 * d);
                p.setColor(color & 0xDDFFFFFF);
                String digits = String.format(Locale.ROOT, "%02d%02d", hh, min);
                float x0 = 34 * d;
                clockText(c, bounds, digits, x0, h - 8 * d);
                text(22, true);
                p.setLetterSpacing(0.05f);
                clockDateRotatedText(c, bounds, dateBounds, fmt("EEEE, MMMM d", now).toUpperCase(locale),
                        24 * d, h - 12 * d, -90);
                p.setLetterSpacing(0);
                break;
            }
            case "flip": {
                String all = hs.length() == 1 ? "0" + hs + ms : hs + ms;
                float cw = 60 * d, ch = 86 * d, gap = 6 * d, x = 0;
                for (int i = 0; i < 4; i++) {
                    if (i == 2) x += gap * 3;
                    RectF r = new RectF(x, 4 * d, x + cw, 4 * d + ch);
                    p.setShadowLayer(0, 0, 0, 0);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xCC15121F);
                    clockRoundRect(c, bounds, r, 10 * d);
                    text(64, true);
                    p.setTextAlign(Paint.Align.CENTER);
                    clockText(c, bounds, String.valueOf(all.charAt(i)), r.centerX(),
                            r.centerY() + p.getTextSize() * 0.36f);
                    p.setColor(0x99000000);
                    clockRect(c, bounds, r.left, r.centerY() - d, r.right, r.centerY() + d);
                    x += cw + gap;
                }
                break;
            }
            case "analog": {
                float r = Math.min(h, w) / 2 - 6 * d, cx = r + 6 * d, cy = h / 2;
                p.setShadowLayer(4 * d, 0, d, 0x55000000);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(color);
                p.setStrokeWidth(3 * d);
                clockCircle(c, bounds, cx, cy, r);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12;
                    float in = r - (i % 3 == 0 ? 14 : 8) * d;
                    clockLine(c, bounds, cx + (float) Math.sin(a) * in, cy - (float) Math.cos(a) * in,
                            cx + (float) Math.sin(a) * (r - 3 * d), cy - (float) Math.cos(a) * (r - 3 * d));
                }
                double ha = Math.PI * 2 * ((hour % 12) + min / 60.0) / 12, ma = Math.PI * 2 * min / 60.0;
                p.setStrokeWidth(6 * d);
                clockLine(c, bounds, cx, cy, cx + (float) Math.sin(ha) * r * 0.5f,
                        cy - (float) Math.cos(ha) * r * 0.5f);
                p.setStrokeWidth(4 * d);
                p.setColor(accent);
                clockLine(c, bounds, cx, cy, cx + (float) Math.sin(ma) * r * 0.78f,
                        cy - (float) Math.cos(ma) * r * 0.78f);
                p.setStyle(Paint.Style.FILL);
                clockCircle(c, bounds, cx, cy, 5 * d);
                break;
            }
            case "none":
                break;
            default: { // overlap: big hours, smaller minutes sharing the baseline and tucked against them
                text(96, true);
                float base = h - p.descent() * 0.6f;
                clockText(c, bounds, hs, 0, base);
                float x = p.measureText(hs) + 4 * d;
                text(66, true);
                clockText(c, bounds, ms, x, base);
            }
        }
    }

    private String[] words(int hh, int hour, int min) {
        return locale.getLanguage().equals("it") ? Words.italian(h24 ? hour : hh, min)
                : new String[]{"it's", Words.hour(hh), Words.minutes(min)};
    }

    private String fmt(String pattern, Calendar c) {
        if (!locale.equals(formatsLocale)) { formats.clear(); formatsLocale = locale; }
        SimpleDateFormat f = formats.computeIfAbsent(pattern, k -> new SimpleDateFormat(pattern, locale));
        f.setTimeZone(TimeZone.getDefault()); // as a fresh one would be, should the zone change
        return f.format(c.getTime());
    }
}
