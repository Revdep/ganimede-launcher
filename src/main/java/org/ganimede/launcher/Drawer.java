package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Gestures;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Classic mode's drawer follows the finger: up from anywhere on the home screen, down anywhere in the list at its top. */
final class Drawer {
    private final Ui ui;
    private final Layout layout;
    private final View root, home, pane;
    private final ListView list;
    private final AlphabetView alpha;
    private final BooleanSupplier listMode, searchEmpty;
    private final Runnable openList, quietHome;
    /** Lets the views under the finger go (no tap, no long-press) once the drawer takes the touch. */
    private final Consumer<MotionEvent> cancelUnder;
    /** 0 = no drag; 1 = opening from the home screen; -1 = closing from the list. */
    private int drag;
    private float dragX, dragY;
    private boolean fromTop;
    private VelocityTracker speed;
    private float open = 1;
    private int homeDim, listDim; // the veil's two ends, read once per drag or slide
    private ValueAnimator settling;

    Drawer(Ui ui, Layout layout, View root, View home, View pane, ListView list, AlphabetView alpha,
           BooleanSupplier listMode, BooleanSupplier searchEmpty, Runnable openList, Runnable quietHome,
           Consumer<MotionEvent> cancelUnder) {
        this.ui = ui;
        this.layout = layout;
        this.root = root;
        this.home = home;
        this.pane = pane;
        this.list = list;
        this.alpha = alpha;
        this.listMode = listMode;
        this.searchEmpty = searchEmpty;
        this.openList = openList;
        this.quietHome = quietHome;
        this.cancelUnder = cancelUnder;
    }

    /** A drag is moving the drawer (a fling then mustn't act on its own). */
    boolean dragging() {
        return drag != 0;
    }

    /** Follows a touch; true while it belongs to the drawer. */
    boolean follow(MotionEvent e) {
        if (root.getHeight() == 0) return false;
        boolean inList = listMode.getAsBoolean();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (settling != null) settling.end(); // still gliding: finish it, then this touch starts fresh
                drag = 0;
                dragX = e.getRawX();
                dragY = e.getRawY();
                fromTop = inList && !list.canScrollVertically(-1) && !alpha.inColumn(e.getRawX());
                if (speed != null) speed.recycle();
                speed = VelocityTracker.obtain();
                speed.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (speed != null) speed.addMovement(e);
                float dx = e.getRawX() - dragX, dy = e.getRawY() - dragY;
                if (drag == 0) {
                    float slop = ui.slop() * 2;
                    if (e.getEventTime() - e.getDownTime() > ViewConfiguration.getLongPressTimeout()) return false;
                    if (!Gestures.startsDrag(dy, dx * 1.5f, slop)) return false;
                    if (!inList && dy < 0 && layout.drawerOn() && !alpha.inColumn(dragX)) drag = 1;
                    else if (inList && dy > 0 && fromTop && searchEmpty.getAsBoolean()) drag = -1;
                    else return false;
                    dragX = e.getRawX(); // start from here: no jump
                    dragY = e.getRawY();
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    cancelUnder.accept(cancel);
                    cancel.recycle();
                    lift();
                    if (drag == 1) {
                        openList.run();
                        at(0);
                    }
                    return true;
                }
                float moved = -(e.getRawY() - dragY) / root.getHeight();
                at(drag == 1 ? moved : 1 + moved);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (drag == 0) return false;
                speed.computeCurrentVelocity(1000);
                float v = speed.getYVelocity();
                boolean opens = e.getActionMasked() == MotionEvent.ACTION_CANCEL ? drag == -1
                        : Gestures.drawerOpens(v, open, drag == 1, ui.dp(600));
                settle(opens, v);
                drag = 0;
                return true;
            }
        }
        return drag != 0;
    }

    /** Glides the drawer open or shut on its own, as a drag let go at the start would: a flick, Back, the keyboard closing. */
    void slide(boolean toOpen) {
        if (root.getHeight() == 0 || drag != 0 || listMode.getAsBoolean() == toOpen) return;
        if (settling != null) settling.end();
        if (toOpen) openList.run();
        lift();
        at(toOpen ? 0 : 1);
        settle(toOpen, 0);
    }

    /** Draws the drawer p of the way open (0 = home, 1 = list): it slides up over the home screen, which fades. */
    private void at(float p) {
        open = p = Math.max(0, Math.min(1, p));
        home.setVisibility(View.VISIBLE);
        pane.setTranslationY((1 - p) * root.getHeight());
        home.setAlpha(1 - p);
        alpha.setAlpha(p);
        root.setBackgroundColor(Colors.veil(Math.round(homeDim + (listDim - homeDim) * p), !ui.light()));
    }

    /** A drag or slide starts: both moving surfaces drawn once into GPU layers, then only moved; the veil's ends read. */
    private void lift() {
        pane.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        home.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        homeDim = ui.prefs.getInt("dim_home", 0);
        listDim = ui.prefs.getInt("dim", 60);
    }

    /** Finishes a drag: glides on at the finger's speed to open or closed, then leaves the screen in that state. */
    private void settle(boolean toOpen, float v) {
        float to = toOpen ? 1 : 0;
        settling = Anim.run(open, to, Gestures.settleMs(Math.abs(to - open) * root.getHeight(), v), Anim.GLIDE, this::at, () -> {
            settling = null;
            pane.setTranslationY(0);
            home.setAlpha(1);
            alpha.setAlpha(1);
            pane.setLayerType(View.LAYER_TYPE_NONE, null);
            home.setLayerType(View.LAYER_TYPE_NONE, null);
            open = 1;
            if (toOpen) openList.run();
            else quietHome.run(); // the home screen is already drawn where it belongs: nothing rebuilt, nothing replayed
        });
        settling.start();
    }
}
