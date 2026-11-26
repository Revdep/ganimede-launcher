package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Safe;
import org.ganimede.launcher.core.Voice;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
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
import java.util.List;

/**
 * Voice notes in notifications, heard privately: the audio a chat app attaches to its notification is copied into
 * this app's own cache and played from there, so the chat app's player never runs and can't send a "listened"
 * receipt. One at a time; the copy is deleted as soon as playback stops.
 */
final class VoicePlayer {
    private static final long MAX_BYTES = 25L << 20;
    private static final AudioAttributes SPEECH = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();

    private final Ui ui;
    private final File dir;
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Row> rows = new ArrayList<>(); // the rows on screen now
    private final Runnable tick = this::tick;
    private Uri current; // being copied, or loaded
    private MediaPlayer player;
    private boolean ready;
    private int run; // grows on every stop, so a copy that finishes after it is thrown away

    VoicePlayer(Ui ui) {
        this.ui = ui;
        dir = new File(ui.a.getCacheDir(), "voice");
        audio = ui.a.getSystemService(AudioManager.class);
        // Speech is paused rather than played under another sound.
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(SPEECH)
                .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(c -> { if (c < 0) pause(); }, main).build();
    }

    /** A player row for each voice note of the notification this app may read, in the chat's order; none if it is secret. */
    void addRows(StatusBarNotification s, ViewGroup into) {
        if (!Safe.secret(s.getNotification().visibility)) for (Uri uri : find(s)) into.addView(new Row(uri));
    }

    private List<Uri> find(StatusBarNotification s) {
        List<Uri> found = new ArrayList<>();
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
            for (int i : Voice.all(types, uris)) {
                Uri uri = Uri.parse(uris[i]);
                // Asks the system, not the chat app, whether that app shared this audio with notification readers.
                if (ui.a.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        == PackageManager.PERMISSION_GRANTED) found.add(uri);
            }
            return found;
        } catch (RuntimeException unreadable) { // an app's own classes inside: Android can't unpack them here
            return found; // the notes read before that, if any
        }
    }

    private void toggle(Uri uri) {
        if (uri.equals(current)) {
            if (!ready) return; // still copying
            if (player.isPlaying()) pause(); else play();
            return;
        }
        stop();
        current = uri;
        int mine = run;
        ContentResolver cr = ui.a.getContentResolver();
        new Thread(() -> {
            File f = copy(cr, uri, dir);
            main.post(() -> {
                if (mine != run) { if (f != null) f.delete(); return; }
                if (f == null) failed(); else load(f);
            });
        }, "voice").start();
        refresh();
    }

    /** The audio into a private file, or null when the chat app refuses, it's gone, or it's too big. */
    private static File copy(ContentResolver cr, Uri uri, File dir) {
        File f = null;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            dir.mkdirs();
            f = File.createTempFile("note", null, dir);
            try (OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[64 << 10];
                long total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    if ((total += n) > MAX_BYTES) throw new IOException("too big");
                    out.write(buf, 0, n);
                }
            }
            return f;
        } catch (IOException | RuntimeException refused) { // SecurityException, FileNotFoundException, too big
            if (f != null) f.delete();
            return null;
        }
    }

    private void load(File f) {
        player = new MediaPlayer();
        player.setAudioAttributes(SPEECH);
        player.setOnPreparedListener(p -> { ready = true; play(); });
        player.setOnCompletionListener(p -> stop());
        player.setOnErrorListener((p, what, extra) -> { failed(); return true; });
        try (FileInputStream in = new FileInputStream(f)) {
            player.setDataSource(in.getFD());
            player.prepareAsync();
        } catch (IOException | RuntimeException e) {
            failed();
        }
    }

    private void play() {
        if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player.start(); // not during a call
        tick();
    }

    private void pause() {
        stop(); // a paused private copy must not remain on disk
    }

    private void failed() {
        stop();
        ui.toast(ui.tr("Can't play this voice message"));
    }

    /** Stops whatever plays and deletes every copy (also any left by a crash). */
    void stop() {
        run++;
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
        final Uri uri;
        final ImageView button = new ImageView(ui.a);
        final SeekBar bar = new SeekBar(ui.a);
        final TextView time = ui.plain(12, false, 0xCCFFFFFF);
        final Glyph play = new Glyph("play", ui.accent), pause = new Glyph("pause", ui.accent);

        Row(Uri uri) {
            super(ui.a);
            this.uri = uri;
            setGravity(Gravity.CENTER_VERTICAL);
            setPaddingRelative(ui.dp(2), 0, ui.dp(12), 0);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(ui.dp(20));
            pill.setColor(Colors.alpha(ui.accent, 0x26));
            setBackground(pill);
            button.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            button.setOnClickListener(v -> toggle(uri));
            addView(button, new LayoutParams(ui.dp(40), ui.dp(40)));
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
            addView(bar, new LayoutParams(0, ui.dp(32), 1));
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
