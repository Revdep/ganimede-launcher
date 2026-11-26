package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Snap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The editing layer over the preview of the home screen. A tap picks a brick; a drag moves it on the 4 dp grid, and it
 * clings to guide lines (screen edges, the middle, its neighbours' edges) when it comes close; the round knob on the
 * picked brick sizes it. Everything is saved as it changes, so the preview underneath is always the real result.
 */
final class BrickOverlay extends View {
    /** OFF: a plain miniature. PICK: a tap picks a brick (the settings home). EDIT: bricks are also moved and sized. */
    static final int OFF = 0, PICK = 1, EDIT = 2;
    private static final int STEP = 4, REACH = 6; // dp: the grid, and how near a guide line pulls

    int mode = OFF;
    Brick[] bricks = {};
    String selected = Brick.SCREEN;
    /** How much the preview is shrunk: lines and knobs keep their size on screen. */
    float scale = 1;
    /** Where bricks may go, 4 dp inside the home area (preview pixels). */
    final RectF area = new RectF();
    /** A setting changed mid-drag (redraw the preview); a gesture ended on this brick (show its controls). */
    Runnable onChange;
    Consumer<String> onPicked;

    private final SharedPreferences p;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Brick held;
    private boolean knob, moved;
    private float downX, downY;
    private final RectF from = new RectF();
    private int x0, y0, size0, guideX = Snap.NONE, guideY = Snap.NONE;
    private int[] linesX, linesY;
    private String readout;

    BrickOverlay(Context c, SharedPreferences p) {
        super(c);
        this.p = p;
        dp = getResources().getDisplayMetrics().density;
    }

    private float on(float screenDp) {
        return screenDp * dp / scale;
    }

    private Brick picked() {
        return Brick.find(bricks, selected);
    }

    private float knobY(Brick b) {
        return b.grow < 0 ? b.at.top : b.at.bottom;
    }

    /**
     * The brick under a finger: the smallest one it is on, else the smallest within reach (a brick touched just
     * inside a neighbour's reach stays the one touched), else the one whose band of the screen it is in.
     */
    private Brick hit(float x, float y) {
        Brick best = smallest(x, y, 0);
        if (best == null) best = smallest(x, y, on(10));
        if (best == null) for (Brick b : bricks) if (b.shown && b.at.width() > getWidth() / 3f && y >= b.at.top && y <= b.at.bottom) best = b;
        return best;
    }

    private Brick smallest(float x, float y, float reach) {
        Brick best = null;
        for (Brick b : bricks) {
            if (!b.shown || x < b.at.left - reach || x > b.at.right + reach || y < b.at.top - reach || y > b.at.bottom + reach) continue;
            if (best == null || b.at.width() * b.at.height() < best.at.width() * best.at.height()) best = b;
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mode == OFF) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Brick sel = mode == EDIT ? picked() : null;
                knob = sel != null && sel.shown && sel.grow != 0 && Math.hypot(x - sel.at.centerX(), y - knobY(sel)) < on(22);
                held = knob ? sel : hit(x, y);
                moved = false;
                downX = x;
                downY = y;
                if (mode == EDIT) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (mode != EDIT || held == null) return true;
                if (!moved && Math.hypot(x - downX, y - downY) < ViewConfiguration.get(getContext()).getScaledTouchSlop() / scale) return true;
                if (!moved) grab();
                if (knob) size((y - downY) * held.grow); else move(x - downX, y - downY);
                if (onChange != null) onChange.run();
                return true;
            case MotionEvent.ACTION_UP:
                if (onPicked != null) onPicked.accept(moved ? selected : held == null ? Brick.SCREEN : held.id);
                // fall through: the gesture is over
            case MotionEvent.ACTION_CANCEL:
                held = null;
                readout = null;
                guideX = guideY = Snap.NONE;
                invalidate();
                return true;
        }
        return true;
    }

    /** A drag begins: remember where the brick and its settings were, and which lines it may cling to. */
    private void grab() {
        moved = true;
        selected = held.id;
        from.set(held.at);
        x0 = held.x(p);
        y0 = held.y(p);
        size0 = held.ksize == null ? 0 : held.size(p);
        // The limits and the middle first, then the neighbours' edges (sideways, only those a brick can reach).
        List<Integer> xs = new ArrayList<>(Arrays.asList(toDp(area.left), toDp(area.right), toDp(getWidth() / 2f)));
        List<Integer> ys = new ArrayList<>(Arrays.asList(toDp(area.top), toDp(area.bottom), toDp(getHeight() / 2f)));
        for (Brick b : bricks) {
            if (!b.shown || b == held) continue;
            if (b.at.left >= area.left && b.at.left <= area.right) xs.add(toDp(b.at.left));
            if (b.at.right >= area.left && b.at.right <= area.right) xs.add(toDp(b.at.right));
            ys.add(toDp(b.at.top));
            ys.add(toDp(b.at.bottom));
        }
        linesX = xs.stream().mapToInt(Integer::intValue).toArray();
        linesY = ys.stream().mapToInt(Integer::intValue).toArray();
    }

    private int toDp(float px) {
        return Math.round(px / dp);
    }

    private void move(float dx, float dy) {
        int right = held.right(p);
        // A brick that fills its row up to a fixed far edge is aligned by the edge that moves.
        float lead = !held.stretches() ? from.left : right > 0 ? from.left : from.right;
        int width = held.stretches() ? 0 : toDp(from.width());
        int[] mx = Snap.magnet(toDp(lead) + Snap.grid(toDp(dx), STEP), width, linesX, REACH);
        if (!held.stretches() && from.left >= area.left - 1 && from.right <= area.right + 1) {
            // Kept whole inside its column: pushed further, its text would wrap against the limit.
            int most = (int) Math.floor(area.right / dp) - (int) Math.ceil(from.width() / dp);
            mx[0] = Math.max(toDp(area.left), Math.min(most, mx[0]));
        }
        int[] my = Snap.magnet(toDp(from.top) + Snap.grid(toDp(dy), STEP), toDp(from.height()), linesY, REACH);
        int wantX = x0 + right * (mx[0] - toDp(lead)), wantY = y0 + my[0] - toDp(from.top);
        int x = held.clampX(wantX), y = held.clampY(wantY);
        guideX = x == wantX ? mx[1] : Snap.NONE; // a guide only shows when the brick really sits on it
        guideY = y == wantY ? my[1] : Snap.NONE;
        // "last_moved": this brick goes where asked and pushes its neighbours.
        p.edit().putInt(held.kx, x).putInt(held.ky, y).putString("last_moved", held.ky).apply();
        readout = String.format(java.util.Locale.ROOT, "x %+d · y %+d", (x - held.xdef) * right, y);
    }

    /** pull: how far the knob was dragged outward, in preview pixels. */
    private void size(float pull) {
        if (held.centred()) pull *= 2;
        float h = Math.max(1, from.height());
        int v = held.sizedInDp() ? Math.max(60, toDp(h + pull)) : Math.round(Math.max(1, size0) * (h + pull) / h);
        v = held.clampSize(Snap.grid(v, held.sstep));
        p.edit().putInt(held.ksize, v).putString("last_moved", held.ky).apply();
        readout = v + held.unit;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (mode != EDIT) return;
        int accent = Ui.accent(p);
        Brick sel = picked();
        paint.setStyle(Paint.Style.STROKE);
        for (Brick b : bricks) {
            if (!b.shown) continue;
            boolean on = b == sel;
            paint.setColor(on ? accent : 0x8CFFFFFF);
            paint.setStrokeWidth(on(on ? 2 : 1));
            paint.setPathEffect(on ? null : new DashPathEffect(new float[]{on(5), on(4)}, 0));
            float r = on(10), out = on(3);
            c.drawRoundRect(b.at.left - out, b.at.top - out, b.at.right + out, b.at.bottom + out, r, r, paint);
        }
        paint.setPathEffect(null);
        paint.setColor(accent);
        paint.setStrokeWidth(on(1));
        if (guideX != Snap.NONE) c.drawLine(guideX * dp, 0, guideX * dp, getHeight(), paint);
        if (guideY != Snap.NONE) c.drawLine(0, guideY * dp, getWidth(), guideY * dp, paint);
        if (sel == null || !sel.shown) return;
        if (sel.grow != 0) { // the knob: pull it to size the brick
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(sel.at.centerX(), knobY(sel), on(8), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(on(2));
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(sel.at.centerX(), knobY(sel), on(8), paint);
        }
        if (readout != null) tag(c, readout, sel.at.centerX(), sel.at.bottom + on(22), accent);
    }

    /** A small pill of text centred on x, kept inside the preview. */
    private void tag(Canvas c, String s, float x, float y, int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(on(13));
        paint.setTextAlign(Paint.Align.CENTER);
        float w = paint.measureText(s) / 2 + on(10), h = on(12);
        x = Math.max(w + on(4), Math.min(getWidth() - w - on(4), x));
        y = Math.max(h + on(4), Math.min(getHeight() - h - on(4), y));
        paint.setColor(accent);
        c.drawRoundRect(x - w, y - h, x + w, y + h, h, h, paint);
        paint.setColor(Colors.on(accent));
        c.drawText(s, x, y + on(4.5f), paint);
    }
}
