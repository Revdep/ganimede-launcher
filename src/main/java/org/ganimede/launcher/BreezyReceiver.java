package org.ganimede.launcher;

import org.ganimede.launcher.core.Wx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Receives Breezy Weather's shared weather (enable "Ganimede" in Breezy's Gadgetbridge data sharing). */
public class BreezyReceiver extends BroadcastReceiver {
    private static final String ACTION = "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER";
    private static final Object IMPORT_LOCK = new Object();
    private static Import next;
    private static boolean importing;

    private static final class Import {
        final String json;
        final PendingResult pending;
        Import(String json, PendingResult pending) {
            this.json = json;
            this.pending = pending;
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (!ACTION.equals(i.getAction())) return;
        // Trade-off: this display-only broadcast is spoofable when Android does not report its sender.
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            String from = getSentFromPackage();
            if (from != null && !Wx.isBreezy(from)) return;
        }
        String json;
        try {
            json = i.getStringExtra("WeatherJson");
        } catch (RuntimeException bad) {
            return;
        }
        if (json == null || json.length() > 64 * 1024) return;
        Import dropped;
        synchronized (IMPORT_LOCK) {
            dropped = next;
            next = new Import(json, goAsync());
            if (!importing) {
                importing = true;
                Context app = c.getApplicationContext();
                new Thread(() -> importWeather(app), "weather-import").start();
            }
        }
        if (dropped != null) dropped.pending.finish(); // only the newest queued reading matters
    }

    private static void importWeather(Context context) {
        while (true) {
            Import work;
            synchronized (IMPORT_LOCK) {
                work = next;
                next = null;
                if (work == null) { importing = false; return; }
            }
            try {
                WeatherStore.saveBreezy(context, work.json);
            } catch (Exception | StackOverflowError e) { // deeply nested JSON overflows the parser's stack
                Log.w("Ganimede", "Ignoring unreadable weather: " + e.getClass().getSimpleName()); // never log the payload
            } finally {
                work.pending.finish();
            }
        }
    }
}
