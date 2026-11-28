package org.ganimede.launcher;

import org.ganimede.launcher.core.Summary;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notification access: unread notifications next to apps, the media player, and the notification summary
 * (non-urgent notifications of chosen apps are snoozed by Android until the next summary time).
 */
public class NotifService extends NotificationListenerService {
    static final Map<String, List<StatusBarNotification>> active = new HashMap<>();
    static volatile Runnable listener;
    static volatile boolean connected;
    private static volatile NotifService self;
    private static final Set<String> URGENT = Set.of(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM,
            Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_ERROR, Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_PROGRESS);

    @Override
    public void onListenerConnected() {
        connected = true;
        self = this;
        worker.post(() -> { // the notes posted while this app wasn't listening, as long as they are still shared
            try {
                VoicePlayer.keep(this, getActiveNotifications());
            } catch (RuntimeException e) {
                Log.w("Ganimede", "Voice notes not kept: " + e.getClass().getName());
            }
        });
        update();
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        self = null;
        synchronized (active) {
            active.clear();
        }
        worker.post(() -> lastSeen = ""); // reconnected, the same notifications must be read in again
        // Not through the worker: onDestroy quits it before Android calls this. No access, no kept voice notes; a rebind
        // with access on keeps them, a note's grant may be gone by then.
        if (!Ui.notifAllowed(this)) VoicePlayer.sweep(this, null, 0);
        tell();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification s, RankingMap rank) {
        try {
            VoicePlayer.keep(this, s); // now, held for the summary or not: a chat app may withdraw the audio when it re-posts
            // summary_held is read and written on the worker only: the hold below posts back to it, so no write is lost
            worker.post(() -> {
                try {
                    if (!maybeBatch(s, rank)) update();
                } catch (RuntimeException e) {
                    Log.w("Ganimede", "Notification skipped: " + e.getClass().getName());
                }
            });
        } catch (RuntimeException e) { // runs in the home's process: one odd notification must not take it down
            Log.w("Ganimede", "Notification skipped: " + e.getClass().getName());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification s) {
        try {
            update();
        } catch (RuntimeException e) {
            Log.w("Ganimede", "Notification skipped: " + e.getClass().getName());
        }
    }

    /** Keys whose hold is queued but not yet written; only the worker thread touches it. */
    private final Set<String> holding = new HashSet<>();

    /** Summary: snooze a non-urgent notification of a chosen app until the next summary time. */
    private boolean maybeBatch(StatusBarNotification s, RankingMap rank) {
        SharedPreferences p = Prefs.of(this);
        // held keys are in prefs, not memory: after a process restart Android still re-posts them at the summary
        long at = System.currentTimeMillis();
        Set<String> held = p.getBoolean("summary", false) ? Summary.expire(p.getStringSet("summary_held", Set.of()), at) : Set.of();
        if (held.size() != p.getStringSet("summary_held", Set.of()).size()) p.edit().putStringSet("summary_held", held).apply(); // switched off or stale
        if (Summary.cameBack(Summary.due(held, s.getKey()), at)) {
            Set<String> rest = new HashSet<>(held);
            rest.removeIf(e -> e.substring(0, e.lastIndexOf('|')).equals(s.getKey()));
            p.edit().putStringSet("summary_held", rest).apply();
            return false;
        }
        if (Summary.isDue(p.getLong("summary_last", 0), System.currentTimeMillis())) return false;
        Notification n = s.getNotification();
        Ranking r = new Ranking();
        boolean known = rank != null && rank.getRanking(s.getKey(), r);
        if (!Summary.waits(new Summary.Rules(p.getBoolean("summary", false),
                p.getStringSet("summary_apps", Set.of()).contains(s.getPackageName()),
                p.getBoolean("summary_skip_chats", true), p.getBoolean("summary_silent", false)),
                new Summary.Note(s.isClearable(), (n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0,
                        n.category != null && URGENT.contains(n.category), n.fullScreenIntent != null,
                        Notification.CATEGORY_MESSAGE.equals(n.category) || (known && Build.VERSION.SDK_INT >= 31 && r.isConversation()),
                        known && r.getImportance() < android.app.NotificationManager.IMPORTANCE_DEFAULT))) return false;
        if (!holding.add(s.getKey())) return true; // a re-post inside the window: its hold is already queued
        long now = System.currentTimeMillis(), last = Summary.roll(p.getLong("summary_last", 0), now);
        p.edit().putLong("summary_last", last).apply();
        long wait = Summary.upcoming(last, now) - now;
        String key = s.getKey();
        // Snoozing revokes the chat app's audio grants at once (NotificationManagerService.SnoozeNotificationRunnable ->
        // cancelNotificationLocked -> updateUriPermissions(null, r, ...)): its voice notes are kept first. Counted as held
        // only once snoozed: unbound meanwhile, snoozeNotification does nothing.
        VoicePlayer.afterKept(() -> {
            try {
                if (!connected) return;
                snoozeNotification(key, wait);
                worker.post(() -> {
                    Set<String> stored = p.getStringSet("summary_held", Set.of());
                    p.edit().putStringSet("summary_held", Summary.hold(stored, key, now + wait, now))
                            .putInt("summary_count", Summary.counted(p.getInt("summary_count", 0), stored, key)).apply();
                });
            } catch (RuntimeException e) { // access just lost: shown, not held
                Log.w("Ganimede", "Summary hold failed: " + e.getClass().getName());
                update();
            } finally {
                worker.post(() -> holding.remove(key));
            }
        });
        return true;
    }

    /** Reads the notifications off the main thread: each read crosses into the system and unpacks every one. */
    private Handler worker;
    private volatile boolean updatePending;

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread t = new HandlerThread("notifications");
        t.start();
        worker = new Handler(t.getLooper());
    }

    @Override
    public void onDestroy() {
        worker.getLooper().quitSafely();
        super.onDestroy();
    }

    /**
     * Re-reads the notifications at most every 250 ms (the only throttle: the home redraws when told): a download's
     * progress posts many times a second. Throttled, not debounced, so a stream of updates still shows.
     */
    private void update() {
        if (updatePending) return;
        updatePending = true;
        worker.postDelayed(() -> {
            updatePending = false;
            try {
                read();
            } catch (RuntimeException e) { // as in the callbacks: never take the home's process down
                Log.w("Ganimede", "Notifications not read: " + e.getClass().getName());
            }
        }, 250);
    }

    /** What the home was last told about (keys and texts); only the worker thread touches it. */
    private String lastSeen = "";

    private void read() {
        StatusBarNotification[] all;
        long snapshot = System.currentTimeMillis(); // before the read: a note kept after it is not in `all`
        try {
            all = getActiveNotifications();
        } catch (RuntimeException e) {
            return;
        }
        if (all == null) all = new StatusBarNotification[0];
        sweepVoice(all, snapshot);
        Map<String, List<StatusBarNotification>> m = new HashMap<>();
        for (StatusBarNotification s : all) {
            Notification n = s.getNotification();
            if (!s.isClearable() || (n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY)) != 0) continue;
            m.computeIfAbsent(group(s.getPackageName(), s.getUser()), k -> new ArrayList<>()).add(s);
        }
        // A download's progress re-posts the same notification many times: the home only hears when what it shows changes.
        StringBuilder sig = new StringBuilder();
        for (List<StatusBarNotification> l : m.values()) for (StatusBarNotification s : l) {
            Bundle ex = s.getNotification().extras;
            sig.append(s.getKey()).append('|').append(safeText(ex, "android.title")).append('|')
                    .append(safeText(ex, "android.text")).append('\n');
        }
        String now = sig.toString();
        if (now.equals(lastSeen)) return;
        lastSeen = now;
        synchronized (active) {
            active.clear();
            active.putAll(m);
        }
        tell();
    }

    /** Kept voice notes stay while their notification is shown or held for the summary. */
    private void sweepVoice(StatusBarNotification[] all, long snapshot) {
        if (!VoicePlayer.hasKept(this)) return; // nothing to delete: skip the call for the snoozed ones
        try {
            List<StatusBarNotification> l = new ArrayList<>(Arrays.asList(all));
            StatusBarNotification[] held = getSnoozedNotifications();
            if (held != null) l.addAll(Arrays.asList(held));
            VoicePlayer.sweep(this, l, snapshot);
        } catch (RuntimeException e) { // access revoked mid-read: the next read sweeps
            Log.w("Ganimede", "Voice notes not swept: " + e.getClass().getName());
        }
    }

    /** Notifications are grouped per app and profile: the same app in the work profile is another group. */
    static String group(String pkg, android.os.UserHandle user) {
        return pkg + "@" + user;
    }

    /** A text from a notification's extras, or null when they can't be unpacked. */
    static CharSequence safeText(Bundle ex, String key) {
        try {
            return ex == null ? null : ex.getCharSequence(key);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    static void tell() {
        Runnable r = listener;
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }

    /** Newest notification of a package, or null. */
    static StatusBarNotification latest(String pkg) {
        synchronized (active) {
            StatusBarNotification best = null;
            for (StatusBarNotification s : active.getOrDefault(pkg, List.of()))
                if (best == null || s.getPostTime() > best.getPostTime()) best = s;
            return best;
        }
    }

    static int count(String pkg) {
        synchronized (active) {
            return active.getOrDefault(pkg, List.of()).size();
        }
    }

    /** Dismisses one notification (key) or, with null, every clearable one. */
    static void dismiss(String key) {
        NotifService s = self;
        if (s == null) return;
        try {
            if (key == null) s.cancelAllNotifications(); else s.cancelNotification(key);
        } catch (Exception ignored) {
            // Listener just disconnected.
        }
    }

    /** Every unread notification, one run per app (for the pop-up's one header per app): newest first, apps by their newest. */
    static List<StatusBarNotification> all() {
        Comparator<StatusBarNotification> newest = (a, b) -> Long.compare(b.getPostTime(), a.getPostTime());
        List<List<StatusBarNotification>> apps = new ArrayList<>();
        synchronized (active) {
            for (List<StatusBarNotification> l : active.values()) {
                if (l.isEmpty()) continue;
                List<StatusBarNotification> app = new ArrayList<>(l);
                app.sort(newest);
                apps.add(app);
            }
        }
        apps.sort((a, b) -> newest.compare(a.get(0), b.get(0)));
        List<StatusBarNotification> out = new ArrayList<>();
        for (List<StatusBarNotification> app : apps) out.addAll(app);
        return out;
    }

    /** The media session that is playing (or paused most recently), or null. Needs notification access. */
    static MediaController media(Context c) {
        try {
            List<MediaController> l = c.getSystemService(MediaSessionManager.class)
                    .getActiveSessions(new ComponentName(c, NotifService.class));
            for (MediaController m : l) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return m;
            }
            for (MediaController m : l) {
                PlaybackState s = m.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PAUSED && m.getMetadata() != null) return m;
            }
        } catch (Exception ignored) {
            // No notification access yet.
        }
        return null;
    }
}
