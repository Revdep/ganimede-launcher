package org.ganimede.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves the voice notes Post wrote, read-only, to whoever the system granted them (the notification listeners).
 * "hang.wav" is a hostile one: a stream that never ends nor sends a byte.
 */
public final class Notes extends ContentProvider {
    private static final List<ParcelFileDescriptor> open = new ArrayList<>(); // write ends held: closed, the reader would see the end

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri.getPath().endsWith("/hang.wav")) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                open.add(pipe[1]);
                return pipe[0];
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(), new File(uri.getPath()).getName()),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
