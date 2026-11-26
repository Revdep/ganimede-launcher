package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.Morph;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The quick button in a bottom corner: tap, double tap, triple tap and long press each run the action chosen in
 * settings (Actions). Drag it anywhere: on release it glides to the nearest side edge and stays anchored there.
 */
final class QuickButton {
    private final Ui ui;
    final TextView view;
    private int taps;
    private final Runnable fire;

    QuickButton(Ui ui, Handler handler, Actions.Home home) {
        this.ui = ui;
        fire = () -> {
            int n = Gestures.action(taps);
            taps = 0;
            Actions.run(ui.a, home, ui.prefs.getString(Actions.GESTURES[n], Actions.DEFAULTS[n]));
        };
        view = new TextView(ui.a);
        view.setText("✳");
        view.setTextSize(24);
        view.setGravity(Gravity.CENTER);
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) { outline.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        view.setOnClickListener(v -> {
            taps++;
            handler.removeCallbacks(fire);
            boolean multi = !"none".equals(ui.prefs.getString("fab_double", "none")) || !"none".equals(ui.prefs.getString("fab_triple", "none"));
            if (Gestures.firesNow(taps, multi)) fire.run(); // nothing waits for more taps: answer at once
            else handler.postDelayed(fire, ViewConfiguration.getDoubleTapTimeout());
        });
        view.setOnLongClickListener(v -> {
            Actions.run(ui.a, home, ui.prefs.getString("fab_long", Actions.DEFAULTS[3]));
            return true;
        });
        float slop = ui.slop();
        float[] down = new float[4];
        boolean[] dragging = new boolean[1];
        view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX(); down[1] = e.getRawY(); down[2] = v.getX(); down[3] = v.getY();
                    dragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!dragging[0] && Math.hypot(dx, dy) > slop * 2) {
                        dragging[0] = true;
                        v.cancelLongPress();
                        v.setPressed(false);
                    }
                    if (dragging[0]) {
                        View parent = (View) v.getParent();
                        v.setX(Math.max(0, Math.min(parent.getWidth() - v.getWidth(), down[2] + dx)));
                        v.setY(Math.max(0, Math.min(parent.getHeight() - v.getHeight(), down[3] + dy)));
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging[0]) return false;
                    View parent = (View) v.getParent();
                    boolean left = Gestures.leftEdge(v.getX(), v.getWidth(), parent.getWidth());
                    float x = left ? ui.dp(16) : parent.getWidth() - v.getWidth() - ui.dp(16);
                    float yFrac = v.getY() / Math.max(1, parent.getHeight() - v.getHeight());
                    Anim.to(v, Morph.SNAP_MS).x(x).start();
                    ui.prefs.edit().putBoolean("fab_left", left).putFloat("fab_y", yFrac).apply();
                    dragging[0] = false;
                    return true;
            }
            return false;
        });
    }

    /** Shown only when switched on in settings (and never over the app list). */
    void show(boolean homeShown) {
        view.setVisibility(ui.prefs.getBoolean("fab", false) && homeShown ? View.VISIBLE : View.GONE);
    }

    /** The accent's colour, and where it was last anchored. */
    void style(boolean homeShown) {
        paint(view, ui.prefs, ui.accent);
        view.setElevation(ui.dp(6));
        show(homeShown);
        if (!ui.prefs.contains("fab_y")) return;
        float yFrac = ui.prefs.getFloat("fab_y", 1);
        boolean left = ui.prefs.getBoolean("fab_left", false);
        view.post(() -> {
            View parent = (View) view.getParent();
            if (parent == null || parent.getWidth() == 0) return;
            view.setX(left ? ui.dp(16) : parent.getWidth() - view.getWidth() - ui.dp(16));
            view.setY(yFrac * (parent.getHeight() - view.getHeight()));
        });
    }

    /** The one rule for the button's icon (star, gray, colours or the custom image), shared by the home and the preview. */
    static void paint(TextView button, SharedPreferences prefs, int accent) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(accent);
        button.setBackground(dot);
        button.setText("✳");
        button.setTextColor(Colors.on(accent));
        button.setContentDescription(T.t(button.getContext(), "Ganimede button"));
        switch (prefs.getString("fab_icon", "default")) {
            case "gray" -> image(button, button.getContext().getDrawable(R.drawable.ganimede_gray));
            case "colour" -> image(button, button.getContext().getDrawable(R.drawable.ganimede_colour));
            case "custom" -> customImage(button, prefs);
        }
    }

    private static void image(TextView button, android.graphics.drawable.Drawable icon) {
        button.setText("");
        button.setBackground(icon);
    }

    /** The private image is decoded once off the main thread; until then (or if missing) the star stays visible. */
    private static void customImage(TextView button, SharedPreferences prefs) {
        if (ButtonIcon.loaded()) {
            useCustom(button, prefs, ButtonIcon.cached());
            return;
        }
        Context app = button.getContext().getApplicationContext();
        WeakReference<TextView> target = new WeakReference<>(button);
        new Thread(() -> {
            Bitmap bitmap = ButtonIcon.custom(app);
            TextView b = target.get();
            if (b == null) return;
            b.post(() -> {
                if (b.isAttachedToWindow() && "custom".equals(prefs.getString("fab_icon", "default"))) useCustom(b, prefs, bitmap);
            });
        }, "button-icon").start();
    }

    /** A missing or undecodable image (a restore onto another phone) falls back to the default, in the preference too. */
    private static void useCustom(TextView button, SharedPreferences prefs, Bitmap bitmap) {
        if (bitmap != null) image(button, new BitmapDrawable(button.getResources(), bitmap));
        else prefs.edit().putString("fab_icon", "default").apply(); // the pref listener restyles the button
    }
}
