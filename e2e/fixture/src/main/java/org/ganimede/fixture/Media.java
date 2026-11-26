package org.ganimede.fixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

/** A playing music session, so the home's media player has something to show. `am broadcast -n org.ganimede.fixture/.Media`. */
public final class Media extends BroadcastReceiver {
    private static MediaSession session; // held: a released session disappears from the list

    @Override public void onReceive(Context c, Intent i) {
        if (session == null) session = new MediaSession(c.getApplicationContext(), "e2e");
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Brano e2e").build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
        session.setActive(true);
    }
}
