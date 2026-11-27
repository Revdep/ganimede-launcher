package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Morph;
import org.ganimede.launcher.core.Wave;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * The A–Z strip by the screen edge (right, or left in left-hand mode). Touch its letters to jump; a vertical drag from
 * the opposite edge is handed here by MainActivity after it clears touch slop, so rows underneath keep their actions.
 */
public class AlphabetView extends View {
    interface Listener {
        void onLetter(String letter);
    }

    static final String STAR = "☆";
    private String[] letters = {STAR};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d, sp;
    private float touchY = -1, lastY = -1, lastX = -1;
    private int picked = -1;
    Listener listener;
    Runnable onDoubleTap;
    int accent = Colors.ACCENT, color = 0xFFFFFFFF;
    boolean haptics = true, showLetters = true;
    /** Theme: letter size in sp (16 by default), distance of the column from the screen edge in dp. */
    float sizeSp = 16, offsetDp = 0;
    private long lastTap;
    boolean oppositeEdgeDrag;
    private float progress; // 0..1, the wave grows in on touch and shrinks back on release
    private final android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(0, 1);

    public AlphabetView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        setContentDescription("Alphabet: touch and slide to jump to apps");
        grow.setDuration(Morph.CLOSE_MS);
        grow.setInterpolator(Anim.EASE);
        grow.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
    }

    void setLetters(Collection<String> l) {
        letters = Stream.concat(Stream.of(STAR), l.stream()).toArray(String[]::new);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        grow.cancel(); // a running wave would keep invalidating a view no longer on screen
        super.onDetachedFromWindow();
    }

    private boolean mirrored() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    /** {text size, row step, column top}: the same numbers the Settings preview draws with. */
    private float[] geometry() {
        return Wave.geometry(letters.length, sizeSp * sp, getHeight(), d);
    }

    private float textSize() { return geometry()[0]; }

    private float step() { return geometry()[1]; }

    /** Where the letters start and end (px in this view), for the Settings preview's brick. */
    float top() { return geometry()[2]; }

    /** Where the 58dp touch column begins, from this view's left edge (the right side, or the left in left-hand mode). */
    float columnStart() {
        return mirrored() ? offsetDp * d : getWidth() - (58 + offsetDp) * d;
    }

    float height() { return step() * letters.length; }

    /** Distance of the letters' centre from the edge they sit on: half the 58dp column plus the theme offset. */
    private float centre() {
        return (29 + offsetDp) * d;
    }

    /** Horizontal distance of a point from the edge where the letters are drawn. */
    private float fromEdge(float x) {
        return mirrored() ? x : getWidth() - x;
    }

    /** The 58dp touch column on the side where the letters are drawn. */
    boolean inColumn(float x) {
        return fromEdge(x) <= (58 + offsetDp) * d;
    }

    private float amplitude() {
        return lastX < 0 ? 0 : Wave.amplitude(fromEdge(lastX), getWidth(), progress, d, sp);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!showLetters && progress == 0) return;
        float step = step(), top = top(), amp = amplitude(), height = step * letters.length;
        float x0 = mirrored() ? centre() : getWidth() - centre();
        paint.setTextSize(textSize());
        paint.setAlpha(255);
        // Shadow radius 4, dy 2 (integer/shadow_radius, integer/shadow_dy), under light letters only.
        paint.setShadowLayer(Colors.luminance(color) > 0.5f ? 4 * d : 0, 0, 2 * d, 0x66000000);
        for (int i = 0; i < letters.length; i++) {
            float y = top + step * (i + 0.5f);
            float pull = lastY < 0 ? 0 : Wave.pull(i, letters.length, lastY, top, step, height, amp);
            paint.setColor(i == picked ? accent : color);
            if (!showLetters) paint.setAlpha((int) (255 * progress)); // hidden letters fade in while touched
            c.drawText(letters[i], mirrored() ? x0 + pull : x0 - pull, y + paint.getTextSize() / 3, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false; // the Settings preview draws this view, it doesn't use it
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                boolean onLetters = inColumn(e.getX());
                if (!onLetters && !oppositeEdgeDrag) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                // Double-tap the alphabet: Quick Lock (the system double-tap window).
                if (onLetters && e.getEventTime() - lastTap < ViewConfiguration.getDoubleTapTimeout() && onDoubleTap != null) {
                    lastTap = 0;
                    onDoubleTap.run();
                    return true;
                }
                if (onLetters) lastTap = e.getEventTime();
                // fall through
            case MotionEvent.ACTION_MOVE:
                touchY = lastY = e.getY();
                lastX = e.getX();
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { grow.cancel(); grow.setFloatValues(progress, 1); grow.start(); }
                int i = Wave.index(touchY, top(), step(), letters.length);
                if (i != picked) {
                    picked = i;
                    if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (listener != null) listener.onLetter(letters[i]);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchY = -1;
                picked = -1;
                grow.cancel();
                grow.setFloatValues(progress, 0); // the letters glide back from where the finger left them
                grow.start();
                return true;
        }
        return false;
    }
}
