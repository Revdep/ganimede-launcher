package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.Morph;
import org.ganimede.launcher.core.Safe;

import android.app.ActivityOptions;
import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;

/** Notifications on the home screen: the bubble under an app's name, the pop-up of all of them, one opened whole. */
final class Notices {
    private final Ui ui;
    private final Apps apps;
    private final View root;
    private final BiConsumer<Item, View> launch;
    private final VoicePlayer voice;

    Notices(Ui ui, Apps apps, View root, BiConsumer<Item, View> launch) {
        this.ui = ui;
        this.apps = apps;
        this.root = root;
        this.launch = launch;
        voice = new VoicePlayer(ui);
    }

    /** The home is leaving the screen: a voice note stops and its copy is deleted. */
    void pause() {
        voice.stop();
    }

    /**
     * The newest notification under an app's name: a small rounded bubble with the sender in bold and the message on
     * a second line, so it reads as one clear message instead of a run-on line. Tapping it opens the message.
     */
    View bubble(StatusBarNotification sbn, Item it, int count) {
        Safe.Shown shown = text(sbn, apps.label(it), false, null);
        CharSequence title = shown.title(), body = shown.body();
        LinearLayout bubble = new LinearLayout(ui.a);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPaddingRelative(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ui.light() ? 0x40000000 : 0x26FFFFFF);
        bg.setCornerRadius(ui.dp(14));
        bg.setStroke(Math.max(1, ui.dp(1)), Colors.alpha(ui.accent, 0x55));
        bubble.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        LinearLayout head = new LinearLayout(ui.a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView who = ui.plain(13, true, ui.fg);
        who.setSingleLine(true);
        who.setEllipsize(TextUtils.TruncateAt.END);
        who.setText(title == null ? apps.label(it) : title);
        head.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
        TextView when = ui.plain(11, false, Colors.alpha(ui.fg, 0xAA));
        when.setText((count > 1 ? "+" + (count - 1) + " · " : "") + ago(sbn.getPostTime()));
        when.setPaddingRelative(ui.dp(8), 0, 0, 0);
        head.addView(when);
        bubble.addView(head);
        if (body != null && body.length() > 0) {
            TextView msg = ui.plain(13, false, Colors.alpha(ui.fg, 0xDD));
            msg.setMaxLines(2);
            msg.setEllipsize(TextUtils.TruncateAt.END);
            msg.setText(body);
            msg.setPaddingRelative(0, ui.dp(2), 0, 0);
            bubble.addView(msg);
        }
        voice.addRows(sbn, bubble);
        // Tap or swipe: a scrollable preview of this app's messages; tapping one there opens it.
        bubble.setOnClickListener(v -> show(it.notifGroup));
        //noinspection ClickableViewAccessibility: the swipe opens what the click opens
        bubble.setOnTouchListener(swipeOpens(it.notifGroup));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = ui.dp(5);
        bubble.setLayoutParams(lp);
        return bubble;
    }

    /**
     * Slide sideways (towards the middle of the screen: right, or left in left-hand mode) for an app's notifications;
     * the view follows the finger a little and springs back. A touch listener runs before the view's own click
     * handling, so a clickable view keeps its tap.
     */
    View.OnTouchListener swipeOpens(String pkg) {
        float slop = ui.slop();
        float[] down = new float[2];
        boolean[] fired = new boolean[1];
        //noinspection ClickableViewAccessibility: a swipe; taps fall through to the view's own click
        return (v, e) -> {
            boolean mirrored = ui.prefs.getBoolean("left_handed", false);
            float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
            if (mirrored) dx = -dx;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    fired[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (fired[0] || dx <= 0 || !Gestures.startsDrag(dx, dy * 2, slop)) return false; // a clearly sideways swipe, forwards only
                    v.cancelLongPress();
                    v.setPressed(false);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    v.animate().cancel(); // a snap-back still running must not fight the finger
                    v.setAlpha(1);
                    v.setTranslationY(0);
                    v.setTranslationX(Math.min(dx, ui.dp(48)) * (mirrored ? -1 : 1));
                    if (dx > ui.dp(56)) {
                        fired[0] = true;
                        Anim.to(v, Morph.SNAP_MS).translationX(0).start();
                        show(pkg);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (v.getTranslationX() != 0) { Anim.to(v, Morph.SNAP_MS).translationX(0).start(); return true; }
                    return fired[0];
            }
            return false;
        };
    }

    /** TalkBack moves the focus with a swipe: the pop-up is also an action of the row while the app has notifications. */
    void accessibleOpens(View row, String pkg) {
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (NotifService.count(pkg) > 0)
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_notifications, ui.tr("Notifications")));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action != R.id.action_notifications) return super.performAccessibilityAction(host, action, args);
                show(pkg);
                return true;
            }
        });
    }

    /**
     * All notifications (or one app's), grouped by app: a header with the app's icon and name, then one card per
     * message (title, text, how long ago) with its own dismiss button, so each message clearly starts and ends.
     */
    void show(String onlyPkg) {
        if (!NotifService.connected) {
            ui.toast(ui.tr("Allow notification access in settings first"));
            ui.settings();
            return;
        }
        List<StatusBarNotification> all = NotifService.all();
        if (onlyPkg != null) all.removeIf(s -> !NotifService.group(s.getPackageName(), s.getUser()).equals(onlyPkg));
        LinearLayout box = new LinearLayout(ui.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(12));
        GradientDrawable sheet = new GradientDrawable();
        sheet.setColor(0xF2151221);
        sheet.setCornerRadius(ui.dp(28));
        box.setBackground(sheet);
        Dialog[] dlg = new Dialog[1];

        LinearLayout head = new LinearLayout(ui.a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.plain(22, true, Colors.WHITE);
        title.setText(all.isEmpty() ? ui.tr("No notifications") : ui.tr("Notifications") + "  ·  " + all.size());
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = ui.plain(15, true, Colors.onDark(ui.accent));
        if (!all.isEmpty()) {
            clear.setText(ui.tr("Clear all"));
            clear.setPaddingRelative(ui.dp(12), ui.dp(12), ui.dp(4), ui.dp(12));
            clear.setOnClickListener(v -> { NotifService.dismiss(null); dlg[0].dismiss(); });
            head.addView(clear);
        }
        box.addView(head);

        int[] remaining = {all.size()};
        String lastPkg = null, lastName = null;
        LinearLayout group = null;
        for (StatusBarNotification s : all) {
            String grp = NotifService.group(s.getPackageName(), s.getUser());
            Item it = apps.byGroup(grp);
            if (!grp.equals(lastPkg)) {
                lastPkg = grp;
                lastName = it == null ? appName(s.getPackageName()) : apps.label(it); // once per group, not per message
                String appName = lastName;
                group = new LinearLayout(ui.a);
                group.setOrientation(LinearLayout.VERTICAL);
                box.addView(group);
                LinearLayout app = new LinearLayout(ui.a);
                app.setGravity(Gravity.CENTER_VERTICAL);
                app.setPaddingRelative(ui.dp(4), ui.dp(18), 0, ui.dp(8));
                if (it != null) {
                    ImageView ic = new ImageView(ui.a);
                    ic.setImageDrawable(Icons.plain(it.icon, Colors.onDark(ui.accent)));
                    app.addView(ic, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
                }
                TextView name = ui.plain(14, true, Colors.onDark(ui.accent));
                name.setText(appName.toUpperCase(T.locale(ui.a)));
                name.setLetterSpacing(0.08f);
                name.setPaddingRelative(ui.dp(10), 0, 0, 0);
                app.addView(name);
                group.addView(app);
            }
            String appName = lastName;
            // Notifications drawn by the app itself (timers, players) carry no text: say what a tap does.
            Safe.Shown shown = text(s, appName, true, ui.tr("Tap to open"));
            CharSequence t = shown.title(), body = shown.body();
            SwipeCard card = new SwipeCard();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPaddingRelative(ui.dp(16), ui.dp(12), ui.dp(6), ui.dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x26FFFFFF);
            bg.setCornerRadius(ui.dp(18));
            card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
            LinearLayout text = new LinearLayout(ui.a);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(ui.a);
            TextView tt = ui.plain(16, true, Colors.WHITE);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            tt.setText(t == null ? "" : t);
            top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
            TextView when = ui.plain(12, false, 0xAAFFFFFF);
            when.setText(ago(s.getPostTime()));
            when.setPaddingRelative(ui.dp(8), ui.dp(2), 0, 0);
            top.addView(when);
            text.addView(top);
            if (body != null && body.length() > 0) {
                TextView b = ui.plain(14, false, 0xDDFFFFFF);
                b.setText(body);
                b.setPaddingRelative(0, ui.dp(4), 0, 0);
                // The whole message, scrolling inside its own card when it's long (up to about eight lines tall).
                text.addView(capped(b, ui.dp(160)));
                addLinkButtons(text, body);
            }
            card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            CharSequence fullTitle = t, fullBody = body;
            card.setOnLongClickListener(v -> { showOne(s, it, appName, fullTitle, fullBody, dlg[0], card); return true; });
            // The text and its scroll box take touches too: a tap or a long-press anywhere on the card does the same.
            passTouches(text, card);
            voice.addRows(s, text); // after passTouches: the players keep their own touches
            if (s.isClearable()) {
                TextView x = ui.plain(18, false, 0xAAFFFFFF);
                x.setText("✕");
                x.setGravity(Gravity.CENTER);
                x.setContentDescription(ui.tr("Dismiss"));
                x.setOnClickListener(v -> card.throwAway());
                card.addView(x, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            }
            card.setOnClickListener(v -> { dlg[0].dismiss(); open(s, it); });
            LinearLayout cardGroup = group;
            if (s.isClearable()) card.setDismiss(() -> {
                NotifService.dismiss(s.getKey());
                cardGroup.removeView(card);
                if (cardGroup.getChildCount() == 1) box.removeView(cardGroup);
                if (--remaining[0] == 0) {
                    title.setText(ui.tr("No notifications"));
                    clear.setVisibility(View.GONE);
                } else title.setText(ui.tr("Notifications") + "  ·  " + remaining[0]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = ui.dp(8);
            group.addView(card, lp);
        }
        dlg[0] = Motion.sheet(ui, box); // a full-width sheet from the bottom, like the other menus
        dlg[0].setOnDismissListener(x -> voice.stop());
        dlg[0].show();
    }

    /**
     * Long-press on a notification: it opens on its own, whole. Long texts scroll inside the pop-up, which never grows
     * past 70% of the screen; its links open from here, and "Open" goes to the app's own screen for it.
     */
    private void showOne(StatusBarNotification s, Item it, String appName, CharSequence title, CharSequence body, Dialog list, View from) {
        SwipeCard box = new SwipeCard();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(16));
        GradientDrawable sheet = new GradientDrawable();
        sheet.setColor(0xF7151221);
        sheet.setCornerRadius(ui.dp(28));
        box.setBackground(sheet);
        TextView app = ui.plain(13, true, Colors.onDark(ui.accent));
        app.setText(appName.toUpperCase(T.locale(ui.a)) + "  ·  " + ago(s.getPostTime()));
        app.setLetterSpacing(0.08f);
        box.addView(app);
        TextView t = ui.plain(19, true, Colors.WHITE);
        t.setText(title == null ? "" : title);
        t.setPaddingRelative(0, ui.dp(8), 0, ui.dp(8));
        box.addView(t);
        TextView b = ui.plain(15, false, 0xEEFFFFFF);
        b.setText(body == null ? "" : body);
        b.setTextIsSelectable(true);
        b.setLineSpacing(0, 1.2f);
        box.addView(capped(b, ui.a.getResources().getDisplayMetrics().heightPixels * 55 / 100));
        if (body != null) addLinkButtons(box, body);
        voice.addRows(s, box);
        Dialog[] d = new Dialog[1];
        TextView open = ui.plain(15, true, Colors.on(ui.accent));
        open.setText(ui.tr("Open"));
        open.setGravity(Gravity.CENTER);
        open.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(ui.dp(24));
        pill.setColor(ui.accent);
        open.setBackground(pill);
        open.setOnClickListener(v -> { d[0].dismiss(); if (list != null) list.dismiss(); open(s, it); });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
        ol.topMargin = ui.dp(16);
        box.addView(open, ol);
        if (s.isClearable()) box.setDismiss(() -> {
            NotifService.dismiss(s.getKey());
            d[0].dismiss();
            if (list != null) list.dismiss();
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.setMargins(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24));
        box.setLayoutParams(lp);
        d[0] = Motion.pop(ui, box, from); // grows out of the card, shrinks back into it
        d[0].setOnDismissListener(x -> voice.stop());
        d[0].show();
    }

    /** Opens what the notification points at, or else its app. */
    private void open(StatusBarNotification sbn, Item it) {
        voice.stop(); // opening the message ends private playback and removes every cached copy first
        PendingIntent pi = sbn.getNotification().contentIntent;
        try {
            if (pi == null) throw new PendingIntent.CanceledException();
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions o = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(ui.a, 0, null, null, null, null, o.toBundle());
            } else {
                pi.send();
            }
        } catch (PendingIntent.CanceledException e) {
            if (it != null) launch.accept(it, root);
        }
    }

    /** All of a notification's words: the expanded text, an inbox's lines, or a chat's messages with their senders. */
    private static CharSequence fullText(StatusBarNotification s) {
        try {
            Bundle ex = s.getNotification().extras;
            Parcelable[] msgs = ex.getParcelableArray("android.messages");
            if (msgs != null && msgs.length > 0) {
                SpannableStringBuilder out = new SpannableStringBuilder();
                for (Parcelable m : msgs) {
                    if (!(m instanceof Bundle)) continue;
                    CharSequence who = ((Bundle) m).getCharSequence("sender"), what = ((Bundle) m).getCharSequence("text");
                    if (what == null) continue;
                    if (out.length() > 0) out.append('\n');
                    if (who != null) out.append(who).append(": ");
                    out.append(what);
                }
                if (out.length() > 0) return out;
            }
            CharSequence[] lines = ex.getCharSequenceArray("android.textLines");
            if (lines != null && lines.length > 0) return TextUtils.join("\n", lines);
            CharSequence body = ex.getCharSequence("android.bigText");
            if (body == null) body = ex.getCharSequence("android.text");
            if (body == null) body = ex.getCharSequence("android.subText");
            return body;
        } catch (RuntimeException unreadable) { // an app's own classes inside: Android can't unpack them here
            return null;
        }
    }

    private static Safe.Shown text(StatusBarNotification s, String appName, boolean expanded, CharSequence hint) {
        Notification n = s.getNotification();
        return Safe.notice(Safe.secret(n.visibility), NotifService.safeText(n.extras, "android.title"),
                expanded ? fullText(s) : NotifService.safeText(n.extras, "android.text"), appName, hint);
    }

    /** Taps and long-presses on the pieces of a card (but not its own buttons) act as on the card itself. */
    private static void passTouches(View v, View card) {
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) passTouches(((ViewGroup) v).getChildAt(i), card);
        if (v.hasOnClickListeners()) return; // "Open link" keeps its own job
        v.setOnLongClickListener(x -> card.performLongClick());
        if (v instanceof TextView || v instanceof ScrollView) v.setOnClickListener(x -> card.performClick());
    }

    /** A view inside a scroll box that grows with it up to maxPx, then scrolls (inside a scrolling pop-up too). */
    private View capped(View v, int maxPx) {
        ScrollView s = new ScrollView(ui.a) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST));
            }
        };
        s.setNestedScrollingEnabled(true);
        s.setVerticalScrollBarEnabled(true);
        s.setFadingEdgeLength(ui.dp(16));
        s.setVerticalFadingEdgeEnabled(true);
        s.addView(v);
        return s;
    }

    /** "Open link" buttons for the web addresses in a message, so a link can be seen without opening the app. */
    private void addLinkButtons(LinearLayout into, CharSequence text) {
        java.util.regex.Matcher m = android.util.Patterns.WEB_URL.matcher(text);
        List<String> found = new ArrayList<>();
        while (m.find()) found.add(m.group());
        List<String> seen = Safe.webLinks(found);
        for (String url : seen) {
            int fg = Colors.onDark(ui.accent);
            TextView b = ui.plain(14, true, fg);
            Glyph g = new Glyph("link", fg);
            g.setBounds(0, 0, ui.dp(18), ui.dp(18));
            b.setCompoundDrawablesRelative(g, null, null, null);
            b.setCompoundDrawablePadding(ui.dp(6));
            b.setText(seen.size() > 1 ? ui.tr("Open link") + " · " + Uri.parse(url).getHost() : ui.tr("Open link"));
            b.setPaddingRelative(ui.dp(12), ui.dp(8), ui.dp(14), ui.dp(8));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(ui.dp(18));
            pill.setColor(Colors.alpha(ui.accent, 0x26));
            b.setBackground(pill);
            // Safe.webLinks only lets http(s) addresses through
            b.setOnClickListener(v -> ui.open(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = ui.dp(8);
            into.addView(b, lp);
        }
    }

    /** A card claims a horizontal drag before its clickable children or either surrounding scroll gesture can. */
    private final class SwipeCard extends LinearLayout {
        private final float slop = ui.slop();
        private float startX, startY;
        private boolean dragging;
        private Runnable dismiss;

        SwipeCard() {
            super(ui.a);
        }

        void setDismiss(Runnable dismiss) {
            this.dismiss = dismiss;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (dismiss == null) return super.onInterceptTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = e.getRawX();
                startY = e.getRawY();
                dragging = false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && horizontal(e)) {
                beginDrag();
                return true;
            }
            return dragging || super.onInterceptTouchEvent(e);
        }

        //noinspection ClickableViewAccessibility: a throw-away swipe (the list cards have a Dismiss button too); taps go to super
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (dismiss == null) return super.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = e.getRawX();
                startY = e.getRawY();
                dragging = false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && horizontal(e)) {
                beginDrag();
            }
            if (!dragging) return super.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - startX;
                setTranslationX(dx);
                setAlpha(Math.max(0.15f, 1 - Math.abs(dx) / Math.max(1, getWidth())));
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                setLayerType(LAYER_TYPE_NONE, null); // so that the settle puts it back this way
                if (e.getActionMasked() == MotionEvent.ACTION_UP && Gestures.thrown(getTranslationX(), getWidth())) throwAway();
                else Anim.to(this, Morph.SNAP_MS).translationX(0).alpha(1).start();
                dragging = false;
            }
            return true;
        }

        private boolean horizontal(MotionEvent e) {
            float dx = e.getRawX() - startX, dy = e.getRawY() - startY;
            return Gestures.startsDrag(dx, dy, slop);
        }

        private void beginDrag() {
            if (dragging) return;
            dragging = true;
            animate().cancel(); // a spring-back still running must not fight the finger
            setLayerType(LAYER_TYPE_HARDWARE, null); // drawn once, then only moved and faded
            getParent().requestDisallowInterceptTouchEvent(true);
            setPressed(false);
            cancelLongPress();
        }

        void throwAway() {
            if (dismiss == null) return;
            float side = getTranslationX() == 0 ? 1 : Math.signum(getTranslationX());
            if (ui.haptics()) performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            Anim.to(this, Morph.SNAP_MS).translationX(side * Math.max(1, getWidth())).alpha(0).withEndAction(dismiss).start();
        }
    }

    /** An installed package's own name (for system notifications that have no app in the list). */
    private String appName(String pkg) {
        try {
            PackageManager pm = ui.a.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private String ago(long when) {
        long min = (System.currentTimeMillis() - when) / 60_000;
        if (min < 1) return ui.tr("now");
        if (min < 60) return ui.f("%d min ago", min);
        if (min < 24 * 60) return ui.f("%d h ago", min / 60);
        return DateFormat.getDateFormat(ui.a).format(new Date(when));
    }
}
