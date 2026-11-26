package org.ganimede.launcher;

import org.ganimede.launcher.core.Morph;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ScrollView;

/** The two curves and helpers every animation goes through; durations live in Morph. */
final class Anim {
    /** Off quickly, then a long gentle landing: for what the app starts (closing lands as opening does). */
    static final Interpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    static final Interpolator GLIDE = new DecelerateInterpolator(1.5f); // a release the finger's speed carries on
    interface Step { void at(float p); }
    /** False when the system animation scale is 0: callers then jump to the end state. */
    static boolean on() { return ValueAnimator.areAnimatorsEnabled(); }
    /** v.animate() with EASE and ms; the caller adds properties and start(). */
    static ViewPropertyAnimator to(View v, long ms) {
        ViewPropertyAnimator a = v.animate().setDuration(ms).setInterpolator(EASE).setStartDelay(0); // a stagger left by enter must not delay the next trip
        // Alpha's offscreen is clipped to the viewport; don't layer an arbitrarily tall scroller child.
        return v.getLayerType() == View.LAYER_TYPE_HARDWARE || v.getParent() instanceof ScrollView ? a : a.withLayer();
    }
    /** Fade and slide v in from (dx, dy) px after delay ms; nothing when animations are off. */
    static void enter(View v, float dx, float dy, long delay) {
        if (!on()) return;
        v.setAlpha(0);
        v.setTranslationX(dx);
        v.setTranslationY(dy);
        to(v, Morph.OPEN_MS).alpha(1).translationX(0).translationY(0).setStartDelay(delay).start();
    }
    /** One trip from..to: each frame calls step; end (never null) runs only if the trip was not cancelled. */
    static ValueAnimator run(float from, float to, long ms, Interpolator curve, Step step, Runnable end) {
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(ms).setInterpolator(curve);
        va.addUpdateListener(a -> step.at((float) a.getAnimatedValue()));
        va.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) { if (!cancelled) end.run(); }
        });
        return va;
    }
    private Anim() {}
}
