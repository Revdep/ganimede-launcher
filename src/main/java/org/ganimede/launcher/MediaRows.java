package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;

import android.content.ComponentName;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

/** The media player under the clock, and the music apps that appear while headphones or speakers are connected. */
final class MediaRows {
    private final Ui ui;
    private final Apps apps;
    private final Launcher launcher;
    private final Handler handler;
    final LinearLayout media, music;
    private MediaController controller;
    private boolean headphones;

    private final AudioDeviceCallback audio = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) { checkHeadphones(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) { checkHeadphones(); }
    };
    private final MediaController.Callback watch = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { post(); }
        @Override public void onMetadataChanged(MediaMetadata m) { post(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = l -> post();

    private void post() {
        handler.post(this::updateMedia);
    }

    MediaRows(Ui ui, Apps apps, Launcher launcher, Handler handler) {
        this.ui = ui;
        this.apps = apps;
        this.launcher = launcher;
        this.handler = handler;
        media = new LinearLayout(ui.a);
        media.setGravity(Gravity.CENTER_VERTICAL);
        media.setPaddingRelative(ui.dp(4), ui.dp(6), 0, ui.dp(6));
        music = new LinearLayout(ui.a);
        music.setPaddingRelative(0, ui.dp(4), 0, ui.dp(4));
    }

    void register() {
        ui.a.getSystemService(AudioManager.class).registerAudioDeviceCallback(audio, handler);
    }

    void unregister() {
        ui.a.getSystemService(AudioManager.class).unregisterAudioDeviceCallback(audio);
        if (controller != null) controller.unregisterCallback(watch);
    }

    void resume() {
        try {
            ui.a.getSystemService(MediaSessionManager.class).addOnActiveSessionsChangedListener(sessions,
                    new ComponentName(ui.a, NotifService.class), handler);
        } catch (Exception ignored) {
            // No notification access: no media player.
        }
        checkHeadphones();
    }

    void pause() {
        try {
            ui.a.getSystemService(MediaSessionManager.class).removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
            // Never added.
        }
    }

    void updateMedia() {
        media.removeAllViews();
        MediaController found = ui.prefs.getBoolean("media_player", true) && NotifService.connected ? NotifService.media(ui.a) : null;
        boolean same = found != null && controller != null && found.getSessionToken().equals(controller.getSessionToken());
        MediaController m = same ? controller : found; // the controller the callback is registered on, so it can be unregistered later
        if (controller != null && !same) controller.unregisterCallback(watch);
        if (m != null && !same) m.registerCallback(watch, handler);
        controller = m;
        media.setVisibility(m == null ? View.GONE : View.VISIBLE);
        if (m == null) return;
        MediaMetadata md = m.getMetadata();
        CharSequence title = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence artist = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        boolean playing = m.getPlaybackState() != null && m.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        TextView t = ui.text(15, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText("♪ " + (title == null ? "" : title) + (artist == null ? "" : " · " + artist));
        t.setOnClickListener(v -> {
            Intent i = ui.a.getPackageManager().getLaunchIntentForPackage(m.getPackageName());
            if (i != null) ui.open(i);
        });
        media.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        String[] glyphs = {"⏮", playing ? "⏸" : "▶", "⏭"};
        for (int i = 0; i < 3; i++) {
            TextView b = ui.text(22, true);
            b.setText(glyphs[i]);
            b.setContentDescription(ui.tr(i == 0 ? "Previous" : i == 2 ? "Next" : playing ? "Pause" : "Play"));
            b.setGravity(Gravity.CENTER);
            b.setMinimumWidth(ui.dp(48));
            b.setMinimumHeight(ui.dp(48));
            b.setPaddingRelative(ui.dp(12), ui.dp(4), ui.dp(4), ui.dp(4));
            int k = i;
            b.setOnClickListener(v -> {
                MediaController.TransportControls c = m.getTransportControls();
                if (k == 0) c.skipToPrevious(); else if (k == 2) c.skipToNext(); else if (playing) c.pause(); else c.play();
            });
            media.addView(b);
        }
    }

    private void checkHeadphones() {
        boolean aids = ui.prefs.getBoolean("music_hearing_aids", false), on = false;
        for (AudioDeviceInfo d : ui.a.getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_USB_HEADSET
                    || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET)
                    || (aids && Build.VERSION.SDK_INT >= 28 && t == AudioDeviceInfo.TYPE_HEARING_AID)) on = true;
        }
        if (on != headphones) {
            headphones = on;
            updateMusic();
        }
    }

    /** Music apps appear above the favourites while headphones or speakers are connected. */
    void updateMusic() {
        music.removeAllViews();
        if (headphones && ui.prefs.getBoolean("music_on_headset", true)) {
            int size = ui.dp(46);
            for (String k : ui.prefs.getStringSet("music_apps", Set.of())) {
                Item it = apps.find(k);
                if (it == null) continue;
                ImageView iv = ui.icon(it.icon, it.fromPack, size);
                iv.setContentDescription(apps.label(it));
                iv.setOnClickListener(v -> launcher.launch(it, v));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd(ui.dp(14));
                music.addView(iv, lp);
            }
        }
        music.setVisibility(music.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }
}
