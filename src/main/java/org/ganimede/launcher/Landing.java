package org.ganimede.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.ganimede.launcher.core.Target;

/**
 * The swipe-home animation onto the app's icon (Android's "gesture nav contract"). Swiping home from an app,
 * Android's recents service puts a "gesture_nav_contract_v1" bundle in the home intent (the closing app's component
 * and user, and a reply message). Answering with that app's icon position makes the system shrink the window
 * straight into the icon; the icon stays hidden until the system says the window has landed. No answer (an app not
 * on the home screen) keeps the default animation.
 */
final class Landing {
    private final Activity a;
    private final Layout layout;
    private final Handler handler;
    /** Home icons and favourite rows by entry key, as last drawn. */
    private final Map<String, List<View>> icons = new HashMap<>();
    private String openedKey;
    private View openedView;
    /** Which home icon is hidden while the closing window flies into it ("component" or "component#user"). */
    private String landingKey;
    private View landingView;
    private int landingIndex = -1;
    private SurfaceView surface;
    private final Runnable timeout = this::showIcon;
    private final Handler done = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message m) {
            showIcon();
        }
    };

    Landing(Activity a, Layout layout, Handler handler) {
        this.a = a;
        this.layout = layout;
        this.handler = handler;
    }

    void clear() {
        icons.clear();
    }

    /** Remembers where an entry's icon is drawn; one a window is still flying into stays hidden. */
    void track(String entry, View v) {
        List<View> duplicates = icons.computeIfAbsent(entry, k -> new ArrayList<>());
        int index = duplicates.size();
        duplicates.add(v);
        if (Target.isLandingIcon(entry, index, landingKey, landingIndex)) {
            landingView = v;
            Launcher.iconIn(v).setAlpha(0);
        }
    }

    /** The precise duplicate (page or dock) that started the app. */
    void opened(String entry, View from) {
        openedKey = entry;
        openedView = from;
    }

    /**
     * Whether the home intent just delivered may come from Android's recents component (the only one that sends the
     * contract): a sender that names itself (Android 15+) must be it.
     */
    private boolean fromRecents() {
        // Trade-off: before Android 15, and for senders that don't name themselves (the system's recents never does),
        // any app may ask; requiring a name would break the animation, and the answer only says where an icon is.
        if (Build.VERSION.SDK_INT < 35) return true;
        try {
            int id = Resources.getSystem().getIdentifier("config_recentsComponentName", "string", "android");
            ComponentName cn = ComponentName.unflattenFromString(id == 0 ? "" : Resources.getSystem().getString(id));
            if (cn == null) return true; // this Android doesn't say: as before
            int uid = a.getCurrentCaller().getUid();
            if (uid == Process.INVALID_UID || uid == Process.SYSTEM_UID) return true;
            return uid == a.getPackageManager().getPackageUid(cn.getPackageName(), 0);
        } catch (Exception e) { // no caller to ask about, or the recents app isn't there: the default animation plays
            return false;
        }
    }

    /** Answers the contract in a home intent, if it carries one for an app whose icon is on screen. */
    void answer(Intent intent) {
        if (Build.VERSION.SDK_INT < 30 || intent == null) return;
        Bundle extras;
        try {
            extras = intent.getBundleExtra("gesture_nav_contract_v1");
        } catch (RuntimeException bad) { // any app can send "home": a bundle that won't unpack is ignored
            return;
        }
        if (extras == null || !fromRecents()) return;
        intent.removeExtra("gesture_nav_contract_v1");
        ComponentName cn;
        UserHandle user;
        Message reply;
        try {
            cn = extras.getParcelable(Intent.EXTRA_COMPONENT_NAME);
            user = extras.getParcelable(Intent.EXTRA_USER);
            reply = extras.getParcelable("android.intent.extra.REMOTE_CALLBACK");
        } catch (RuntimeException bad) { // wrong types from another app: ignored
            return;
        }
        if (cn == null || user == null || reply == null || reply.replyTo == null) return;
        String key = Target.key(cn.flattenToString(), user.equals(Process.myUserHandle()) ? -1
                : a.getSystemService(UserManager.class).getSerialNumberForUser(user));
        String opened = openedView != null && openedView.isShown() ? openedKey : null;
        List<String> onScreen = new ArrayList<>();
        for (String k : icons.keySet()) if (shown(k) != null) onScreen.add(k);
        String landed = Target.pick(key, cn.getPackageName(), opened, onScreen, layout::folderApps);
        View row = landed.equals(key) && key.equals(opened) ? openedView : shown(landed);
        openedKey = null;
        openedView = null;
        if (row == null) return;
        key = landed;
        int duplicate = icons.getOrDefault(key, List.of()).indexOf(row);
        View icon = Launcher.iconIn(row);
        Rect visible = new Rect();
        if (!icon.getGlobalVisibleRect(visible) || visible.isEmpty()) return;
        RectF pos = new RectF(visible);
        // A small surface showing the icon, handed to the system: the window lands on it and turns into the icon in
        // one piece.
        SurfaceView sv = new SurfaceView(a);
        sv.setZOrderOnTop(true);
        sv.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        int[] w = new int[2];
        icon.getLocationInWindow(w);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(icon.getWidth(), icon.getHeight());
        lp.leftMargin = w[0];
        lp.topMargin = w[1];
        boolean[] sent = new boolean[1];
        // First the position alone, at once (the window heads straight for the icon), then again with the surface.
        Consumer<Boolean> answerWith = withSurface -> {
            Bundle result = new Bundle();
            result.putParcelable("gesture_nav_contract_icon_position", pos);
            if (withSurface && Build.VERSION.SDK_INT >= 29 && sv.getHolder().getSurface().isValid())
                result.putParcelable("gesture_nav_contract_surface_control", sv.getSurfaceControl());
            Message finished = Message.obtain();
            finished.replyTo = new Messenger(done); // sent back when the window has landed
            result.putParcelable("gesture_nav_contract_finish_callback", finished);
            Message answer = Message.obtain();
            answer.copyFrom(reply);
            answer.setData(result);
            try {
                answer.replyTo.send(answer);
            } catch (Exception e) {
                showIcon(); // the recents service went away: the default animation plays
            }
        };
        Runnable send = () -> {
            if (sent[0]) return;
            sent[0] = true;
            answerWith.accept(true);
        };
        answerWith.accept(false);
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                try {
                    Canvas c = h.lockCanvas();
                    if (c != null) {
                        c.drawColor(0, PorterDuff.Mode.CLEAR);
                        icon.draw(c); // the icon as it looks on the home screen
                        h.unlockCanvasAndPost(c);
                    }
                } catch (RuntimeException surfaceGone) {
                    // the window still aims at the icon's position
                }
                send.run();
            }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int wd, int ht) {}
            @Override public void surfaceDestroyed(SurfaceHolder h) {}
        });
        showIcon(); // a previous landing still pending
        ((ViewGroup) a.getWindow().getDecorView()).addView(sv, lp);
        surface = sv;
        handler.postDelayed(send, 150); // no surface in time: the position alone still aims the window at the icon
        landingKey = key;
        landingView = row;
        landingIndex = duplicate;
        icon.setAlpha(0);
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 1200); // in case the "landed" message never comes
    }

    private void showIcon() {
        View row = landingView;
        landingKey = null;
        landingView = null;
        landingIndex = -1;
        if (row != null) Launcher.iconIn(row).setAlpha(1);
        if (surface != null) {
            ((ViewGroup) a.getWindow().getDecorView()).removeView(surface);
            surface = null;
        }
    }

    private View shown(String key) {
        for (View v : icons.getOrDefault(key, List.of())) if (v.isShown()) return v;
        return null;
    }
}
