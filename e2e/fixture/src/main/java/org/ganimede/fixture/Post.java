package org.ganimede.fixture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A chat notification with two voice notes, the way chat apps post them: one MessagingStyle notification whose
 * messages carry audio from this app's own provider. `am broadcast -n org.ganimede.fixture/.Post [--ez secret true]`.
 * The notes are silent WAVs of 30 and 40 s: their sizes tell which one plays, and they last long enough to be checked
 * while playing.
 */
public final class Post extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("chat", "Chat", NotificationManager.IMPORTANCE_DEFAULT));
        Person anna = new Person.Builder().setName("Anna").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(new Person.Builder().setName("Me").build());
        long now = System.currentTimeMillis();
        for (int s : new int[]{30, 40}) {
            style.addMessage(new Notification.MessagingStyle.Message("Nota " + s, now + s, anna)
                    .setData("audio/wav", Uri.parse("content://org.ganimede.fixture.notes/" + wav(c, s).getName())));
        }
        nm.notify(1, new Notification.Builder(c, "chat").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setStyle(style).setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(i.getBooleanExtra("secret", false) ? Notification.VISIBILITY_SECRET : Notification.VISIBILITY_PRIVATE)
                .build());
    }

    /** s seconds of silence: 8 kHz, 16-bit mono PCM. */
    private static File wav(Context c, int s) {
        File f = new File(c.getFilesDir(), s + ".wav");
        int data = 8000 * 2 * s;
        try (DataOutputStream o = new DataOutputStream(new FileOutputStream(f))) {
            o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + data)); o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
            o.writeInt(Integer.reverseBytes(8000)); o.writeInt(Integer.reverseBytes(16000));
            o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data"); o.writeInt(Integer.reverseBytes(data)); o.write(new byte[data]);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return f;
    }
}
