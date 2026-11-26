package org.ganimede.launcher;

import org.ganimede.launcher.core.ImageLimits;
import org.ganimede.launcher.core.LimitedInputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** The quick button's included images and its safely sized private custom image. */
final class ButtonIcon {
    static final String[] VALUES = {"default", "gray", "colour", "custom"};
    static final String[] NAMES = {"Default icon", "Gray", "Colours", "Your own image…"};
    static final String CUSTOM_FILE = "fab-icon.png";
    private static final int DECODE_SIZE = 384, SAVED_SIZE = 192;
    private static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;

    private static volatile Bitmap custom;
    private static volatile boolean loaded;

    /** Reads the already-small private file; called on a background thread. */
    static Bitmap custom(Context context) {
        if (loaded) return custom;
        synchronized (ButtonIcon.class) {
            if (!loaded) {
                try {
                    custom = BitmapFactory.decodeFile(file(context).getPath());
                } catch (RuntimeException | OutOfMemoryError badFile) {
                    custom = null;
                }
                loaded = true;
            }
            return custom;
        }
    }

    static boolean loaded() { return loaded; }

    static Bitmap cached() { return custom; }

    /** Downsamples, centre-crops, rounds and atomically saves an image chosen through the document picker. */
    static void save(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (!ImageLimits.acceptable(bounds.outWidth, bounds.outHeight)) throw new IOException("image dimensions refused");

        int orientation;
        try (InputStream in = open(context, uri)) {
            orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / options.inSampleSize > DECODE_SIZE && options.inSampleSize <= 1 << 29)
            options.inSampleSize <<= 1;
        Bitmap source;
        try (InputStream in = open(context, uri)) {
            source = BitmapFactory.decodeStream(in, null, options);
        }
        if (source == null) throw new IOException("could not decode image");

        Bitmap upright = orient(source, orientation);
        if (upright != source) source.recycle();
        int side = Math.min(upright.getWidth(), upright.getHeight());
        Bitmap crop = Bitmap.createBitmap(upright, (upright.getWidth() - side) / 2, (upright.getHeight() - side) / 2, side, side);
        if (crop != upright) upright.recycle();
        Bitmap square = Bitmap.createScaledBitmap(crop, SAVED_SIZE, SAVED_SIZE, true);
        if (square != crop) crop.recycle();
        Bitmap round = Bitmap.createBitmap(SAVED_SIZE, SAVED_SIZE, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(round).drawCircle(SAVED_SIZE / 2f, SAVED_SIZE / 2f, SAVED_SIZE / 2f, paint);
        square.recycle();

        AtomicFile file = new AtomicFile(file(context));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            if (!round.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("could not encode image");
            file.finishWrite(out);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            if (out != null) file.failWrite(out);
            throw e;
        }
        synchronized (ButtonIcon.class) { // a late decode of the old file must not overwrite this
            custom = round;
            loaded = true;
        }
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("could not open image");
        return new LimitedInputStream(in, MAX_SOURCE_BYTES);
    }

    private static File file(Context context) { return new File(context.getFilesDir(), CUSTOM_FILE); }

    /** Applies camera EXIF before the square crop chooses which part of the photo to keep. */
    private static Bitmap orient(Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1, 1);
            case ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1, -1);
            case ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90); matrix.postScale(-1, 1); }
            case ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90);
            case ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90); matrix.postScale(-1, 1); }
            case ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90);
            default -> { return source; }
        }
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private ButtonIcon() {}
}
