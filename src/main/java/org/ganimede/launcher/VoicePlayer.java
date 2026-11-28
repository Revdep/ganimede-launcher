package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Latest;
import org.ganimede.launcher.core.Safe;
import org.ganimede.launcher.core.Voice;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.util.Log;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice notes in notifications, heard privately: the audio a chat app attaches to its notification is played by this
 * app, so the chat app's player never runs and can't send a "listened" receipt. One at a time.
 * A chat app may withdraw its earlier notes' audio when it re-posts the conversation (issue 083), so each note this app
 * may read is copied on arrival into its no-backup storage (out of backups) and kept until the note leaves every
 * notification, shown or held for the summary. Playback reads that kept copy; only a note not kept yet (not shared
 * at arrival) is copied into the cache for the time it plays, and that copy is deleted when playback stops.
 */
final class VoicePlayer {
    // Copies on arrival and deletions, in order: a deletion queued after a copy sees its file. Each copy waits at most COPY_MS.
    // Both are made on first use (a home with no voice note never starts them), as daemons: they never hold the process up.
    private static ExecutorService keeper;
    private static Handler watchdog; // off the main thread: a busy screen must not delay a cut
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet(); // apps with a kept copy whose thread has not returned
    private static final int BUSY_MAX = 2;
    // Notifications with notes waiting for a copy, the latest per key; one copy task drains them (SCHEDULED).
    private static final Latest<StatusBarNotification> WAITING = new Latest<>(Voice.QUEUE_MAX);
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    // When each copy kept by this process started, by file name: in ms, unlike File.lastModified(), and from before the
    // copy's end, so a sweep can tell a note queued after its snapshot from a slow copy of one already gone.
    private static final Map<String, Long> STARTED = new ConcurrentHashMap<>();
    private static final AudioAttributes SPEECH = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();

    private final Ui ui;
    private final File dir;
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Row> rows = new ArrayList<>(); // the rows on screen now
    private final Runnable tick = this::tick;
    private Uri current; // the note's URI in its notification, being copied or loaded: what identifies a row
    private MediaPlayer player;
    private boolean ready;
    private int run; // grows on every stop, so a copy that finishes after it is thrown away

    VoicePlayer(Ui ui) {
        this.ui = ui;
        dir = new File(ui.a.getCacheDir(), "voice");
        audio = ui.a.getSystemService(AudioManager.class);
        // Speech is paused rather than played under another sound.
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(SPEECH)
                .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(c -> { if (c < 0) stop("focus lost " + c); }, main).build();
    }

    /** A player row for each voice note of the notification this app may read (kept, or shared now), in the chat's order; none if it is secret. */
    void addRows(StatusBarNotification s, ViewGroup into) {
        for (Uri uri : notes(s)) {
            File kept = kept(ui.a, s, uri);
            if (kept.isFile() || granted(ui.a, uri)) into.addView(new Row(uri, kept));
        }
    }

    /**
     * On arrival: a private copy of each note this app may read now. Never of a secret notification, never of a note the
     * chat app did not share: those it withdraws before this app ever saw them get no row.
     */
    static void keep(Context c, StatusBarNotification... all) {
        boolean queued = false;
        for (StatusBarNotification s : all) if (!notes(s).isEmpty()) queued |= WAITING.offer(s.getKey(), s); // full: not kept; the sweep and the holds still queue
        if (!queued || !SCHEDULED.compareAndSet(false, true)) return;
        keeper().execute(() -> {
            SCHEDULED.set(false); // an arrival from now on schedules the next drain
            try {
                for (StatusBarNotification s : WAITING.drain()) for (Uri uri : notes(s)) {
                    File f = kept(c, s, uri);
                    if (f.isFile() || !granted(c, uri) || !Voice.room(used(f.getParentFile()))) continue;
                    long start = System.currentTimeMillis();
                    File part = copyAside(c.getContentResolver(), s.getPackageName(), uri, new File(f.getPath() + ".part")); // never half a note
                    if (part == null) continue;
                    if (part.renameTo(f)) STARTED.put(f.getName(), start); else part.delete();
                }
            } catch (RuntimeException e) { // runs in the home's process: one odd notification must not take it down
                Log.w("Ganimede", "Voice note not kept: " + e.getClass().getName());
            }
        });
    }

    /** Runs r after the copies already queued: snoozing a notification takes its audio grants back (issue 083). */
    static void afterKept(Runnable r) {
        keeper().execute(r);
    }

    /**
     * The copy on a thread of its own, waited for a little past the watchdog: Android can't cut short a call into another
     * app, so one whose open never returns strands that thread, not the queue. When it does return, the watchdog has
     * closed the stream and copy() deletes its half. An app with a copy still stranded gets no other until it returns,
     * and at most BUSY_MAX apps strand one at a time: one re-posting ever-new notes strands one thread and stalls the
     * queue once, not once per note.
     */
    private static File copyAside(ContentResolver cr, String app, Uri uri, File f) {
        if (BUSY.size() >= BUSY_MAX || !BUSY.add(app)) return null; // only this queue adds
        FutureTask<File> task = new FutureTask<>(() -> {
            try {
                return copy(cr, uri, f);
            } finally {
                BUSY.remove(app);
            }
        });
        new Thread(task, "voice-keep").start();
        try {
            return task.get(Voice.COPY_MS + 1000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static long used(File dir) {
        long n = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) n += f.length();
        return n;
    }

    /**
     * Deletes the kept notes no longer in any of these notifications: removed, dismissed, gone from the chat, or turned
     * secret. Null (no notification access): deletes every one, halves being written too. After the copies already
     * queued, so none lands once its note is gone. snapshot: when all was read; a copy started since is not swept.
     */
    static void sweep(Context c, Iterable<StatusBarNotification> all, long snapshot) {
        keeper().execute(() -> {
            try {
                File dir = keptDir(c);
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) return;
                Set<String> wanted = all == null ? null : new HashSet<>();
                if (all != null) for (StatusBarNotification s : all) for (Uri uri : notes(s)) wanted.add(Voice.keptName(s.getKey(), uri.toString()));
                String[] names = new String[files.length];
                long[] since = new long[files.length];
                long now = System.currentTimeMillis();
                for (int i = 0; i < files.length; i++) {
                    names[i] = files[i].getName();
                    since[i] = names[i].endsWith(".part") ? files[i].lastModified() : STARTED.getOrDefault(names[i], 0L);
                }
                for (String name : Voice.drop(names, since, wanted, snapshot, now)) {
                    new File(dir, name).delete();
                    STARTED.remove(name);
                }
            } catch (RuntimeException e) { // as in keep: never take the home's process down
                Log.w("Ganimede", "Voice notes not swept: " + e.getClass().getName());
            }
        });
    }

    /** Whether any kept copy (or half) is on disk: with none, a sweep has nothing to delete. */
    static boolean hasKept(Context c) {
        String[] files = keptDir(c).list();
        return files != null && files.length > 0;
    }

    private static synchronized ExecutorService keeper() {
        if (keeper == null) keeper = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "voice-keeper");
            t.setDaemon(true);
            return t;
        });
        return keeper;
    }

    private static synchronized Handler watchdog() {
        if (watchdog == null) {
            HandlerThread t = new HandlerThread("voice-watchdog");
            t.setDaemon(true);
            t.start();
            watchdog = new Handler(t.getLooper());
        }
        return watchdog;
    }

    private static File kept(Context c, StatusBarNotification s, Uri uri) {
        return new File(keptDir(c), Voice.keptName(s.getKey(), uri.toString()));
    }

    private static File keptDir(Context c) {
        return new File(c.getNoBackupFilesDir(), "voice"); // private, outside backups
    }

    /** Asks the system, not the chat app, whether that app shares this audio with notification readers now. */
    private static boolean granted(Context c, Uri uri) {
        return c.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The voice notes of a notification, in the chat's order, shared or not; none if it is secret (never shown, never kept). */
    static List<Uri> notes(StatusBarNotification s) {
        List<Uri> found = new ArrayList<>();
        if (Safe.secret(s.getNotification().visibility)) return found;
        try {
            Parcelable[] msgs = s.getNotification().extras.getParcelableArray("android.messages");
            if (msgs == null) return found;
            String[] types = new String[msgs.length], uris = new String[msgs.length];
            for (int i = 0; i < msgs.length; i++) {
                if (!(msgs[i] instanceof Bundle)) continue;
                Bundle m = (Bundle) msgs[i];
                Object u = m.getParcelable("uri");
                types[i] = m.getString("type");
                uris[i] = u == null ? null : u.toString();
            }
            for (int i : Voice.all(types, uris)) found.add(Uri.parse(uris[i]));
            return found;
        } catch (RuntimeException unreadable) { // an app's own classes inside: Android can't unpack them here
            return found;
        }
    }

    /** The row's note: its kept copy plays as it is; without one it is copied from the chat app first. */
    private void toggle(Uri uri, File kept) {
        if (uri.equals(current)) {
            if (!ready) return; // still copying
            if (player.isPlaying()) pause(); else play();
            return;
        }
        stop("another note");
        current = uri;
        if (kept.isFile()) {
            load(kept);
            refresh();
            return;
        }
        int mine = run;
        ContentResolver cr = ui.a.getContentResolver();
        new Thread(() -> {
            File f = copy(cr, uri, new File(dir, "note" + mine));
            main.post(() -> {
                if (mine != run) { if (f != null) f.delete(); return; }
                if (f == null) failed(); else load(f);
            });
        }, "voice").start();
        refresh();
    }

    /**
     * The audio into the private file f, or null when the chat app refuses, it's gone, it's too big, or it takes longer
     * than Voice.COPY_MS from before the open (the open is cancelled, if the app listens, and the stream closed under it).
     */
    private static File copy(ContentResolver cr, Uri uri, File f) {
        CancellationSignal stop = new CancellationSignal();
        Runnable cut = stop::cancel;
        watchdog().postDelayed(cut, Voice.COPY_MS);
        try (AssetFileDescriptor afd = cr.openAssetFileDescriptor(uri, "r", stop)) {
            if (afd == null) return null;
            f.getParentFile().mkdirs();
            try (InputStream in = afd.createInputStream(); OutputStream out = new FileOutputStream(f)) {
                stop.setOnCancelListener(() -> close(in)); // at once if the time is already up
                byte[] buf = new byte[64 << 10];
                long total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    if ((total += n) > Voice.NOTE_BYTES) throw new IOException("too big");
                    out.write(buf, 0, n);
                }
            }
            stop.throwIfCanceled(); // a stream closed under a read may look ended: half a note
            return f;
        } catch (IOException | RuntimeException refused) { // SecurityException, FileNotFoundException, cancelled, too big
            f.delete();
            return null;
        } finally {
            watchdog().removeCallbacks(cut);
        }
    }

    private static void close(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            Log.w("Ganimede", "Voice note not cut off: " + e.getClass().getName());
        }
    }

    private void load(File f) {
        player = new MediaPlayer();
        player.setAudioAttributes(SPEECH);
        player.setOnPreparedListener(p -> { ready = true; play(); });
        player.setOnCompletionListener(p -> stop("ended"));
        player.setOnErrorListener((p, what, extra) -> { failed(); return true; });
        try (FileInputStream in = new FileInputStream(f)) {
            player.setDataSource(in.getFD());
            player.prepareAsync();
        } catch (IOException | RuntimeException e) {
            failed();
        }
    }

    private void play() {
        if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { // not during a call
            player.start();
            Log.i("Ganimede", "voice plays " + player.getDuration()); // the e2e suite reads it: the screen never rests while a note plays (13-popup)
        }
        tick();
    }

    private void pause() {
        stop("paused"); // a paused private copy must not remain on disk
    }

    private void failed() {
        stop("failed");
        ui.toast(ui.tr("Can't play this voice message"));
    }

    /** Stops whatever plays and deletes every copy (also any left by a crash). */
    void stop() {
        stop("closed");
    }

    private void stop(String why) {
        run++;
        Log.i("Ganimede", "voice stops: " + why); // the e2e suite reads "voice stops" (13-popup); why tells a failed step what ended the note
        main.removeCallbacks(tick);
        if (player != null) player.release();
        player = null;
        ready = false;
        current = null;
        audio.abandonAudioFocusRequest(focus);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        refresh();
    }

    private void tick() {
        refresh();
        main.removeCallbacks(tick);
        if (ready && player.isPlaying()) main.postDelayed(tick, 250);
    }

    private void refresh() {
        for (Row r : rows) r.show();
    }

    /** Play or pause, a thin bar to drag through the note, and the time: the app's accent, its line icons. */
    private final class Row extends LinearLayout {
        final Uri uri; // the notification's: the same whether the note is played from its kept copy or the chat app
        final ImageView button = new ImageView(ui.a);
        final SeekBar bar = new SeekBar(ui.a);
        final TextView time = ui.plain(12, false, 0xCCFFFFFF);
        final Glyph play = new Glyph("play", ui.accent), pause = new Glyph("pause", ui.accent);

        Row(Uri uri, File kept) {
            super(ui.a);
            this.uri = uri;
            setGravity(Gravity.CENTER_VERTICAL);
            setPaddingRelative(ui.dp(2), 0, ui.dp(12), 0);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(ui.dp(24));
            pill.setColor(Colors.alpha(ui.accent, 0x26));
            setBackground(pill);
            button.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14)); // the same 20 dp glyph in a 48 dp target
            button.setOnClickListener(v -> toggle(uri, kept));
            addView(button, new LayoutParams(ui.dp(48), ui.dp(48)));
            ColorStateList accent = ColorStateList.valueOf(ui.accent);
            bar.setProgressTintList(accent);
            bar.setThumbTintList(accent);
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
            bar.setPaddingRelative(ui.dp(8), 0, ui.dp(8), 0);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar b, int ms, boolean byUser) {
                    if (byUser && ready && uri.equals(current)) { player.seekTo(ms); show(); }
                }
                @Override public void onStartTrackingTouch(SeekBar b) {}
                @Override public void onStopTrackingTouch(SeekBar b) {}
            });
            addView(bar, new LayoutParams(0, ui.dp(48), 1));
            addView(time);
            LayoutParams lp = new LayoutParams(-1, -2);
            lp.topMargin = ui.dp(8);
            setLayoutParams(lp);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            rows.add(this);
            show();
        }

        @Override protected void onDetachedFromWindow() {
            rows.remove(this);
            super.onDetachedFromWindow();
        }

        void show() {
            boolean mine = uri.equals(current), on = mine && ready, playing = on && player.isPlaying();
            button.setImageDrawable(playing ? pause : play);
            button.setContentDescription(ui.tr(playing ? "Pause" : "Play voice message"));
            bar.setEnabled(on);
            bar.setMax(on ? player.getDuration() : 1);
            bar.setProgress(on ? player.getCurrentPosition() : 0);
            time.setText(on ? Voice.clock(player.getCurrentPosition()) + " / " + Voice.clock(player.getDuration())
                    : ui.tr(mine ? "Loading…" : "Voice message"));
        }
    }
}
