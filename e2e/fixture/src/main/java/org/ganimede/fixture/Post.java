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
 * `--es shape a|b|c|d|e|f|h --ei upto k` posts the first k messages of a chat with text before the notes, the way Telegram
 * re-posts the conversation with one message more on each arrival (issue 083): (a) "Ciao", a text with a link, note A,
 * note B; (b) "Ciao", A, B; (c) "Ciao", a bare link, A, B; (d) a text with a link, A; (e) as (a), the app revoking the
 * read grant of its earlier notes before each re-post; (f) as (a), the link message carrying a preview image as its data;
 * (h) one note whose stream never ends (Notes "hang.wav"); (s) a note sent slowly (Notes "slow.wav"), then note A;
 * (x) one note whose provider never opens it (Notes "block.wav").
 * `--ei id n` posts under another id (default 1); `--ez cancel true` cancels that id instead of posting.
 * The notes are silent WAVs of 30 and 40 s: their sizes tell which one plays, and they last long enough to be checked
 * while playing.
 */
public final class Post extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        int id = i.getIntExtra("id", 1);
        if (i.getBooleanExtra("cancel", false)) { nm.cancel(id); return; }
        nm.createNotificationChannel(new NotificationChannel("chat", "Chat", NotificationManager.IMPORTANCE_DEFAULT));
        Person anna = new Person.Builder().setName("Anna").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(new Person.Builder().setName("Me").build());
        long now = System.currentTimeMillis();
        String shape = i.getStringExtra("shape"), link = "Offerta https://example.com/a";
        String[] chat = shape == null ? new String[]{"30", "40"} : switch (shape) {
            case "b" -> new String[]{"Ciao", "30", "40"};
            case "c" -> new String[]{"Ciao", "https://example.com/a", "30", "40"};
            case "d" -> new String[]{link, "30"};
            case "h" -> new String[]{"hang"};
            case "s" -> new String[]{"slow", "30"};
            case "x" -> new String[]{"block"};
            default -> new String[]{"Ciao", link, "30", "40"};
        };
        int upto = Math.min(chat.length, i.getIntExtra("upto", chat.length));
        for (int k = 0; k < upto; k++) {
            boolean note = chat[k].matches("\\d+|hang|slow|block");
            Notification.MessagingStyle.Message m = new Notification.MessagingStyle.Message(
                    note ? "Nota " + chat[k] : chat[k], now + k, anna);
            if (note) {
                Uri uri = Uri.parse("content://org.ganimede.fixture.notes/"
                        + (chat[k].matches("\\d+") ? wav(c, Integer.parseInt(chat[k])).getName() : chat[k] + ".wav"));
                if (chat[k].equals("slow")) wav(c, 1); // what Notes trickles
                if ("e".equals(shape) && k < upto - 1) c.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                m.setData("audio/wav", uri);
            } else if ("f".equals(shape) && chat[k].equals(link)) {
                m.setData("image/png", Uri.parse("content://org.ganimede.fixture.notes/preview.png"));
            }
            style.addMessage(m);
        }
        nm.notify(id, new Notification.Builder(c, "chat").setSmallIcon(android.R.drawable.ic_btn_speak_now)
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
