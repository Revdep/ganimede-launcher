package org.ganimede.launcher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Usage breaker: after an app chosen in settings has been open for a while without coming back home,
 * a gentle notification suggests a break. Coming back to the home screen cancels it.
 */
public class BreakReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "breaks";

    static void schedule(Context c, String label, int minutes) {
        Intent i = new Intent(c, BreakReceiver.class).putExtra("label", label).putExtra("min", minutes);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + minutes * 60_000L, pi);
    }

    static void cancel(Context c) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, BreakReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) c.getSystemService(AlarmManager.class).cancel(pi);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, T.t(c, "Break reminders"), NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent home = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(T.t(c, "Time for a break?"))
                .setContentText(T.f(c, "You've been on %s for %d minutes.", i.getStringExtra("label"), i.getIntExtra("min", 15)))
                .setContentIntent(home)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(7, n);
        } catch (SecurityException ignored) {
            // Notification permission was taken back; nothing to show.
        }
    }
}
