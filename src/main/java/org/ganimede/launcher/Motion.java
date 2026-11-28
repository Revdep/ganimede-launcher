package org.ganimede.launcher;

import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.Morph;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/**
 * Every pop-up and bottom sheet of the home screen: a full-screen see-through window holding one card that grows out
 * of what was touched and shrinks back onto it (Morph has the geometry), over a dim that fades with it. However it
 * closes (a row, Back, a tap beside it, a pull, a drag taking over) the close plays first; a close during the
 * opening turns back from where it is.
 */
final class Motion extends Dialog {
    private static final int DIM = 0x99;

    private final Ui ui;
    private final View card;
    private final boolean sheet;
    private final ColorDrawable dim = new ColorDrawable(Color.BLACK);
    private final Rect origin = new Rect();
    private View from;
    /** Where the touch was when it opened: the origin when no view is there (or once it's gone). */
    private float pointX = -1, pointY = -1;
    private float p, start, drop;
    private ValueAnimator run;
    private boolean opened, closing, placed;

    /** A card that grows out of `from` (null: what was last touched), centred unless it brings its own layout. */
    static Motion pop(Ui ui, View card, View from) {
        Motion m = new Motion(ui, card, false);
        m.from = from;
        return m;
    }

    /** A full-width sheet at the bottom that rises leaning towards what was touched, and can be pulled down. */
    static Motion sheet(Ui ui, View content) {
        return new Motion(ui, content, true);
    }

    private Motion(Ui ui, View content, boolean sheet) {
        super(ui.a, android.R.style.Theme_DeviceDefault_NoActionBar);
        this.ui = ui;
        this.sheet = sheet;
        if (sheet) {
            ScrollView pull = new Pull();
            pull.addView(content);
            card = pull;
        } else {
            card = content;
            card.setClickable(true); // a tap on the card's own padding isn't a tap beside it
        }
        FrameLayout root = new FrameLayout(ui.a);
        root.setFitsSystemWindows(true);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); // Back closes it for screen readers
        root.setOnClickListener(v -> cancel());
        int m = Math.round(24 * ui.a.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = sheet ? new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
                : card.getLayoutParams() instanceof FrameLayout.LayoutParams ? (FrameLayout.LayoutParams) card.getLayoutParams()
                : new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        if (!sheet && card.getLayoutParams() == null) lp.setMargins(m, m, m, m);
        root.addView(card, lp);
        setContentView(root);
        Window w = getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setFormat(PixelFormat.TRANSLUCENT);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        w.setWindowAnimations(0); // the card moves, not the window
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void show() {
        super.show();
        View decor = ui.a.getWindow().getDecorView();
        pointX = ui.touchX;
        pointY = ui.touchY;
        if (from == null && pointX >= 0) from = hit(decor, (int) pointX, (int) pointY);
        dim.setAlpha(0);
        decor.getOverlay().add(dim); // under this window, over the home: the dim lives on the home's side
        // Folded onto its origin in the pre-draw of its first frame, once its size is known: nothing shows before.
        card.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                card.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!closing) go(1, Morph.OPEN_MS);
                return true;
            }
        });
    }

    /** Its close animation has started: on its way out, though still showing until the animation ends. */
    boolean closing() {
        return closing;
    }

    @Override
    public void dismiss() {
        if (closing) return;
        closing = true;
        if (!isShowing() || !card.isAttachedToWindow() || ui.a.isFinishing()) { end(); return; }
        Window w = getWindow();
        if (w != null) { // touches reach the home at once, and so does a drag that just took over
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            ui.a.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(card.getWindowToken(), 0);
        }
        go(0, Morph.CLOSE_MS);
    }

    /** Animates to open (1) or closed (0) from wherever it is now. */
    private void go(float to, long ms) {
        if (run != null) run.cancel();
        measure();
        run = Anim.run(p, to, Morph.ms(ms, p, to), Anim.EASE, this::at, () -> { // not on cancel: the next trip took over
            run = null;
            at(to);
            card.setLayerType(View.LAYER_TYPE_NONE, null);
            if (to == 0) end();
        });
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null); // drawn once, then only moved and faded
        at(p);
        // A new window reaches the screen a few frames after its first drawing: an opening waits for it, folded.
        if (!opened) run.setStartDelay(48);
        opened = true;
        run.start();
    }

    private void at(float progress) {
        p = progress;
        float s = Morph.scale(start, p);
        card.setScaleX(s);
        card.setScaleY(s);
        if (sheet) card.setTranslationY(Morph.drop(p, drop));
        else card.setAlpha(Morph.alpha(p));
        dim.setAlpha(Math.round(DIM * p));
    }

    private void end() {
        if (run != null) run.cancel();
        ui.a.getWindow().getDecorView().getOverlay().remove(dim);
        if (!ui.a.isDestroyed()) super.dismiss(); // else its window went with the screen it belonged to
    }

    /** Where the card is, untouched by its own movement, and where its origin is now (or was, once it's gone). */
    private void measure() {
        View root = (View) card.getParent();
        int[] xy = new int[2];
        root.getLocationOnScreen(xy);
        float left = xy[0] + card.getLeft(), top = xy[1] + card.getTop(), w = card.getWidth(), h = card.getHeight();
        View decor = ui.a.getWindow().getDecorView();
        dim.setBounds(0, 0, decor.getWidth(), decor.getHeight());
        if (from != null && from.isAttachedToWindow() && from.isShown()) {
            from.getLocationOnScreen(xy);
            origin.set(xy[0], xy[1], xy[0] + from.getWidth(), xy[1] + from.getHeight());
        } else if (!placed) { // nothing to grow from: the touch point itself, or else the card's middle
            int x = pointX >= 0 ? (int) pointX : (int) (left + w / 2), y = pointY >= 0 ? (int) pointY : (int) (top + h / 2);
            origin.set(x, y, x, y);
        }
        placed = true;
        if (sheet) {
            start = Morph.SHEET_SCALE;
            card.setPivotX(Morph.inside(origin.exactCenterX(), left, w));
            card.setPivotY(h);
            drop = root.getHeight() - card.getTop(); // all the way below the screen's edge
        } else {
            start = Morph.startScale(origin.width(), origin.height(), w, h);
            card.setPivotX(Morph.pivot(left, w, origin.exactCenterX(), start));
            card.setPivotY(Morph.pivot(top, h, origin.exactCenterY(), start));
        }
    }

    /** The innermost tappable view under a point of the screen, or null. */
    private static View hit(View v, int x, int y) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() == 0) return null;
        int[] xy = new int[2];
        v.getLocationOnScreen(xy);
        if (x < xy[0] || y < xy[1] || x >= xy[0] + v.getWidth() || y >= xy[1] + v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View h = hit(g.getChildAt(i), x, y);
                if (h != null) return h;
            }
        }
        return v.isClickable() || v.isLongClickable() ? v : null;
    }

    /**
     * The sheet's scroll box: once scrolled to its top it can be pulled down. It follows the finger and closes past
     * a quarter of its height or on a quick flick (Gestures.sheetCloses), otherwise springs back.
     */
    private final class Pull extends ScrollView {
        final float slop = ViewConfiguration.get(ui.a).getScaledTouchSlop(), flick = 800 * ui.a.getResources().getDisplayMetrics().density;
        float startX, startY;
        boolean pulling;
        VelocityTracker vt;

        Pull() {
            super(ui.a);
        }

        /** True once the finger has moved down past the touch slop with the content at its top. */
        boolean startsPull(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { startX = e.getRawX(); startY = e.getRawY(); pulling = false; }
            else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && !pulling && !closing && !canScrollVertically(-1)
                    && e.getRawY() > startY && Gestures.startsDrag(e.getRawY() - startY, e.getRawX() - startX, slop)) {
                pulling = true;
                startY = e.getRawY();
                setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            return pulling;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return startsPull(e) || super.onInterceptTouchEvent(e);
        }

        //noinspection ClickableViewAccessibility: a pull to close (Back does the same); every other touch goes to super
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            // Touches on plain text reach here directly, without passing onInterceptTouchEvent first.
            if (!pulling && !startsPull(e)) return super.onTouchEvent(e);
            if (vt == null) vt = VelocityTracker.obtain();
            MotionEvent onScreen = MotionEvent.obtain(e);
            onScreen.setLocation(e.getRawX(), e.getRawY()); // the sheet moves with the finger: measure on the screen
            vt.addMovement(onScreen);
            onScreen.recycle();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (run != null) run.cancel();
                    run = null;
                    at(Morph.pulled(e.getRawY() - startY, drop));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    vt.computeCurrentVelocity(1000);
                    boolean away = Gestures.sheetCloses(e.getActionMasked() == MotionEvent.ACTION_UP,
                            Morph.drop(p, drop), getHeight(), vt.getYVelocity(), flick);
                    vt.recycle();
                    vt = null;
                    pulling = false;
                    if (away) dismiss();
                    else go(1, Morph.OPEN_MS);
                    break;
            }
            return true;
        }
    }
}
