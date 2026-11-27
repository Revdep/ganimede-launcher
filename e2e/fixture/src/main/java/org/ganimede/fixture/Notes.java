package org.ganimede.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves the voice notes Post wrote, read-only, to whoever the system granted them (the notification listeners).
 * "hang.wav" is a hostile one: a stream that never ends nor sends a byte. "block.wav" never opens: openFile never
 * returns. "slow.wav" is 1 s of silence sent over 5 s, so a copy of it is still running seconds after the post.
 */
public final class Notes extends ContentProvider {
    private static final List<ParcelFileDescriptor> open = new ArrayList<>(); // write ends held: closed, the reader would see the end

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri.getPath().endsWith("/block.wav")) {
            while (true) SystemClock.sleep(60_000); // ignores any cancellation, as a hostile app would
        }
        if (uri.getPath().endsWith("/hang.wav") || uri.getPath().endsWith("/slow.wav")) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                if (uri.getPath().endsWith("/hang.wav")) open.add(pipe[1]);
                else trickle(pipe[1]);
                return pipe[0];
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(), new File(uri.getPath()).getName()),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void trickle(ParcelFileDescriptor end) {
        File wav = new File(getContext().getFilesDir(), "1.wav");
        new Thread(() -> {
            try (FileInputStream in = new FileInputStream(wav);
                 OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(end)) {
                byte[] buf = new byte[1600];
                for (int n; (n = in.read(buf)) > 0; ) { out.write(buf, 0, n); SystemClock.sleep(500); }
            } catch (IOException ignored) {
                // the reader gave up
            }
        }).start();
    }

    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
